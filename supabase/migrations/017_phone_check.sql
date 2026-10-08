-- Ràng buộc số điện thoại ở tầng database (cùng quy tắc với lib/validate.ts), để gọi thẳng API
-- cũng không lưu được số sai. Chỉ nhận số Việt Nam, lưu dạng 0xxxxxxxxx:
--   di động 10 số (03/05/07/08/09), cố định 11 số (02x).
-- Chạy SAU 016_remove_mfa.sql. An toàn khi chạy lại.

begin;

-- "+84 912.345.678" → "0912345678"; null nếu không phải số hợp lệ
create or replace function public.fg_normalize_phone(p text)
returns text language sql immutable set search_path = public as $$
  select case when d ~ '^0([35789][0-9]{8}|2[0-9]{9})$' then d end
  from (select regexp_replace(regexp_replace(coalesce(p, ''), '[\s.-]', '', 'g'), '^\+?84', '0') as d) x
$$;

-- ============================================================ 1. hồ sơ + nhà hàng: CHECK
-- SĐT không bắt buộc ở 2 bảng này: chuẩn hóa số cũ, số sai không cứu được thì xóa (null)
update public.fg_profiles set phone = public.fg_normalize_phone(phone)
where phone is distinct from public.fg_normalize_phone(phone);
update public.fg_restaurants set phone = public.fg_normalize_phone(phone)
where phone is distinct from public.fg_normalize_phone(phone);

alter table public.fg_profiles drop constraint if exists fg_profiles_phone_check;
alter table public.fg_profiles add constraint fg_profiles_phone_check
  check (phone is null or phone = public.fg_normalize_phone(phone));
alter table public.fg_restaurants drop constraint if exists fg_restaurants_phone_check;
alter table public.fg_restaurants add constraint fg_restaurants_phone_check
  check (phone is null or phone = public.fg_normalize_phone(phone));

-- ============================================================ 2. địa chỉ + đơn hàng: trigger
-- SĐT bắt buộc ở đây nên số cũ sai không thể xóa. CHECK sẽ chặn MỌI lần sửa dòng cũ (kể cả đổi
-- trạng thái đơn), nên dùng trigger: chỉ kiểm tra khi thêm mới hoặc khi chính cột phone được ghi.
-- Đơn cũ giữ nguyên (lịch sử); địa chỉ cũ sai phải sửa số khi chỉnh sửa / khi đặt hàng.
update public.fg_addresses set phone = public.fg_normalize_phone(phone)
where public.fg_normalize_phone(phone) is not null and phone <> public.fg_normalize_phone(phone);

create or replace function public.fg_check_phone()
returns trigger language plpgsql set search_path = public as $$
declare v text := public.fg_normalize_phone(new.phone);
begin
  if v is null then raise exception 'Số điện thoại không hợp lệ (VD: 0912 345 678)'; end if;
  new.phone := v;
  return new;
end $$;

drop trigger if exists fg_addresses_check_phone on public.fg_addresses;
create trigger fg_addresses_check_phone before insert or update of phone on public.fg_addresses
  for each row execute function public.fg_check_phone();
drop trigger if exists fg_orders_check_phone on public.fg_orders;
create trigger fg_orders_check_phone before insert or update of phone on public.fg_orders
  for each row execute function public.fg_check_phone();

commit;

-- kiểm tra: cả 4 cột phải bằng 0
select
  (select count(*) from public.fg_profiles where phone is not null and phone <> coalesce(public.fg_normalize_phone(phone), '')) as profiles_bad,
  (select count(*) from public.fg_restaurants where phone is not null and phone <> coalesce(public.fg_normalize_phone(phone), '')) as restaurants_bad,
  (select count(*) from pg_trigger where tgname in ('fg_addresses_check_phone', 'fg_orders_check_phone')) - 2 as triggers_missing,
  (select count(*) from pg_constraint where conname in ('fg_profiles_phone_check', 'fg_restaurants_phone_check')) - 2 as checks_missing;
