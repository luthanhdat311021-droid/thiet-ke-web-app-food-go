-- Chủ nhà hàng tự đăng ký + phí duy trì 200.000đ/tháng (chuyển khoản QR, SePay tự xác nhận).
-- Chủ quán quản lý món, voucher, đơn hàng, doanh thu của RIÊNG quán mình. Admin thu phí duy trì.
-- Chạy SAU 013_multi_restaurant.sql. An toàn khi chạy lại.

-- ============================================================ 1. chủ quán + hạn dùng
alter table public.fg_restaurants add column if not exists owner_id uuid references auth.users(id) on delete set null;
alter table public.fg_restaurants add column if not exists paid_until timestamptz;
alter table public.fg_restaurants add column if not exists phone text;
-- mỗi tài khoản sở hữu tối đa 1 nhà hàng
create unique index if not exists fg_restaurants_owner_uidx on public.fg_restaurants(owner_id) where owner_id is not null;

create or replace function public.fg_subscription_fee() returns int language sql immutable as $$ select 200000 $$;

-- quán do admin tạo (không có chủ) luôn hoạt động; quán của chủ cần còn hạn
create or replace function public.fg_restaurant_live(r public.fg_restaurants)
returns boolean language sql stable as $$
  select r.is_active and (r.owner_id is null or coalesce(r.paid_until > now(), false))
$$;

create or replace function public.fg_owns_restaurant(p_restaurant_id bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.fg_restaurants where id = p_restaurant_id and owner_id = auth.uid())
$$;

-- ============================================================ 2. quyền (RLS)
drop policy if exists "fg restaurants public read" on public.fg_restaurants;
create policy "fg restaurants public read" on public.fg_restaurants for select
  using ((is_active and (owner_id is null or paid_until > now())) or owner_id = auth.uid() or public.fg_is_admin());
drop policy if exists "fg restaurants owner update" on public.fg_restaurants;
create policy "fg restaurants owner update" on public.fg_restaurants for update
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- chủ quán không tự sửa được: chủ sở hữu, hạn dùng, điểm đánh giá, trạng thái ẩn (admin quyết)
-- current_user = 'authenticated' chỉ khi sửa TRỰC TIẾP từ app (hàm security definer chạy với quyền owner của hàm)
create or replace function public.fg_guard_restaurant()
returns trigger language plpgsql set search_path = public as $$
begin
  if current_user = 'authenticated' and not public.fg_is_admin() then
    new.owner_id := old.owner_id;
    new.paid_until := old.paid_until;
    new.rating := old.rating;
    new.review_count := old.review_count;
    new.is_active := old.is_active;
  end if;
  return new;
end $$;
drop trigger if exists fg_restaurants_guard on public.fg_restaurants;
create trigger fg_restaurants_guard before update on public.fg_restaurants
  for each row execute function public.fg_guard_restaurant();

drop policy if exists "fg foods owner write" on public.fg_foods;
create policy "fg foods owner write" on public.fg_foods for all
  using (public.fg_owns_restaurant(restaurant_id)) with check (public.fg_owns_restaurant(restaurant_id));

-- đơn hàng: chủ quán xem + cập nhật trạng thái đơn của quán mình
drop policy if exists "fg orders owner read" on public.fg_orders;
create policy "fg orders owner read" on public.fg_orders for select using (public.fg_owns_restaurant(restaurant_id));
drop policy if exists "fg orders owner update" on public.fg_orders;
create policy "fg orders owner update" on public.fg_orders for update
  using (public.fg_owns_restaurant(restaurant_id)) with check (public.fg_owns_restaurant(restaurant_id));
drop policy if exists "fg order_items owner read" on public.fg_order_items;
create policy "fg order_items owner read" on public.fg_order_items for select using (
  exists (select 1 from public.fg_orders o where o.id = order_id and public.fg_owns_restaurant(o.restaurant_id))
);

-- chủ quán chỉ được đổi trạng thái đơn / thanh toán, không sửa tiền, địa chỉ...
create or replace function public.fg_guard_order()
returns trigger language plpgsql set search_path = public as $$
declare skip text[] := array['status', 'payment_status', 'updated_at', 'paid_at', 'delivered_at'];
begin
  if current_user = 'authenticated' and not public.fg_is_admin()
     and (to_jsonb(new) - skip) is distinct from (to_jsonb(old) - skip) then
    raise exception 'Chỉ được cập nhật trạng thái đơn hàng';
  end if;
  return new;
