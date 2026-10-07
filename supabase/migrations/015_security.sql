-- Bảo mật: 2FA cho quyền cao, giới hạn tần suất, nhật ký bảo mật, giới hạn file tải lên.
-- Chạy SAU 014_shop_owners.sql. An toàn khi chạy lại.

-- ============================================================ 1. 2FA (TOTP, Supabase MFA)
-- Tài khoản ĐÃ bật 2FA thì phiên đăng nhập phải xác thực mã (aal2) mới dùng được quyền admin / chủ quán.
-- Lộ mật khẩu một mình không đủ để chiếm quyền.
create or replace function public.fg_mfa_ok()
returns boolean language sql stable security definer set search_path = public, auth as $$
  select coalesce(auth.jwt()->>'aal', 'aal1') = 'aal2'
    or not exists (select 1 from auth.mfa_factors where user_id = auth.uid() and status = 'verified')
$$;

create or replace function public.fg_is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.fg_profiles where id = auth.uid() and role = 'admin') and public.fg_mfa_ok();
$$;

create or replace function public.fg_owns_restaurant(p_restaurant_id bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.fg_restaurants where id = p_restaurant_id and owner_id = auth.uid()) and public.fg_mfa_ok()
$$;

drop policy if exists "fg restaurants owner update" on public.fg_restaurants;
create policy "fg restaurants owner update" on public.fg_restaurants for update
  using (owner_id = auth.uid() and public.fg_mfa_ok()) with check (owner_id = auth.uid());

-- ============================================================ 2. giới hạn tần suất (chống spam, dò mã giảm giá)
create table if not exists public.fg_rate_limits (
  key text primary key,            -- "<hành động>:<user id>"
  window_start timestamptz not null,
  hits int not null
);
alter table public.fg_rate_limits enable row level security;   -- không có policy: app không đọc/ghi trực tiếp

create or replace function public.fg_throttle(p_action text, p_max int, p_window_seconds int)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_key text := p_action || ':' || coalesce(auth.uid()::text, 'anon');
  v_hits int;
begin
  insert into public.fg_rate_limits as r (key, window_start, hits) values (v_key, now(), 1)
  on conflict (key) do update set
    hits = case when r.window_start < now() - make_interval(secs => p_window_seconds) then 1 else r.hits + 1 end,
    window_start = case when r.window_start < now() - make_interval(secs => p_window_seconds) then now() else r.window_start end
  returning hits into v_hits;
  if v_hits > p_max then raise exception 'Bạn thao tác quá nhiều lần, vui lòng thử lại sau ít phút'; end if;
end $$;
revoke execute on function public.fg_throttle(text, int, int) from public, anon, authenticated;

-- Kiểm tra mã giảm giá: lỗi được TRẢ VỀ (không raise) để lượt thử sai vẫn được đếm → chặn dò mã
create or replace function public.fg_check_voucher(p_code text, p_subtotal int, p_restaurant_id bigint default null)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'Bạn cần đăng nhập để dùng mã giảm giá'; end if;
  perform public.fg_throttle('voucher', 15, 600);
  begin
    return public.fg_voucher_quote(p_code, auth.uid(), p_subtotal, case when p_subtotal >= 100000 then 0 else 15000 end, p_restaurant_id);
  exception when raise_exception then
    return jsonb_build_object('error', sqlerrm);
  end;
end $$;
grant execute on function public.fg_check_voucher(text, int, bigint) to authenticated;

-- chèn giới hạn vào các hàm đã có (sửa định nghĩa hiện tại, giữ nguyên phần còn lại)
do $$
declare
  d text;
  patches text[][] := array[
    ['public.fg_place_order(jsonb, text, text, text, text, text, double precision, double precision, text)',
     'if v_user is null then raise exception ''Bạn cần đăng nhập để đặt hàng''; end if;', 'order', '10', '600'],
    ['public.fg_submit_review(bigint, bigint, integer, text)',
     'if v_user is null then raise exception ''Bạn cần đăng nhập để đánh giá''; end if;', 'review', '30', '600'],
    ['public.fg_register_restaurant(jsonb)',
     'if auth.uid() is null then raise exception ''Bạn cần đăng nhập để đăng ký nhà hàng''; end if;', 'register', '5', '3600'],
    ['public.fg_create_subscription_payment(integer)',
     'if auth.uid() is null then raise exception ''Bạn cần đăng nhập''; end if;', 'sub_invoice', '20', '3600']
  ];
  p text[];
