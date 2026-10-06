-- FoodGo database schema for Supabase
-- Chạy toàn bộ file này trong Supabase Dashboard → SQL Editor → New query → Run.
-- Chạy lại được nhiều lần (idempotent).
-- Mọi bảng, hàm, trigger, policy đều có tiền tố fg_ để dùng chung project với app khác
-- mà không đụng tới bảng của app đó.

-- ============================================================
-- 1. TABLES
-- ============================================================

create table if not exists public.fg_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  phone text,
  birthday date,
  avatar_url text,
  role text not null default 'customer' check (role in ('customer', 'admin')),
  created_at timestamptz not null default now()
);

create table if not exists public.fg_categories (
  id bigint generated always as identity primary key,
  name text not null unique,
  image text,
  sort int not null default 0
);

create table if not exists public.fg_restaurants (
  id bigint generated always as identity primary key,
  name text not null,
  cuisine text,
  address text,
  image text,
  logo text,
  rating numeric(2,1) not null default 5.0,
  review_count int not null default 0,
  distance_km numeric(4,1) not null default 1.0,
  delivery_time text not null default '20-30 phút',
  tag text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.fg_foods (
  id bigint generated always as identity primary key,
  restaurant_id bigint not null references public.fg_restaurants(id) on delete cascade,
  category_id bigint references public.fg_categories(id) on delete set null,
  name text not null,
  description text,
  price int not null check (price >= 0),
  old_price int check (old_price is null or old_price >= 0),
  image text,
  rating numeric(2,1) not null default 5.0,
  sold_count int not null default 0,
  is_available boolean not null default true,
  is_popular boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists fg_foods_restaurant_idx on public.fg_foods(restaurant_id);
create index if not exists fg_foods_category_idx on public.fg_foods(category_id);

create table if not exists public.fg_addresses (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  label text not null default 'Nhà riêng',
  recipient text not null,
  phone text not null,
  address text not null,
  is_default boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists fg_addresses_user_idx on public.fg_addresses(user_id);

create table if not exists public.fg_favorites (
  user_id uuid not null references auth.users(id) on delete cascade,
  food_id bigint not null references public.fg_foods(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, food_id)
);

create table if not exists public.fg_orders (
  id bigint generated always as identity primary key,
  code text not null unique,
  user_id uuid not null references auth.users(id) on delete cascade,
  restaurant_id bigint references public.fg_restaurants(id) on delete set null,
  restaurant_name text not null,
  status text not null default 'pending'
    check (status in ('pending','confirmed','preparing','picking_up','delivering','delivered','cancelled')),
  payment_method text not null check (payment_method in ('cod','qr')),
  payment_status text not null default 'unpaid' check (payment_status in ('unpaid','paid','refunded')),
  subtotal int not null,
  shipping_fee int not null default 0,
  total int not null,
  recipient text not null,
  phone text not null,
  address text not null,
  note text,
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists fg_orders_user_idx on public.fg_orders(user_id, created_at desc);

create table if not exists public.fg_order_items (
  id bigint generated always as identity primary key,
  order_id bigint not null references public.fg_orders(id) on delete cascade,
  food_id bigint references public.fg_foods(id) on delete set null,
  name text not null,
  image text,
  price int not null,
  qty int not null check (qty > 0)
);
create index if not exists fg_order_items_order_idx on public.fg_order_items(order_id);

create table if not exists public.fg_notifications (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  order_id bigint references public.fg_orders(id) on delete cascade,
  title text not null,
  body text,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists fg_notifications_user_idx on public.fg_notifications(user_id, created_at desc);

-- ============================================================
-- 2. HELPERS & TRIGGERS
-- ============================================================

create or replace function public.fg_is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.fg_profiles where id = auth.uid() and role = 'admin');
$$;

-- Tạo profile khi có user mới (email hoặc Google)
create or replace function public.fg_handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.fg_profiles (id, full_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name', split_part(new.email, '@', 1)),
    new.raw_user_meta_data->>'avatar_url'
  )
  on conflict (id) do nothing;
  return new;
exception when others then
  -- không bao giờ chặn việc đăng ký (auth.users dùng chung với app khác)
  raise warning 'fg_handle_new_user: %', sqlerrm;
  return new;
end $$;

drop trigger if exists fg_on_auth_user_created on auth.users;
create trigger fg_on_auth_user_created after insert on auth.users
  for each row execute function public.fg_handle_new_user();

-- Tạo profile cho các tài khoản đã có sẵn (project dùng chung với app khác)
insert into public.fg_profiles (id, full_name, avatar_url)
select u.id,
  coalesce(u.raw_user_meta_data->>'full_name', u.raw_user_meta_data->>'name', split_part(u.email, '@', 1)),
  u.raw_user_meta_data->>'avatar_url'
from auth.users u
on conflict (id) do nothing;

-- Chặn user tự nâng quyền admin
create or replace function public.fg_protect_profile_role()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  -- auth.uid() null = SQL Editor / service role: được phép (dùng để tạo admin đầu tiên)
  if new.role is distinct from old.role and auth.uid() is not null and not public.fg_is_admin() then
    raise exception 'Không có quyền thay đổi vai trò';
  end if;
  return new;
end $$;

drop trigger if exists fg_profiles_protect_role on public.fg_profiles;
create trigger fg_profiles_protect_role before update on public.fg_profiles
  for each row execute function public.fg_protect_profile_role();

-- Mỗi user chỉ có 1 địa chỉ mặc định
create or replace function public.fg_single_default_address()
returns trigger language plpgsql as $$
begin
  if new.is_default then
    update public.fg_addresses set is_default = false
    where user_id = new.user_id and id <> new.id and is_default;
  end if;
  return new;
end $$;

drop trigger if exists fg_addresses_single_default on public.fg_addresses;
create trigger fg_addresses_single_default after insert or update of is_default on public.fg_addresses
  for each row execute function public.fg_single_default_address();

-- Thông báo khi đơn hàng thay đổi
create or replace function public.fg_notify_order_change()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  msg text;
begin
  new.updated_at := now();
  if tg_op = 'INSERT' then
    insert into public.fg_notifications (user_id, order_id, title, body)
    values (new.user_id, new.id, 'Đặt hàng thành công', 'Đơn ' || new.code || ' đã được gửi tới ' || new.restaurant_name || '.');
    return new;
  end if;

  if new.status is distinct from old.status then
    msg := case new.status
      when 'confirmed'  then 'Nhà hàng đã xác nhận đơn hàng của bạn.'
      when 'preparing'  then 'Nhà hàng đang chuẩn bị món.'
      when 'picking_up' then 'Tài xế đang đến lấy hàng.'
      when 'delivering' then 'Tài xế đang giao hàng tới bạn.'
      when 'delivered'  then 'Đơn hàng đã giao thành công. Chúc ngon miệng!'
      when 'cancelled'  then 'Đơn hàng đã bị hủy.'
      else null end;
    if msg is not null then
      insert into public.fg_notifications (user_id, order_id, title, body)
      values (new.user_id, new.id, 'Đơn ' || new.code, msg);
    end if;
  end if;

  if new.payment_status = 'paid' and old.payment_status is distinct from 'paid' then
    new.paid_at := coalesce(new.paid_at, now());
    insert into public.fg_notifications (user_id, order_id, title, body)
    values (new.user_id, new.id, 'Thanh toán thành công', 'Đã nhận ' || to_char(new.total, 'FM999G999G999') || 'đ cho đơn ' || new.code || '.');
  end if;
  return new;
end $$;

drop trigger if exists fg_orders_notify_insert on public.fg_orders;
create trigger fg_orders_notify_insert after insert on public.fg_orders
  for each row execute function public.fg_notify_order_change();
drop trigger if exists fg_orders_notify_update on public.fg_orders;
create trigger fg_orders_notify_update before update on public.fg_orders
  for each row execute function public.fg_notify_order_change();

-- ============================================================
-- 3. RPC: đặt hàng / hủy đơn (giá tính ở server, không tin client)
-- ============================================================

create or replace function public.fg_place_order(
  p_items jsonb,            -- [{"food_id": 1, "qty": 2}, ...]
  p_recipient text,
  p_phone text,
  p_address text,
  p_payment_method text,    -- 'cod' | 'qr'
  p_note text default null
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
    subtotal, shipping_fee, total, recipient, phone, address, note)
  values (v_code, v_user, v_restaurant.id, v_restaurant.name, p_payment_method,
    v_subtotal, v_shipping, v_subtotal + v_shipping, trim(p_recipient), trim(p_phone), trim(p_address), nullif(trim(p_note), ''))
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

create or replace function public.fg_cancel_order(p_order_id bigint)
returns public.fg_orders language plpgsql security definer set search_path = public as $$
declare v_order public.fg_orders;
begin
  update public.fg_orders set status = 'cancelled'
  where id = p_order_id and user_id = auth.uid() and status = 'pending' and payment_status = 'unpaid'
  returning * into v_order;
  if v_order.id is null then raise exception 'Chỉ hủy được đơn chưa xác nhận và chưa thanh toán'; end if;
  return v_order;
end $$;

grant execute on function public.fg_place_order(jsonb, text, text, text, text, text) to authenticated;
grant execute on function public.fg_cancel_order(bigint) to authenticated;

-- ============================================================
-- 4. ROW LEVEL SECURITY
-- ============================================================

alter table public.fg_profiles      enable row level security;
alter table public.fg_categories    enable row level security;
alter table public.fg_restaurants   enable row level security;
alter table public.fg_foods         enable row level security;
alter table public.fg_addresses     enable row level security;
alter table public.fg_favorites     enable row level security;
alter table public.fg_orders        enable row level security;
alter table public.fg_order_items   enable row level security;
alter table public.fg_notifications enable row level security;

-- profiles
drop policy if exists "fg profiles read own or admin" on public.fg_profiles;
create policy "fg profiles read own or admin" on public.fg_profiles for select using (id = auth.uid() or public.fg_is_admin());
drop policy if exists "fg profiles insert own" on public.fg_profiles;
create policy "fg profiles insert own" on public.fg_profiles for insert with check (id = auth.uid() and role = 'customer');
drop policy if exists "fg profiles update own or admin" on public.fg_profiles;
create policy "fg profiles update own or admin" on public.fg_profiles for update using (id = auth.uid() or public.fg_is_admin());

-- catalog: ai cũng đọc được, chỉ admin sửa
drop policy if exists "fg categories public read" on public.fg_categories;
create policy "fg categories public read" on public.fg_categories for select using (true);
drop policy if exists "fg categories admin write" on public.fg_categories;
create policy "fg categories admin write" on public.fg_categories for all using (public.fg_is_admin()) with check (public.fg_is_admin());

drop policy if exists "fg restaurants public read" on public.fg_restaurants;
create policy "fg restaurants public read" on public.fg_restaurants for select using (is_active or public.fg_is_admin());
drop policy if exists "fg restaurants admin write" on public.fg_restaurants;
create policy "fg restaurants admin write" on public.fg_restaurants for all using (public.fg_is_admin()) with check (public.fg_is_admin());

drop policy if exists "fg foods public read" on public.fg_foods;
create policy "fg foods public read" on public.fg_foods for select using (true);
drop policy if exists "fg foods admin write" on public.fg_foods;
create policy "fg foods admin write" on public.fg_foods for all using (public.fg_is_admin()) with check (public.fg_is_admin());

-- addresses & favorites: của ai người nấy dùng
drop policy if exists "fg addresses own" on public.fg_addresses;
create policy "fg addresses own" on public.fg_addresses for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "fg favorites own" on public.fg_favorites;
create policy "fg favorites own" on public.fg_favorites for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- orders: tạo qua fg_place_order(), khách chỉ đọc đơn của mình, admin đọc/sửa tất cả
drop policy if exists "fg orders read own or admin" on public.fg_orders;
create policy "fg orders read own or admin" on public.fg_orders for select using (user_id = auth.uid() or public.fg_is_admin());
drop policy if exists "fg orders admin update" on public.fg_orders;
create policy "fg orders admin update" on public.fg_orders for update using (public.fg_is_admin()) with check (public.fg_is_admin());

drop policy if exists "fg order_items read own or admin" on public.fg_order_items;
create policy "fg order_items read own or admin" on public.fg_order_items for select using (
  public.fg_is_admin() or exists (select 1 from public.fg_orders o where o.id = order_id and o.user_id = auth.uid())
);

drop policy if exists "fg notifications own read" on public.fg_notifications;
create policy "fg notifications own read" on public.fg_notifications for select using (user_id = auth.uid());
drop policy if exists "fg notifications own update" on public.fg_notifications;
create policy "fg notifications own update" on public.fg_notifications for update using (user_id = auth.uid());

-- ============================================================
-- 5. REALTIME (cập nhật trạng thái đơn / thông báo tức thì)
-- ============================================================

do $$ begin
  begin alter publication supabase_realtime add table public.fg_orders; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.fg_notifications; exception when duplicate_object then null; end;
end $$;

-- ============================================================
-- 6. STORAGE: bucket ảnh cho admin upload
-- ============================================================

insert into storage.buckets (id, name, public) values ('fg-images', 'fg-images', true)
on conflict (id) do nothing;

drop policy if exists "fg images public read" on storage.objects;
create policy "fg images public read" on storage.objects for select using (bucket_id = 'fg-images');
drop policy if exists "fg images admin insert" on storage.objects;
create policy "fg images admin insert" on storage.objects for insert with check (bucket_id = 'fg-images' and public.fg_is_admin());
drop policy if exists "fg images admin delete" on storage.objects;
create policy "fg images admin delete" on storage.objects for delete using (bucket_id = 'fg-images' and public.fg_is_admin());