end $$;
drop trigger if exists fg_orders_guard on public.fg_orders;
create trigger fg_orders_guard before update on public.fg_orders
  for each row execute function public.fg_guard_order();

-- ảnh: mọi tài khoản được tải ảnh vào thư mục riêng <user id>/... (form đăng ký quán, món ăn)
drop policy if exists "fg images own folder insert" on storage.objects;
create policy "fg images own folder insert" on storage.objects for insert to authenticated
  with check (bucket_id = 'fg-images' and (storage.foldername(name))[1] = auth.uid()::text);

-- ============================================================ 3. thông báo có đường dẫn (chủ quán nhận "Đơn mới")
alter table public.fg_notifications add column if not exists link text;

create or replace function public.fg_notify_shop_new_order()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_owner uuid;
begin
  select owner_id into v_owner from public.fg_restaurants where id = new.restaurant_id;
  if v_owner is not null then
    insert into public.fg_notifications (user_id, order_id, title, body, link)
    values (v_owner, new.id, 'Đơn mới #' || new.code,
      new.recipient || ' đặt ' || to_char(new.total, 'FM999G999G999') || 'đ. Xác nhận đơn ngay nhé!', '/shop?tab=orders');
  end if;
  return new;
end $$;
drop trigger if exists fg_orders_notify_shop on public.fg_orders;
create trigger fg_orders_notify_shop after insert on public.fg_orders
  for each row execute function public.fg_notify_shop_new_order();

-- ============================================================ 4. voucher riêng của quán
alter table public.fg_vouchers add column if not exists restaurant_id bigint references public.fg_restaurants(id) on delete cascade;
drop policy if exists "fg vouchers owner all" on public.fg_vouchers;
create policy "fg vouchers owner all" on public.fg_vouchers for all
  using (restaurant_id is not null and public.fg_owns_restaurant(restaurant_id))
  with check (restaurant_id is not null and public.fg_owns_restaurant(restaurant_id));

drop function if exists public.fg_voucher_quote(text, uuid, int, int);
create or replace function public.fg_voucher_quote(p_code text, p_user uuid, p_subtotal int, p_shipping int, p_restaurant_id bigint)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.fg_vouchers;
  v_today date := (now() at time zone 'Asia/Ho_Chi_Minh')::date;
  v_count int;
  v_discount int;
begin
  select * into v from public.fg_vouchers where code = upper(regexp_replace(coalesce(p_code, ''), '\s', '', 'g')) for update;
  if not found or not v.is_active then raise exception 'Mã giảm giá không tồn tại hoặc đã ngừng áp dụng'; end if;
  if v.restaurant_id is not null and v.restaurant_id is distinct from p_restaurant_id then
    raise exception 'Mã % chỉ áp dụng cho %', v.code, (select name from public.fg_restaurants where id = v.restaurant_id);
  end if;
  if v.starts_on is not null and v_today < v.starts_on then raise exception 'Mã % áp dụng từ ngày %', v.code, to_char(v.starts_on, 'DD/MM/YYYY'); end if;
  if v.expires_on is not null and v_today > v.expires_on then raise exception 'Mã % đã hết hạn', v.code; end if;
  if p_subtotal < v.min_subtotal then
    raise exception 'Mã % áp dụng cho đơn từ %đ', v.code, replace(to_char(v.min_subtotal, 'FM999,999,999'), ',', '.');
  end if;
  if v.usage_limit is not null then
    select count(*) into v_count from public.fg_orders where voucher_code = v.code and status <> 'cancelled';
    if v_count >= v.usage_limit then raise exception 'Mã % đã hết lượt sử dụng', v.code; end if;
  end if;
  if v.per_user_limit is not null then
    select count(*) into v_count from public.fg_orders where voucher_code = v.code and user_id = p_user and status <> 'cancelled';
    if v_count >= v.per_user_limit then raise exception 'Bạn đã dùng hết lượt của mã %', v.code; end if;
  end if;
  if v.discount_type = 'freeship' and p_shipping = 0 then raise exception 'Đơn của bạn đã được miễn phí giao hàng'; end if;

  v_discount := case v.discount_type
    when 'percent' then floor(p_subtotal * v.discount_value / 100.0)::int
    when 'amount' then v.discount_value
    else p_shipping end;
  if v.max_discount is not null then v_discount := least(v_discount, v.max_discount); end if;
  v_discount := greatest(0, least(v_discount, p_subtotal + p_shipping));
  return jsonb_build_object('code', v.code, 'description', v.description, 'type', v.discount_type, 'discount', v_discount);
