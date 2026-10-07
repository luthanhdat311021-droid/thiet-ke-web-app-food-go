-- FoodGo – thanh toán ví MoMo. Chạy SAU 002_maps.sql (an toàn khi chạy lại).

-- 1. Cho phép phương thức 'momo' + lưu mã giao dịch MoMo
alter table public.fg_orders drop constraint if exists fg_orders_payment_method_check;
alter table public.fg_orders add constraint fg_orders_payment_method_check check (payment_method in ('cod', 'qr', 'momo'));
alter table public.fg_orders add column if not exists momo_trans_id text;

-- 2. Khóa bí mật lưu trong DB (RLS bật, không có policy → API không đọc được; chỉ hàm security definer đọc)
create table if not exists public.fg_secrets (key text primary key, value text not null);
alter table public.fg_secrets enable row level security;
revoke all on public.fg_secrets from anon, authenticated;

-- Khóa SANDBOX công khai trong tài liệu MoMo (test-payment.momo.vn) – không trừ tiền thật.
-- Khi có tài khoản doanh nghiệp: update public.fg_secrets set value = '...' where key = ...;
insert into public.fg_secrets (key, value) values
  ('momo_access_key', 'F8BBA842ECF85'),
  ('momo_secret_key', 'K951B6PE1waDMi640xX08PD3vg6EkVlz')
on conflict (key) do nothing;

-- 3. Xác nhận thanh toán MoMo (IPN hoặc redirect). Kiểm tra chữ ký HMAC-SHA256 ngay trong DB,
--    nên ai gọi hàm này cũng không thể giả mạo trạng thái "đã thanh toán".
create or replace function public.fg_momo_confirm(p jsonb)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_access text;
  v_secret text;
  v_raw text;
  v_order public.fg_orders;
begin
  select value into v_access from public.fg_secrets where key = 'momo_access_key';
  select value into v_secret from public.fg_secrets where key = 'momo_secret_key';
  if v_access is null or v_secret is null then return 'not_configured'; end if;

  v_raw := 'accessKey=' || v_access
    || '&amount=' || coalesce(p->>'amount', '')
    || '&extraData=' || coalesce(p->>'extraData', '')
    || '&message=' || coalesce(p->>'message', '')
    || '&orderId=' || coalesce(p->>'orderId', '')
    || '&orderInfo=' || coalesce(p->>'orderInfo', '')
    || '&orderType=' || coalesce(p->>'orderType', '')
    || '&partnerCode=' || coalesce(p->>'partnerCode', '')
    || '&payType=' || coalesce(p->>'payType', '')
    || '&requestId=' || coalesce(p->>'requestId', '')
    || '&responseTime=' || coalesce(p->>'responseTime', '')
    || '&resultCode=' || coalesce(p->>'resultCode', '')
    || '&transId=' || coalesce(p->>'transId', '');

  if encode(extensions.hmac(v_raw, v_secret, 'sha256'), 'hex') is distinct from p->>'signature' then
    return 'invalid_signature';
  end if;
  if p->>'resultCode' is distinct from '0' then return 'failed'; end if;

  -- MoMo orderId = <mã đơn>_<thời điểm>, mỗi lần thanh toán lại là một orderId mới
  select * into v_order from public.fg_orders where code = split_part(p->>'orderId', '_', 1) for update;
  if v_order.id is null then return 'not_found'; end if;
  if v_order.payment_status = 'paid' then return 'already_paid'; end if;
  if (p->>'amount')::bigint < v_order.total then return 'amount_mismatch'; end if;

  update public.fg_orders
  set payment_status = 'paid',
      momo_trans_id = p->>'transId',
      status = case when status = 'pending' then 'confirmed' else status end
  where id = v_order.id;
  return 'ok';
end $$;

grant execute on function public.fg_momo_confirm(jsonb) to anon, authenticated;

-- 4. Đặt hàng: chấp nhận 'momo'
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

-- kiểm tra: hàm hmac có sẵn và trả về chuỗi hex
select encode(extensions.hmac('test', 'key', 'sha256'), 'hex') as hmac_ok;
