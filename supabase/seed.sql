-- Dữ liệu mẫu cho FoodGo. Chạy SAU schema.sql, chỉ chạy 1 lần trên database trống.

insert into public.fg_categories (name, image, sort) values
  ('Cơm',         'https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=160&h=160&q=80', 1),
  ('Đồ ăn nhanh', 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=160&h=160&q=80', 2),
  ('Pizza',       'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=160&h=160&q=80', 3),
  ('Gà rán',      'https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?auto=format&fit=crop&w=160&h=160&q=80', 4),
  ('Đồ uống',     'https://images.unsplash.com/photo-1544145945-f90425340c7e?auto=format&fit=crop&w=160&h=160&q=80', 5),
  ('Trà sữa',     'https://images.unsplash.com/photo-1558857563-b371033873b8?auto=format&fit=crop&w=160&h=160&q=80', 6),
  ('Mì',          'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=160&h=160&q=80', 7),
  ('Healthy',     'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=160&h=160&q=80', 8),
  ('Tráng miệng', 'https://images.unsplash.com/photo-1488477181946-6428a0291777?auto=format&fit=crop&w=160&h=160&q=80', 9)
on conflict (name) do nothing;

insert into public.fg_restaurants (name, cuisine, address, image, logo, rating, review_count, distance_km, delivery_time, tag) values
  ('Cơm Nhà',      'Cơm Việt • Món gia đình', '45 Lê Lợi, Quận 1',
   'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=900&q=85',
   'https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=160&q=85', 4.8, 2400, 1.2, '20-30 phút', 'Freeship'),
  ('Gà Giòn 24H',  'Gà rán • Đồ ăn nhanh', '12 Pasteur, Quận 1',
   'https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?auto=format&fit=crop&w=900&q=85',
   'https://images.unsplash.com/photo-1562967916-eb82221dfb92?auto=format&fit=crop&w=160&q=85', 4.9, 1800, 0.8, '15-25 phút', 'Bán chạy'),
  ('Pizza Corner', 'Pizza • Đồ ăn nhanh', '88 Hai Bà Trưng, Quận 1',
   'https://images.unsplash.com/photo-1579751626657-72bc17010498?auto=format&fit=crop&w=900&q=85',
   'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=160&q=85', 4.7, 980, 2.1, '25-35 phút', 'Giảm 20%'),
  ('Bếp Việt',     'Bún • Mì • Món Việt', '21 Nguyễn Trãi, Quận 5',
   'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=900&q=85',
   'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=160&q=85', 4.6, 1200, 1.8, '20-30 phút', null),
  ('Trà Sữa Mộc',  'Trà sữa • Đồ uống • Tráng miệng', '5 Võ Văn Tần, Quận 3',
   'https://images.unsplash.com/photo-1558857563-b371033873b8?auto=format&fit=crop&w=900&q=85',
   'https://images.unsplash.com/photo-1544145945-f90425340c7e?auto=format&fit=crop&w=160&q=85', 4.8, 3100, 1.0, '10-20 phút', 'Mua 2 tặng 1'),
  ('Green Bowl',   'Healthy • Salad', '102 Điện Biên Phủ, Bình Thạnh',
   'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=900&q=85',
   'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=160&q=85', 4.7, 640, 2.6, '25-35 phút', 'Mới');

-- helper: lấy id theo tên
with r as (select id, name from public.fg_restaurants), c as (select id, name from public.fg_categories)
insert into public.fg_foods (restaurant_id, category_id, name, description, price, old_price, image, rating, is_popular)
select r.id, c.id, f.name, f.description, f.price, f.old_price, f.image, f.rating, f.popular
from (values
  ('Cơm Nhà',      'Cơm',         'Cơm gà xối mỡ',      'Đùi gà chiên giòn xối mỡ hành, cơm chiên vàng ruộm, kèm dưa leo và nước mắm chua ngọt.', 35000, 42000, 'photo-1512058564366-18510be2db19', 4.8, true),
  ('Cơm Nhà',      'Cơm',         'Cơm tấm sườn bì chả','Sườn nướng than hồng, bì, chả trứng, mỡ hành, nước mắm kẹo.',                           45000, null,  'photo-1547592180-85f173990554', 4.7, false),
  ('Cơm Nhà',      'Healthy',     'Cơm gạo lứt rau củ', 'Cơm gạo lứt, rau củ luộc, đậu hũ non, sốt mè rang.',                                     39000, null,  'photo-1512621776951-a57141f2eefd', 4.5, false),
  ('Gà Giòn 24H',  'Gà rán',      'Gà rán sốt cay',     '3 miếng gà rán giòn rụm phủ sốt cay Hàn Quốc.',                                          49000, 59000, 'photo-1562967916-eb82221dfb92', 4.9, true),
  ('Gà Giòn 24H',  'Gà rán',      'Combo gà giòn 2 người','4 miếng gà, 2 khoai tây chiên, 2 nước ngọt.',                                         129000, 149000,'photo-1626082927389-6cd097cdc6ec', 4.8, false),
  ('Gà Giòn 24H',  'Đồ ăn nhanh', 'Burger bò phô mai',  'Bò nướng, phô mai cheddar, rau xà lách, cà chua, sốt đặc biệt.',                        55000, null,  'photo-1568901346375-23c9450c58cd', 4.6, false),
  ('Pizza Corner', 'Pizza',       'Pizza hải sản',      'Tôm, mực, nghêu, phô mai mozzarella, sốt cà chua, đế mỏng giòn.',                       89000, null,  'photo-1579751626657-72bc17010498', 4.7, true),
  ('Pizza Corner', 'Pizza',       'Pizza pepperoni',    'Xúc xích pepperoni, phô mai mozzarella kéo sợi, lá húng tây.',                           99000, 119000,'photo-1565299624946-b28f40a0ae38', 4.8, false),
  ('Pizza Corner', 'Mì',          'Mì Ý bò bằm',        'Spaghetti sốt bò bằm cà chua, phô mai parmesan.',                                        69000, null,  'photo-1551892374-ecf8754cf8b0', 4.6, false),
  ('Bếp Việt',     'Mì',          'Bún bò Huế',         'Nước dùng đậm đà sả ớt, bắp bò, chả cua, giò heo.',                                       55000, null,  'photo-1582878826629-29b7ad1cdc43', 4.9, true),
  ('Bếp Việt',     'Mì',          'Mì ramen trứng lòng đào','Mì ramen nước tonkotsu, chả cá, trứng lòng đào, rau thơm.',                          65000, null,  'photo-1569718212165-3a8278d5f624', 4.7, false),
  ('Trà Sữa Mộc',  'Trà sữa',     'Trà sữa trân châu',  'Trà đen ủ lạnh, sữa tươi, trân châu đường đen.',                                         29000, null,  'photo-1558857563-b371033873b8', 4.8, true),
  ('Trà Sữa Mộc',  'Đồ uống',     'Nước ép trái cây nhiệt đới','Xoài, chanh dây, thơm tươi ép lạnh.',                                              35000, null,  'photo-1544145945-f90425340c7e', 4.6, false),
  ('Trà Sữa Mộc',  'Tráng miệng', 'Panna cotta dâu',    'Panna cotta sữa tươi mềm mịn, sốt dâu tây.',                                             32000, null,  'photo-1488477181946-6428a0291777', 4.7, false),
  ('Trà Sữa Mộc',  'Tráng miệng', 'Donut sô-cô-la',     'Combo 3 bánh donut phủ sô-cô-la và cốm màu.',                                            45000, 52000, 'photo-1551024601-bec78aea704b', 4.5, false),
  ('Green Bowl',   'Healthy',     'Salad bơ tôm',       'Bơ sáp, tôm luộc, cà chua bi, rau mầm, sốt chanh dây.',                                  75000, null,  'photo-1512621776951-a57141f2eefd', 4.8, true)
) as f(restaurant, category, name, description, price, old_price, image, rating, popular)
join r on r.name = f.restaurant
join c on c.name = f.category;

-- chuyển id ảnh Unsplash thành URL đầy đủ
update public.fg_foods
set image = 'https://images.unsplash.com/' || image || '?auto=format&fit=crop&w=900&q=85'
where image like 'photo-%';