end $$;
revoke execute on function public.fg_voucher_quote(text, uuid, int, int, bigint) from public, anon, authenticated;

drop function if exists public.fg_check_voucher(text, int);
create or replace function public.fg_check_voucher(p_code text, p_subtotal int, p_restaurant_id bigint default null)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'Bạn cần đăng nhập để dùng mã giảm giá'; end if;
  return public.fg_voucher_quote(p_code, auth.uid(), p_subtotal, case when p_subtotal >= 100000 then 0 else 15000 end, p_restaurant_id);
end $$;
grant execute on function public.fg_check_voucher(text, int, bigint) to authenticated;

-- ============================================================ 5. đặt hàng: chặn quán hết hạn + voucher theo quán
create or replace function public.fg_place_order(
  p_items jsonb,
  p_recipient text,
  p_phone text,
  p_address text,
  p_payment_method text,
  p_note text default null,
  p_lat double precision default null,
  p_lng double precision default null,
  p_voucher_code text default null
) returns public.fg_orders
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_restaurant public.fg_restaurants;
  v_restaurant_count int;
  v_subtotal int := 0;
  v_shipping int;
  v_discount int := 0;
  v_voucher text;
  v_quote jsonb;
  v_order public.fg_orders;
  v_code text;
  it record;
begin
  if v_user is null then raise exception 'Bạn cần đăng nhập để đặt hàng'; end if;
  if p_payment_method not in ('cod', 'qr', 'momo') then raise exception 'Phương thức thanh toán không hợp lệ'; end if;
  if coalesce(jsonb_array_length(p_items), 0) = 0 then raise exception 'Giỏ hàng trống'; end if;
  if coalesce(trim(p_recipient),'') = '' or coalesce(trim(p_phone),'') = '' or coalesce(trim(p_address),'') = '' then
    raise exception 'Vui lòng nhập đủ thông tin giao hàng';
  end if;
  if (p_lat is null) <> (p_lng is null) or p_lat not between -90 and 90 or p_lng not between -180 and 180 then
    raise exception 'Tọa độ giao hàng không hợp lệ';
  end if;

  select count(distinct f.restaurant_id) into v_restaurant_count
  from jsonb_to_recordset(p_items) as x(food_id bigint, qty int)
  join public.fg_foods f on f.id = x.food_id;
  if v_restaurant_count <> 1 then raise exception 'Mỗi đơn chỉ được đặt món từ một nhà hàng'; end if;

  select r.* into v_restaurant from public.fg_restaurants r
  where r.id = (select f.restaurant_id from public.fg_foods f where f.id = (p_items->0->>'food_id')::bigint);
  if not public.fg_restaurant_live(v_restaurant) then raise exception '% đang tạm ngưng nhận đơn', v_restaurant.name; end if;
  if not public.fg_store_open(v_restaurant) then
    if not v_restaurant.is_open then raise exception '% đang tạm đóng cửa, vui lòng quay lại sau', v_restaurant.name; end if;
    raise exception '% đã đóng cửa. Giờ nhận đơn: % - %', v_restaurant.name, to_char(v_restaurant.open_time, 'HH24:MI'), to_char(v_restaurant.close_time, 'HH24:MI');
  end if;

  for it in
    select f.id, f.name, f.image, f.price, f.is_available, x.qty
    from jsonb_to_recordset(p_items) as x(food_id bigint, qty int)
    left join public.fg_foods f on f.id = x.food_id
  loop
    if it.id is null then raise exception 'Món ăn không tồn tại'; end if;
    if not it.is_available then raise exception 'Món "%" đã hết', it.name; end if;
    if it.qty is null or it.qty < 1 or it.qty > 50 then raise exception 'Số lượng không hợp lệ'; end if;
    v_subtotal := v_subtotal + it.price * it.qty;
  end loop;

  v_shipping := case when v_subtotal >= 100000 then 0 else 15000 end;

  if coalesce(trim(p_voucher_code), '') <> '' then
    v_quote := public.fg_voucher_quote(p_voucher_code, v_user, v_subtotal, v_shipping, v_restaurant.id);
    v_voucher := v_quote->>'code';
    v_discount := (v_quote->>'discount')::int;
  end if;

  loop
    v_code := 'FG' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
    exit when not exists (select 1 from public.fg_orders where code = v_code);
  end loop;

  insert into public.fg_orders (code, user_id, restaurant_id, restaurant_name, payment_method,
    subtotal, shipping_fee, discount, voucher_code, total, recipient, phone, address, note, delivery_lat, delivery_lng)
  values (v_code, v_user, v_restaurant.id, v_restaurant.name, p_payment_method,
    v_subtotal, v_shipping, v_discount, v_voucher, v_subtotal + v_shipping - v_discount, trim(p_recipient), trim(p_phone), trim(p_address),
    nullif(trim(p_note), ''), p_lat, p_lng)
  returning * into v_order;

  insert into public.fg_order_items (order_id, food_id, name, image, price, qty)
  select v_order.id, f.id, f.name, f.image, f.price, x.qty
  from jsonb_to_recordset(p_items) as x(food_id bigint, qty int)
  join public.fg_foods f on f.id = x.food_id;

  update public.fg_foods f set sold_count = sold_count + x.qty
  from jsonb_to_recordset(p_items) as x(food_id bigint, qty int)
  where f.id = x.food_id;

  return v_order;
