-- FoodGo – bản đồ & vị trí. Chạy SAU schema.sql + seed.sql (an toàn khi chạy lại).

-- 1. Tọa độ
alter table public.fg_restaurants add column if not exists lat double precision;
alter table public.fg_restaurants add column if not exists lng double precision;
alter table public.fg_addresses   add column if not exists lat double precision;
alter table public.fg_addresses   add column if not exists lng double precision;
alter table public.fg_orders      add column if not exists delivery_lat double precision;
alter table public.fg_orders      add column if not exists delivery_lng double precision;

-- Tọa độ gần đúng của các nhà hàng mẫu (admin có thể sửa trong /admin → Nhà hàng)
update public.fg_restaurants r set lat = v.lat, lng = v.lng
from (values
  ('Cơm Nhà',      10.77395, 106.70095),
  ('Gà Giòn 24H',  10.77560, 106.70250),
  ('Pizza Corner', 10.77920, 106.70210),
  ('Bếp Việt',     10.75600, 106.67950),
  ('Trà Sữa Mộc',  10.77700, 106.69260),
  ('Green Bowl',   10.80050, 106.71050)
) as v(name, lat, lng)
where r.name = v.name and r.lat is null;

-- 2. Đặt hàng kèm tọa độ giao hàng (thêm p_lat, p_lng)
drop function if exists public.fg_place_order(jsonb, text, text, text, text, text);

create or replace function public.fg_place_order(
  p_items jsonb,
  p_recipient text,
  p_phone text,
  p_address text,
  p_payment_method text,
  p_note text default null,
  p_lat double precision default null,
  p_lng double precision default null
) returns public.fg_orders
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_restaurant public.fg_restaurants;
  v_restaurant_count int;
  v_subtotal int := 0;
  v_shipping int;
  v_order public.fg_orders;
  v_code text;
  it record;
begin
  if v_user is null then raise exception 'Bạn cần đăng nhập để đặt hàng'; end if;
  if p_payment_method not in ('cod','qr') then raise exception 'Phương thức thanh toán không hợp lệ'; end if;
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
  if not v_restaurant.is_active then raise exception 'Nhà hàng đang tạm ngưng'; end if;

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

  loop
    v_code := 'FG' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
    exit when not exists (select 1 from public.fg_orders where code = v_code);
  end loop;

  insert into public.fg_orders (code, user_id, restaurant_id, restaurant_name, payment_method,
    subtotal, shipping_fee, total, recipient, phone, address, note, delivery_lat, delivery_lng)
  values (v_code, v_user, v_restaurant.id, v_restaurant.name, p_payment_method,
    v_subtotal, v_shipping, v_subtotal + v_shipping, trim(p_recipient), trim(p_phone), trim(p_address),
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

grant execute on function public.fg_place_order(jsonb, text, text, text, text, text, double precision, double precision) to authenticated;

select name, lat, lng from public.fg_restaurants order by id;
