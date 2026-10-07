-- Khách hàng đánh giá món (1–5 sao + nhận xét). Điểm của món và của quán được TÍNH TỰ ĐỘNG từ đánh giá.
-- Chạy SAU 010_sepay.sql. An toàn khi chạy lại.

-- 1. Bảng đánh giá: mỗi khách đánh giá mỗi món trong một đơn đúng 1 lần (sửa lại được)
create table if not exists public.fg_reviews (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  order_id bigint not null references public.fg_orders(id) on delete cascade,
  food_id bigint not null references public.fg_foods(id) on delete cascade,
  reviewer_name text not null,          -- tên hiển thị rút gọn, vd "Đạt L."
  rating int not null check (rating between 1 and 5),
  comment text check (comment is null or char_length(comment) <= 500),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, order_id, food_id)
);
create index if not exists fg_reviews_food_idx on public.fg_reviews(food_id, created_at desc);

alter table public.fg_reviews enable row level security;
drop policy if exists "fg reviews public read" on public.fg_reviews;
create policy "fg reviews public read" on public.fg_reviews for select using (true);
drop policy if exists "fg reviews admin delete" on public.fg_reviews;
create policy "fg reviews admin delete" on public.fg_reviews for delete using (public.fg_is_admin());
-- không có policy insert/update: khách chỉ gửi qua fg_submit_review() (có kiểm tra điều kiện)

alter table public.fg_foods add column if not exists review_count int not null default 0;

-- 2. Gửi / sửa đánh giá: chỉ cho món có trong đơn ĐÃ GIAO của chính khách
create or replace function public.fg_submit_review(p_order_id bigint, p_food_id bigint, p_rating int, p_comment text default null)
returns public.fg_reviews language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_name text;
  v_review public.fg_reviews;
begin
  if v_user is null then raise exception 'Bạn cần đăng nhập để đánh giá'; end if;
  if p_rating is null or p_rating < 1 or p_rating > 5 then raise exception 'Vui lòng chọn từ 1 đến 5 sao'; end if;
  if char_length(coalesce(p_comment, '')) > 500 then raise exception 'Nhận xét tối đa 500 ký tự'; end if;
  if not exists (select 1 from public.fg_orders where id = p_order_id and user_id = v_user and status = 'delivered') then
    raise exception 'Chỉ đánh giá được món trong đơn đã giao của bạn';
  end if;
  if not exists (select 1 from public.fg_order_items where order_id = p_order_id and food_id = p_food_id) then
    raise exception 'Món này không có trong đơn hàng';
  end if;

  -- "Lữ Thành Đạt" → "Đạt L." (giữ riêng tư, vẫn đủ thật)
  select case
      when coalesce(trim(full_name), '') = '' then 'Khách hàng'
      when position(' ' in trim(full_name)) = 0 then trim(full_name)
      else regexp_replace(trim(full_name), '^.*\s', '') || ' ' || upper(left(trim(full_name), 1)) || '.'
    end into v_name
  from public.fg_profiles where id = v_user;

  insert into public.fg_reviews (user_id, order_id, food_id, reviewer_name, rating, comment)
  values (v_user, p_order_id, p_food_id, coalesce(v_name, 'Khách hàng'), p_rating, nullif(trim(p_comment), ''))
  on conflict (user_id, order_id, food_id)
  do update set rating = excluded.rating, comment = excluded.comment, updated_at = now()
  returning * into v_review;
  return v_review;
end $$;
grant execute on function public.fg_submit_review(bigint, bigint, int, text) to authenticated;

-- 3. Tự tính lại điểm món + điểm quán mỗi khi có đánh giá thêm / sửa / xóa
create or replace function public.fg_refresh_ratings()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_food bigint := coalesce(new.food_id, old.food_id);
begin
  update public.fg_foods f
  set rating = coalesce((select round(avg(r.rating)::numeric, 1) from public.fg_reviews r where r.food_id = v_food), 0),
      review_count = (select count(*) from public.fg_reviews r where r.food_id = v_food)
  where f.id = v_food;

  -- "where true": request từ app chạy với pg_safeupdate, chặn UPDATE không có WHERE
  update public.fg_restaurants
  set rating = coalesce((select round(avg(rating)::numeric, 1) from public.fg_reviews), 0),
      review_count = (select count(*) from public.fg_reviews)
  where true;
  return null;
end $$;

drop trigger if exists fg_reviews_refresh on public.fg_reviews;
create trigger fg_reviews_refresh after insert or update or delete on public.fg_reviews
  for each row execute function public.fg_refresh_ratings();

-- 4. Bỏ điểm nhập tay: tính lại từ đánh giá thật (chưa có đánh giá → 0 = "Chưa có đánh giá")
update public.fg_foods f
set rating = coalesce((select round(avg(r.rating)::numeric, 1) from public.fg_reviews r where r.food_id = f.id), 0),
    review_count = (select count(*) from public.fg_reviews r where r.food_id = f.id);
update public.fg_restaurants
set rating = coalesce((select round(avg(rating)::numeric, 1) from public.fg_reviews), 0),
    review_count = (select count(*) from public.fg_reviews);

-- 5. Thông báo khi giao xong: mời đánh giá
do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  if position('Đánh giá món' in d) = 0 then
    execute replace(d, 'Đơn hàng đã giao thành công. Chúc ngon miệng!',
      'Đơn hàng đã giao thành công. Chúc ngon miệng! Đánh giá món để quán phục vụ bạn tốt hơn nhé.');
  end if;
end $$;

select (select count(*) from public.fg_foods where rating = 0) as foods_reset,
  (select rating from public.fg_restaurants limit 1) as store_rating,
  position('Đánh giá món' in pg_get_functiondef('public.fg_notify_order_change'::regproc)) > 0 as notify_ok;
