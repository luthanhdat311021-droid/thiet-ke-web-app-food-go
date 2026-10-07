-- FoodGo chỉ phục vụ MỘT quán duy nhất (đường Trần Văn Ơn, gần cổng ĐH Thủ Dầu Một).
-- Giữ bảng fg_restaurants với đúng 1 dòng = thông tin quán; gộp toàn bộ món vào quán này.
-- Chạy SAU 004_tdmu_restaurants.sql. An toàn khi chạy lại.

do $$
declare
  v_store bigint;
begin
  -- quán chính = Cơm Nhà cũ (đã ở Trần Văn Ơn), hoặc quán đầu tiên nếu đã đổi tên
  select id into v_store from public.fg_restaurants
  order by (name = 'FoodGo') desc, (name = 'Cơm Nhà') desc, id
  limit 1;
  if v_store is null then raise exception 'Chưa có dữ liệu quán (chạy seed.sql trước)'; end if;

  update public.fg_restaurants set
    name = 'FoodGo',
    cuisine = 'Cơm • Gà rán • Pizza • Mì • Trà sữa • Healthy',
    address = 'Đường Trần Văn Ơn (gần cổng ĐH Thủ Dầu Một), Phú Hòa, Thủ Dầu Một',
    lat = coalesce(lat, 10.98000), lng = coalesce(lng, 106.67380),
    distance_km = 0.2, delivery_time = '15-25 phút', tag = 'Freeship từ 100k', is_active = true
  where id = v_store;

  -- gộp món và đơn cũ về quán chính trước khi xóa các quán khác (fg_foods xóa theo dây chuyền)
  update public.fg_foods set restaurant_id = v_store where restaurant_id <> v_store;
  update public.fg_orders set restaurant_id = v_store, restaurant_name = 'FoodGo'
  where restaurant_id is distinct from v_store or restaurant_name <> 'FoodGo';
  delete from public.fg_restaurants where id <> v_store;
end $$;

-- đổi chữ "Nhà hàng" thành "Quán" trong thông báo và lỗi (sửa định nghĩa hàm hiện có)
do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  execute replace(replace(d, 'Nhà hàng đã xác nhận', 'Quán đã xác nhận'), 'Nhà hàng đang chuẩn bị', 'Quán đang chuẩn bị');
  d := pg_get_functiondef('public.fg_place_order(jsonb, text, text, text, text, text, double precision, double precision)'::regprocedure);
  execute replace(replace(d, 'Nhà hàng đang tạm ngưng', 'Quán đang tạm ngưng nhận đơn'), 'từ một nhà hàng', 'trong một lần');
end $$;

select r.id, r.name, r.address, (select count(*) from public.fg_foods f where f.restaurant_id = r.id) as foods
from public.fg_restaurants r;
