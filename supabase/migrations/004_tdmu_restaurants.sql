-- Chuyển các nhà hàng mẫu về quanh Trường Đại học Thủ Dầu Một (6 Trần Văn Ơn, Phú Hòa, Thủ Dầu Một).
-- Tọa độ đã kiểm tra bằng reverse geocoding (OpenStreetMap) – mỗi điểm nằm đúng trên con đường ghi trong địa chỉ.
-- Chỉ ghi tên đường, không ghi số nhà, để không trùng với cửa hàng có thật.

update public.fg_restaurants r
set address = v.address, lat = v.lat, lng = v.lng, distance_km = v.km, delivery_time = v.eta
from (values
  ('Cơm Nhà',      'Đường Trần Văn Ơn (gần cổng ĐH Thủ Dầu Một), Phú Hòa, Thủ Dầu Một', 10.98000, 106.67380, 0.2, '10-15 phút'),
  ('Gà Giòn 24H',  'Đại lộ Bình Dương, Phú Hòa, Thủ Dầu Một',                          10.98060, 106.66840, 0.7, '10-20 phút'),
  ('Pizza Corner', 'Đường Lê Hồng Phong, Phú Hòa, Thủ Dầu Một',                         10.98060, 106.68040, 0.7, '15-25 phút'),
  ('Bếp Việt',     'Đường Đoàn Thị Liên, Phú Lợi, Thủ Dầu Một',                         10.98660, 106.67440, 0.7, '15-20 phút'),
  ('Trà Sữa Mộc',  'Đường 30 Tháng 4, Phú Hòa, Thủ Dầu Một',                            10.97460, 106.67440, 0.7, '10-15 phút'),
  ('Green Bowl',   'Đường Huỳnh Văn Lũy, Phú Lợi, Thủ Dầu Một',                         10.98660, 106.66840, 0.9, '15-25 phút')
) as v(name, address, lat, lng, km, eta)
where r.name = v.name;

select name, address, lat, lng, distance_km, delivery_time from public.fg_restaurants order by id;
