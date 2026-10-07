-- Mở rộng từ 1 quán thành NHIỀU nhà hàng (admin thêm nhà hàng, ghim vị trí trên bản đồ).
-- Chạy SAU 012_hours_vouchers.sql. An toàn khi chạy lại. Dữ liệu quán FoodGo hiện có được giữ nguyên.

-- 1. Nhà hàng mới: chưa có đánh giá thì điểm = 0 ("Chưa có đánh giá")
alter table public.fg_restaurants alter column rating set default 0;
create index if not exists fg_orders_restaurant_idx on public.fg_orders(restaurant_id, created_at desc);

-- 2. Điểm nhà hàng = trung bình đánh giá các món CỦA NHÀ HÀNG ĐÓ (trước đây tính chung cho cả hệ thống)
create or replace function public.fg_refresh_ratings()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_food bigint := coalesce(new.food_id, old.food_id);
  v_restaurant bigint := (select restaurant_id from public.fg_foods where id = v_food);
begin
  update public.fg_foods f
  set rating = coalesce((select round(avg(r.rating)::numeric, 1) from public.fg_reviews r where r.food_id = v_food), 0),
      review_count = (select count(*) from public.fg_reviews r where r.food_id = v_food)
  where f.id = v_food;

  update public.fg_restaurants rs
  set rating = coalesce((select round(avg(r.rating)::numeric, 1) from public.fg_reviews r join public.fg_foods f on f.id = r.food_id where f.restaurant_id = rs.id), 0),
      review_count = (select count(*) from public.fg_reviews r join public.fg_foods f on f.id = r.food_id where f.restaurant_id = rs.id)
  where rs.id = v_restaurant;
  return null;
end $$;

update public.fg_restaurants rs
set rating = coalesce((select round(avg(r.rating)::numeric, 1) from public.fg_reviews r join public.fg_foods f on f.id = r.food_id where f.restaurant_id = rs.id), 0),
    review_count = (select count(*) from public.fg_reviews r join public.fg_foods f on f.id = r.food_id where f.restaurant_id = rs.id)
where true;

-- 3. Đặt hàng: thông báo lỗi nêu tên nhà hàng; mỗi đơn chỉ từ một nhà hàng (logic giữ nguyên 012)
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
  if not v_restaurant.is_active then raise exception '% đang tạm ngưng nhận đơn', v_restaurant.name; end if;
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

-- 4. Thông báo: "Quán đã xác nhận" → "Nhà hàng đã xác nhận" (sửa định nghĩa hàm hiện có)
do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  if position('Quán đã xác nhận' in d) > 0 then
    execute replace(replace(d, 'Quán đã xác nhận', 'Nhà hàng đã xác nhận'), 'Quán đang chuẩn bị', 'Nhà hàng đang chuẩn bị');
  end if;
end $$;

select id, name, rating, review_count, lat, lng from public.fg_restaurants order by id;
