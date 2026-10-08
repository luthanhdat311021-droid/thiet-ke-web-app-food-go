-- Gỡ xác thực 2 lớp (2FA) đã thêm ở 015: quyền admin / chủ quán không còn phụ thuộc mã 2FA.
-- Các phần khác của 015 (giới hạn tần suất, nhật ký bảo mật, giới hạn file) vẫn giữ nguyên.
-- Chạy SAU 015_security.sql. An toàn khi chạy lại.

create or replace function public.fg_is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.fg_profiles where id = auth.uid() and role = 'admin');
$$;

create or replace function public.fg_owns_restaurant(p_restaurant_id bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.fg_restaurants where id = p_restaurant_id and owner_id = auth.uid())
$$;

drop policy if exists "fg restaurants owner update" on public.fg_restaurants;
create policy "fg restaurants owner update" on public.fg_restaurants for update
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());

drop function if exists public.fg_mfa_ok();

select position('mfa' in pg_get_functiondef('public.fg_is_admin'::regproc)) = 0 as admin_check_ok,
  position('mfa' in pg_get_functiondef('public.fg_owns_restaurant'::regproc)) = 0 as owner_check_ok;
