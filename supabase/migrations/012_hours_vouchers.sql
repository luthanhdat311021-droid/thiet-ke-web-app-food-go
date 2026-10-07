-- Giờ mở cửa + mã giảm giá (voucher). Chạy SAU 011_reviews.sql. An toàn khi chạy lại.
-- Giờ giấc tính theo giờ Việt Nam (Asia/Ho_Chi_Minh), không phụ thuộc đồng hồ máy khách.

-- 1. Giờ mở cửa: is_open = công tắc "tạm đóng cửa" (khác is_active: tắt is_active thì khách không thấy quán)
alter table public.fg_restaurants add column if not exists open_time time default '07:00';
alter table public.fg_restaurants add column if not exists close_time time default '21:00';
alter table public.fg_restaurants add column if not exists is_open boolean not null default true;

-- quán có đang nhận đơn không (hỗ trợ ca qua đêm, vd 18:00 → 02:00)
create or replace function public.fg_store_open(r public.fg_restaurants)
returns boolean language sql stable as $$
  select r.is_open and (
    r.open_time is null or r.close_time is null or r.open_time = r.close_time
    or case when r.open_time < r.close_time then t >= r.open_time and t < r.close_time
            else t >= r.open_time or t < r.close_time end)
  from (select (now() at time zone 'Asia/Ho_Chi_Minh')::time as t) x
$$;

-- 2. Mã giảm giá
create table if not exists public.fg_vouchers (
  id bigint generated always as identity primary key,
  code text not null unique,
  description text,
  discount_type text not null default 'amount' check (discount_type in ('percent', 'amount', 'freeship')),
  discount_value int not null default 0 check (discount_value >= 0),
  max_discount int check (max_discount is null or max_discount > 0),      -- trần giảm cho mã %
  min_subtotal int not null default 0 check (min_subtotal >= 0),          -- đơn tối thiểu (tiền món)
  usage_limit int check (usage_limit is null or usage_limit > 0),         -- tổng lượt dùng
  per_user_limit int default 1 check (per_user_limit is null or per_user_limit > 0),
  starts_on date,
  expires_on date,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  check (discount_type <> 'percent' or discount_value between 1 and 100)
);

-- mã luôn viết HOA, không khoảng trắng
create or replace function public.fg_normalize_voucher()
returns trigger language plpgsql as $$
begin
  new.code := upper(regexp_replace(new.code, '\s', '', 'g'));
  if new.code = '' then raise exception 'Mã giảm giá không được để trống'; end if;
  return new;
end $$;
drop trigger if exists fg_vouchers_normalize on public.fg_vouchers;
create trigger fg_vouchers_normalize before insert or update on public.fg_vouchers
  for each row execute function public.fg_normalize_voucher();

-- chỉ admin xem/sửa danh sách mã; khách kiểm tra mã qua fg_check_voucher()
alter table public.fg_vouchers enable row level security;
drop policy if exists "fg vouchers admin all" on public.fg_vouchers;
create policy "fg vouchers admin all" on public.fg_vouchers for all using (public.fg_is_admin()) with check (public.fg_is_admin());

alter table public.fg_orders add column if not exists voucher_code text;
alter table public.fg_orders add column if not exists discount int not null default 0;
create index if not exists fg_orders_voucher_idx on public.fg_orders(voucher_code) where voucher_code is not null;

-- số lượt đã dùng (đơn bị hủy trả lại lượt). Admin chọn được như một cột: select=*,fg_used_count
create or replace function public.fg_used_count(v public.fg_vouchers)
returns int language sql stable security definer set search_path = public as $$
  select count(*)::int from public.fg_orders where voucher_code = v.code and status <> 'cancelled'
$$;

-- tính số tiền giảm; báo lỗi rõ ràng nếu mã không dùng được. Khóa dòng mã để 2 đơn cùng lúc không vượt lượt.
create or replace function public.fg_voucher_quote(p_code text, p_user uuid, p_subtotal int, p_shipping int)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v public.fg_vouchers;
  v_today date := (now() at time zone 'Asia/Ho_Chi_Minh')::date;
  v_count int;
  v_discount int;
begin
  select * into v from public.fg_vouchers where code = upper(regexp_replace(coalesce(p_code, ''), '\s', '', 'g')) for update;
  if not found or not v.is_active then raise exception 'Mã giảm giá không tồn tại hoặc đã ngừng áp dụng'; end if;
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
revoke execute on function public.fg_voucher_quote(text, uuid, int, int) from public, anon, authenticated;

-- khách bấm "Áp dụng" ở trang thanh toán
create or replace function public.fg_check_voucher(p_code text, p_subtotal int)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'Bạn cần đăng nhập để dùng mã giảm giá'; end if;
  return public.fg_voucher_quote(p_code, auth.uid(), p_subtotal, case when p_subtotal >= 100000 then 0 else 15000 end);
end $$;
grant execute on function public.fg_check_voucher(text, int) to authenticated;

-- 3. Đặt hàng: kiểm tra giờ mở cửa + áp mã giảm giá (thêm tham số p_voucher_code)
drop function if exists public.fg_place_order(jsonb, text, text, text, text, text, double precision, double precision);
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
  if v_restaurant_count <> 1 then raise exception 'Mỗi đơn chỉ được đặt món trong một lần'; end if;

  select r.* into v_restaurant from public.fg_restaurants r
  where r.id = (select f.restaurant_id from public.fg_foods f where f.id = (p_items->0->>'food_id')::bigint);
  if not v_restaurant.is_active then raise exception 'Quán đang tạm ngưng nhận đơn'; end if;
  if not public.fg_store_open(v_restaurant) then
    if not v_restaurant.is_open then raise exception 'Quán đang tạm đóng cửa, vui lòng quay lại sau'; end if;
    raise exception 'Quán đã đóng cửa. Giờ nhận đơn: % - %', to_char(v_restaurant.open_time, 'HH24:MI'), to_char(v_restaurant.close_time, 'HH24:MI');
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
    v_quote := public.fg_voucher_quote(p_voucher_code, v_user, v_subtotal, v_shipping);
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

-- mã mẫu để thử: giảm 20.000đ cho đơn từ 80.000đ, mỗi khách 1 lần
insert into public.fg_vouchers (code, description, discount_type, discount_value, min_subtotal, per_user_limit)
values ('CHAOBAN20K', 'Giảm 20.000đ cho đơn từ 80.000đ', 'amount', 20000, 80000, 1)
on conflict (code) do nothing;

select (select open_time from public.fg_restaurants order by id limit 1) as open_time,
  (select public.fg_store_open(r) from public.fg_restaurants r order by id limit 1) as store_open_now,
  (select count(*) from public.fg_vouchers) as vouchers;