end $$;
grant execute on function public.fg_place_order(jsonb, text, text, text, text, text, double precision, double precision, text) to authenticated;

-- ============================================================ 6. phí duy trì
create table if not exists public.fg_subscription_payments (
  id bigint generated always as identity primary key,
  code text not null unique,                 -- nội dung chuyển khoản: NHxxxxxxxx
  restaurant_id bigint not null references public.fg_restaurants(id) on delete cascade,
  owner_id uuid references auth.users(id) on delete set null,
  months int not null check (months between 1 and 12),
  amount int not null check (amount >= 0),   -- 0 = admin cấp miễn phí (demo)
  status text not null default 'pending' check (status in ('pending', 'paid', 'cancelled')),
  method text not null default 'qr' check (method in ('qr', 'admin')),
  paid_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists fg_subscription_payments_restaurant_idx on public.fg_subscription_payments(restaurant_id, created_at desc);
alter table public.fg_subscription_payments enable row level security;
drop policy if exists "fg subs read own or admin" on public.fg_subscription_payments;
create policy "fg subs read own or admin" on public.fg_subscription_payments for select
  using (owner_id = auth.uid() or public.fg_is_admin());

-- ghi nhận thanh toán: cộng thêm tháng tính từ hạn hiện tại (hoặc từ hôm nay nếu đã hết hạn)
create or replace function public.fg_apply_subscription(p_payment_id bigint)
returns public.fg_subscription_payments language plpgsql security definer set search_path = public as $$
declare
  v public.fg_subscription_payments;
  v_until timestamptz;
begin
  select * into v from public.fg_subscription_payments where id = p_payment_id for update;
  if v.id is null or v.status = 'paid' then return v; end if;
  update public.fg_restaurants
  set paid_until = greatest(coalesce(paid_until, now()), now()) + make_interval(months => v.months)
  where id = v.restaurant_id
  returning paid_until into v_until;
  update public.fg_subscription_payments set status = 'paid', paid_at = now() where id = v.id returning * into v;
  if v.owner_id is not null then
    insert into public.fg_notifications (user_id, title, body, link)
    values (v.owner_id, 'Đã gia hạn nhà hàng',
      'Nhà hàng hoạt động tới ' || to_char(v_until at time zone 'Asia/Ho_Chi_Minh', 'DD/MM/YYYY') || '. Cảm ơn bạn!', '/shop');
  end if;
  return v;
end $$;
revoke execute on function public.fg_apply_subscription(bigint) from public, anon, authenticated;

create or replace function public.fg_new_subscription_code() returns text language plpgsql as $$
declare v text;
begin
  loop
    v := 'NH' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
    exit when not exists (select 1 from public.fg_subscription_payments where code = v);
  end loop;
  return v;
end $$;

-- chủ quán tạo hóa đơn gia hạn (dùng lại hóa đơn chưa trả cùng số tháng)
create or replace function public.fg_create_subscription_payment(p_months int default 1)
returns public.fg_subscription_payments language plpgsql security definer set search_path = public as $$
declare
  v_restaurant bigint;
  v public.fg_subscription_payments;
begin
  if auth.uid() is null then raise exception 'Bạn cần đăng nhập'; end if;
  if p_months not in (1, 3, 6, 12) then raise exception 'Số tháng không hợp lệ'; end if;
  select id into v_restaurant from public.fg_restaurants where owner_id = auth.uid();
  if v_restaurant is null then raise exception 'Bạn chưa đăng ký nhà hàng'; end if;
  select * into v from public.fg_subscription_payments
  where restaurant_id = v_restaurant and status = 'pending' and months = p_months and method = 'qr'
  order by id desc limit 1;
  if v.id is not null then return v; end if;
  insert into public.fg_subscription_payments (code, restaurant_id, owner_id, months, amount)
  values (public.fg_new_subscription_code(), v_restaurant, auth.uid(), p_months, public.fg_subscription_fee() * p_months)
  returning * into v;
  return v;
end $$;
grant execute on function public.fg_create_subscription_payment(int) to authenticated;

-- đăng ký nhà hàng mới (chưa hiển thị cho khách tới khi thanh toán phí tháng đầu)
create or replace function public.fg_register_restaurant(p jsonb)
returns public.fg_subscription_payments language plpgsql security definer set search_path = public as $$
declare v_id bigint;
begin
  if auth.uid() is null then raise exception 'Bạn cần đăng nhập để đăng ký nhà hàng'; end if;
  if exists (select 1 from public.fg_restaurants where owner_id = auth.uid()) then raise exception 'Tài khoản của bạn đã có nhà hàng'; end if;
  if coalesce(trim(p->>'name'), '') = '' then raise exception 'Vui lòng nhập tên nhà hàng'; end if;
  if coalesce(trim(p->>'address'), '') = '' or p->>'lat' is null or p->>'lng' is null then raise exception 'Vui lòng ghim vị trí nhà hàng trên bản đồ'; end if;
  if coalesce(trim(p->>'phone'), '') !~ '^(0|\+84)[0-9]{9,10}$' then raise exception 'Số điện thoại không hợp lệ'; end if;

  insert into public.fg_restaurants (name, cuisine, address, phone, lat, lng, image, logo, open_time, close_time, delivery_time, owner_id, rating, review_count)
  values (trim(p->>'name'), nullif(trim(p->>'cuisine'), ''), trim(p->>'address'), trim(p->>'phone'),
    (p->>'lat')::double precision, (p->>'lng')::double precision, nullif(p->>'image', ''), nullif(p->>'logo', ''),
    coalesce(nullif(p->>'open_time', '')::time, '07:00'), coalesce(nullif(p->>'close_time', '')::time, '21:00'),
    coalesce(nullif(trim(p->>'delivery_time'), ''), '20-30 phút'), auth.uid(), 0, 0)
  returning id into v_id;
  return public.fg_create_subscription_payment(1);
end $$;
grant execute on function public.fg_register_restaurant(jsonb) to authenticated;

-- ============================================================ 7. SePay: nhận cả mã đơn hàng FG… và mã gia hạn NH…
create or replace function public.fg_sepay_confirm(p jsonb, p_key text)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_key text;
  v_text text := upper(coalesce(p->>'code', '') || ' ' || coalesce(p->>'content', ''));
  v_code text;
  v_amount bigint := coalesce((p->>'transferAmount')::bigint, 0);
  v_order public.fg_orders;
  v_sub public.fg_subscription_payments;
  v_inserted int;
  v_result text;
begin
  select value into v_key from public.fg_secrets where key = 'sepay_api_key';
  if v_key is null or p_key is distinct from v_key then return 'unauthorized'; end if;
  if p->>'transferType' is distinct from 'in' or p->>'id' is null then return 'ignored'; end if;

  -- ngân hàng có thể chèn thêm chữ vào nội dung CK → tìm mã FGxxxxxxxx (đơn) hoặc NHxxxxxxxx (phí duy trì)
  v_code := coalesce(substring(v_text from 'FG[0-9A-F]{8}'), substring(v_text from 'NH[0-9A-F]{8}'));

  insert into public.fg_bank_transactions (id, order_code, amount, content, gateway)
  values ((p->>'id')::bigint, v_code, v_amount, p->>'content', p->>'gateway')
  on conflict (id) do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then return 'duplicate'; end if;

  if v_code is null then
    v_result := 'no_code';
  elsif v_code like 'NH%' then
    select * into v_sub from public.fg_subscription_payments where code = v_code;
    if v_sub.id is null then v_result := 'not_found';
    elsif v_sub.status = 'paid' then v_result := 'already_paid';
    elsif v_amount < v_sub.amount then v_result := 'amount_mismatch';
    else perform public.fg_apply_subscription(v_sub.id); v_result := 'ok';
    end if;
  else
    select * into v_order from public.fg_orders where code = v_code for update;
    if v_order.id is null then v_result := 'not_found';
    elsif v_order.payment_status <> 'unpaid' then v_result := 'already_paid';
    elsif v_amount < v_order.total then v_result := 'amount_mismatch';
    else
      -- tiền đã vào → đã thanh toán + tự xác nhận đơn (trừ khi khách đã hủy: admin sẽ thấy "Cần hoàn tiền")
      update public.fg_orders
      set payment_status = 'paid', status = case when status = 'pending' then 'confirmed' else status end
      where id = v_order.id;
      v_result := 'ok';
    end if;
  end if;
  update public.fg_bank_transactions set result = v_result where id = (p->>'id')::bigint;
  return v_result;
end $$;
grant execute on function public.fg_sepay_confirm(jsonb, text) to anon, authenticated;

-- ============================================================ 8. admin (demo): cấp nhà hàng, xác nhận tiền thủ công
-- TẠM THỜI cho buổi demo; xóa khi quy trình đã tự động hoàn toàn.
create or replace function public.fg_admin_grant_restaurant(p_user uuid, p_restaurant_id bigint default null, p_name text default null, p_months int default 1)
returns bigint language plpgsql security definer set search_path = public as $$
declare
  v_id bigint;
  v_pay bigint;
begin
  if not public.fg_is_admin() then raise exception 'Chỉ admin được cấp nhà hàng'; end if;
  if p_months not between 1 and 12 then raise exception 'Số tháng không hợp lệ'; end if;
  select id into v_id from public.fg_restaurants where owner_id = p_user;
  if v_id is not null then
    -- đã có quán: chỉ gia hạn thêm
    if p_restaurant_id is not null and p_restaurant_id <> v_id then raise exception 'Người dùng này đã có nhà hàng khác'; end if;
  elsif p_restaurant_id is not null then
    if exists (select 1 from public.fg_restaurants where id = p_restaurant_id and owner_id is not null and owner_id <> p_user) then
      raise exception 'Nhà hàng này đã có chủ';
    end if;
    update public.fg_restaurants set owner_id = p_user where id = p_restaurant_id returning id into v_id;
    if v_id is null then raise exception 'Không tìm thấy nhà hàng'; end if;
  else
    if coalesce(trim(p_name), '') = '' then raise exception 'Vui lòng nhập tên nhà hàng'; end if;
    insert into public.fg_restaurants (name, owner_id, rating, review_count) values (trim(p_name), p_user, 0, 0) returning id into v_id;
  end if;
  insert into public.fg_subscription_payments (code, restaurant_id, owner_id, months, amount, method)
  values (public.fg_new_subscription_code(), v_id, p_user, p_months, 0, 'admin')
  returning id into v_pay;
  perform public.fg_apply_subscription(v_pay);
  return v_id;
end $$;
grant execute on function public.fg_admin_grant_restaurant(uuid, bigint, text, int) to authenticated;

create or replace function public.fg_admin_confirm_subscription(p_payment_id bigint)
returns public.fg_subscription_payments language plpgsql security definer set search_path = public as $$
begin
  if not public.fg_is_admin() then raise exception 'Chỉ admin được xác nhận'; end if;
  return public.fg_apply_subscription(p_payment_id);
end $$;
grant execute on function public.fg_admin_confirm_subscription(bigint) to authenticated;

select (select count(*) from public.fg_restaurants where owner_id is null) as admin_restaurants,
  public.fg_subscription_fee() as fee,
  public.fg_sepay_confirm('{"id": 1, "transferType": "in"}'::jsonb, 'wrong-key') as wrong_key_result;