begin
  foreach p slice 1 in array patches loop
    d := pg_get_functiondef(p[1]::regprocedure);
    if position('fg_throttle' in d) = 0 then
      if position(p[2] in d) = 0 then raise exception 'Không tìm thấy vị trí chèn trong %', p[1]; end if;
      execute replace(d, p[2], p[2] || E'\n  perform public.fg_throttle(''' || p[3] || ''', ' || p[4] || ', ' || p[5] || ');');
    end if;
  end loop;
end $$;

-- ============================================================ 3. nhật ký bảo mật (ai làm gì, lúc nào)
create table if not exists public.fg_audit_log (
  id bigint generated always as identity primary key,
  at timestamptz not null default now(),
  actor uuid,                      -- null = hệ thống (webhook, trigger)
  action text not null,            -- insert / update / delete
  table_name text not null,
  row_id text,
  changes jsonb
);
create index if not exists fg_audit_log_at_idx on public.fg_audit_log(at desc);
alter table public.fg_audit_log enable row level security;
drop policy if exists "fg audit admin read" on public.fg_audit_log;
create policy "fg audit admin read" on public.fg_audit_log for select using (public.fg_is_admin());
-- không ai sửa / xóa được nhật ký qua app (không có policy insert/update/delete)

-- tham số trigger: các cột cần theo dõi, cách nhau bằng dấu phẩy ('*' = tất cả, ghi cả thêm/xóa)
create or replace function public.fg_audit()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_old jsonb := case when tg_op <> 'INSERT' then to_jsonb(old) end;
  v_new jsonb := case when tg_op <> 'DELETE' then to_jsonb(new) end;
  v_cols text[] := string_to_array(coalesce(tg_argv[0], '*'), ',');
  v_noise text[] := array['updated_at', 'rating', 'review_count', 'sold_count'];
  v_diff jsonb;
begin
  if tg_op = 'UPDATE' then
    select jsonb_object_agg(k, jsonb_build_object('from', v_old->k, 'to', v_new->k)) into v_diff
    from jsonb_object_keys(v_new) k
    where v_new->k is distinct from v_old->k and not (k = any(v_noise)) and (v_cols = array['*'] or k = any(v_cols));
    if v_diff is null then return null; end if;
  elsif v_cols = array['*'] or tg_op = 'DELETE' then
    v_diff := coalesce(v_new, v_old);
  else
    return null;
  end if;
  insert into public.fg_audit_log (actor, action, table_name, row_id, changes)
  values (auth.uid(), lower(tg_op), tg_table_name, coalesce(v_new->>'id', v_old->>'id'), v_diff);
  return null;
end $$;

drop trigger if exists fg_audit_profiles on public.fg_profiles;
create trigger fg_audit_profiles after update on public.fg_profiles
  for each row execute function public.fg_audit('role');
drop trigger if exists fg_audit_restaurants on public.fg_restaurants;
create trigger fg_audit_restaurants after insert or update or delete on public.fg_restaurants
  for each row execute function public.fg_audit('*');
drop trigger if exists fg_audit_vouchers on public.fg_vouchers;
create trigger fg_audit_vouchers after insert or update or delete on public.fg_vouchers
  for each row execute function public.fg_audit('*');
drop trigger if exists fg_audit_subscriptions on public.fg_subscription_payments;
create trigger fg_audit_subscriptions after insert or update on public.fg_subscription_payments
  for each row execute function public.fg_audit('*');
drop trigger if exists fg_audit_orders on public.fg_orders;
create trigger fg_audit_orders after update on public.fg_orders
  for each row execute function public.fg_audit('status,payment_status');
drop trigger if exists fg_audit_foods on public.fg_foods;
create trigger fg_audit_foods after update or delete on public.fg_foods
  for each row execute function public.fg_audit('price,name,restaurant_id,is_available');

-- ============================================================ 4. file tải lên: chỉ ảnh (không SVG), tối đa 5MB
update storage.buckets
set file_size_limit = 5242880,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/avif']
where id = 'fg-images';

-- ============================================================ 5. thu hồi quyền gọi các hàm nội bộ
revoke execute on function public.fg_new_subscription_code() from public, anon, authenticated;
revoke execute on function public.fg_mfa_ok() from public, anon;

-- quyền tối thiểu: khách chưa đăng nhập chỉ gọi được webhook thanh toán (tự xác thực bằng chữ ký / khóa API).
-- fg_is_admin / fg_owns_restaurant phải giữ vì RLS gọi chúng với quyền của người đang truy vấn.
do $$
declare f text;
begin
  foreach f in array array[
    'fg_admin_confirm_subscription(bigint)', 'fg_admin_grant_restaurant(uuid,bigint,text,integer)',
    'fg_cancel_order(bigint)', 'fg_check_voucher(text,integer,bigint)', 'fg_create_subscription_payment(integer)',
    'fg_place_order(jsonb,text,text,text,text,text,double precision,double precision,text)',
    'fg_register_restaurant(jsonb)', 'fg_submit_review(bigint,bigint,integer,text)', 'fg_used_count(fg_vouchers)'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
  end loop;
end $$;

-- liệt kê hàm SECURITY DEFINER mà khách vãng lai (anon) vẫn gọi được — để rà soát
select p.oid::regprocedure::text as anon_callable_definer_function
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prosecdef and p.proname like 'fg\_%'
  and p.prorettype <> 'trigger'::regtype
  and has_function_privilege('anon', p.oid, 'execute')
order by 1;
