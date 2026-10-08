-- Gợi ý món ăn cá nhân hóa (học máy trên dữ liệu hành vi, chạy ngay trong Postgres):
--   • Ghi lịch sử xem món / tìm kiếm / thêm vào giỏ (fg_user_events). Đơn hàng, yêu thích, đánh giá
--     lấy từ các bảng có sẵn.
--   • Mô hình item-based collaborative filtering: độ tương đồng cosine giữa các món trên ma trận
--     người dùng × món (phản hồi ngầm, có trọng số và giảm dần theo thời gian), cộng thêm
--     content-based (cùng danh mục / cùng nhà hàng / tầm giá) để món mới chưa ai mua vẫn được gợi ý.
--     Kết quả lưu ở fg_food_similarity, tính lại định kỳ bằng pg_cron (fg_refresh_recommendations).
--   • fg_recommend_foods(): điểm của từng món cho MỘT người = CF + khớp từ khóa đã tìm
--     + danh mục hay chọn + món hay đặt lại + độ phổ biến, kèm lý do gợi ý. Chưa có lịch sử → món bán chạy.
--   • fg_similar_foods(): "Có thể bạn cũng thích" trong trang chi tiết món.
-- Chạy SAU 017_phone_check.sql. An toàn khi chạy lại.

begin;

-- ============================================================ 1. so khớp tiếng Việt không dấu
create extension if not exists unaccent with schema extensions;

-- "Phở Bò (Đặc Biệt)" → "pho bo dac biet": không dấu, chỉ còn chữ/số cách nhau 1 dấu cách
-- (dùng đúng schema nơi unaccent được cài)
do $$
declare s text;
begin
  select n.nspname into s from pg_extension e join pg_namespace n on n.oid = e.extnamespace where e.extname = 'unaccent';
  execute format($f$
    create or replace function public.fg_norm(p text)
    returns text language sql immutable parallel safe as $b$
      select trim(lower(regexp_replace(%I.unaccent(%L::regdictionary, coalesce(p, '')), '[^[:alnum:]]+', ' ', 'g')))
    $b$ $f$, s, s || '.unaccent');
end $$;

-- như fg_norm nhưng giữ dấu: "Phở Bò (Đặc Biệt)" → "phở bò đặc biệt"
create or replace function public.fg_words(p text)
returns text language sql immutable parallel safe as $$
  select trim(lower(regexp_replace(coalesce(p, ''), '[^[:alnum:]]+', ' ', 'g')))
$$;

-- ============================================================ 2. lịch sử hành vi
create table if not exists public.fg_user_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('view', 'search', 'cart')),
  food_id bigint references public.fg_foods(id) on delete cascade,
  query text check (char_length(query) <= 80),
  created_at timestamptz not null default now(),
  check (case when kind = 'search' then query is not null and food_id is null else food_id is not null and query is null end)
);
create index if not exists fg_user_events_user_idx on public.fg_user_events(user_id, created_at desc);
create index if not exists fg_user_events_food_idx on public.fg_user_events(food_id) where food_id is not null;

-- ghi qua fg_track(); người dùng xem / xóa được lịch sử của chính mình
alter table public.fg_user_events enable row level security;
drop policy if exists "fg events own read" on public.fg_user_events;
create policy "fg events own read" on public.fg_user_events for select using (user_id = auth.uid());
drop policy if exists "fg events own delete" on public.fg_user_events;
create policy "fg events own delete" on public.fg_user_events for delete using (user_id = auth.uid());

create or replace function public.fg_track(p_kind text, p_food_id bigint default null, p_query text default null)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_query text := left(nullif(trim(regexp_replace(coalesce(p_query, ''), '\s+', ' ', 'g')), ''), 80);
begin
  if v_user is null then return; end if;
  if p_kind not in ('view', 'search', 'cart') then raise exception 'Sự kiện không hợp lệ'; end if;
  if p_kind = 'search' then
    if v_query is null then return; end if;
    p_food_id := null;
  else
    if p_food_id is null or not exists (select 1 from public.fg_foods where id = p_food_id) then return; end if;
    v_query := null;
  end if;
  perform public.fg_throttle('track', 120, 60);
  -- cùng một món / cùng từ khóa trong 10 phút chỉ tính một lần
  if exists (
    select 1 from public.fg_user_events
    where user_id = v_user and kind = p_kind and created_at > now() - interval '10 minutes'
      and food_id is not distinct from p_food_id and lower(query) is not distinct from lower(v_query)
  ) then return; end if;
  insert into public.fg_user_events (user_id, kind, food_id, query) values (v_user, p_kind, p_food_id, v_query);
end $$;

-- ============================================================ 3. ma trận người dùng × món (phản hồi ngầm)
-- Trọng số: xem 1 · thêm giỏ 3 · yêu thích 4 · đặt mua 5 (+ln số lượng) · đánh giá (sao - 3) × 2
-- (5★ = +4, 1★ = -4). Mỗi tương tác giảm dần theo thời gian: e^(-số ngày / 30). Yêu thích không giảm.
-- p_user null = mọi người dùng (để huấn luyện). Chỉ dùng bên trong các hàm security definer.
create or replace function public.fg_rec_interactions(p_user uuid default null)
returns table (user_id uuid, food_id bigint, w double precision)
language sql stable security definer set search_path = public as $$
  select e.user_id, e.food_id, sum(e.w * exp(-extract(epoch from now() - e.at) / 86400.0 / 30))
  from (
    select ue.user_id, ue.food_id, (case ue.kind when 'view' then 1 else 3 end)::float8 as w, ue.created_at as at
    from public.fg_user_events ue
    where ue.kind in ('view', 'cart') and (p_user is null or ue.user_id = p_user)
    union all
    select fv.user_id, fv.food_id, 4::float8, now()
    from public.fg_favorites fv
    where p_user is null or fv.user_id = p_user
    union all
    select o.user_id, oi.food_id, 5 + ln(oi.qty::float8), o.created_at
    from public.fg_orders o join public.fg_order_items oi on oi.order_id = o.id
    where o.status <> 'cancelled' and oi.food_id is not null and (p_user is null or o.user_id = p_user)
    union all
    select rv.user_id, rv.food_id, ((rv.rating - 3) * 2)::float8, rv.created_at
    from public.fg_reviews rv
    where p_user is null or rv.user_id = p_user
  ) e
  group by e.user_id, e.food_id
$$;

-- ============================================================ 4. mô hình: độ tương đồng món – món
create table if not exists public.fg_food_similarity (
  food_id bigint not null references public.fg_foods(id) on delete cascade,
  similar_id bigint not null references public.fg_foods(id) on delete cascade,
  score real not null,
  co_users int not null default 0,   -- số người cùng tương tác với cả hai món
  primary key (food_id, similar_id)
);
alter table public.fg_food_similarity enable row level security;   -- không có policy: chỉ đọc qua RPC

create or replace function public.fg_refresh_recommendations()
returns int language plpgsql security definer set search_path = public as $$
declare v_count int;
begin
  -- pg_cron / SQL Editor (không có auth.uid()) hoặc admin
  if auth.uid() is not null and not public.fg_is_admin() then raise exception 'Chỉ admin được cập nhật mô hình gợi ý'; end if;

  delete from public.fg_user_events where created_at < now() - interval '180 days';
  delete from public.fg_food_similarity where true;

  insert into public.fg_food_similarity (food_id, similar_id, score, co_users)
  with r as materialized (
    select i.user_id, i.food_id, i.w
    from public.fg_rec_interactions(null) i join public.fg_foods f on f.id = i.food_id
    where i.w > 0
  ),
  norm as (select r.food_id, sqrt(sum(r.w * r.w)) as n from r group by r.food_id),
  co as (
    select a.food_id, b.food_id as similar_id, sum(a.w * b.w) as dot, count(*) as users
    from r a join r b on b.user_id = a.user_id and b.food_id <> a.food_id
    group by a.food_id, b.food_id
  ),
  cf as (
    -- cosine; cặp món ít người chung thì tin ít hơn (shrinkage users / (users + 3))
    select co.food_id, co.similar_id, co.users, co.dot / (na.n * nb.n) * co.users / (co.users + 3.0) as s
    from co join norm na on na.food_id = co.food_id join norm nb on nb.food_id = co.similar_id
  ),
  content as (
    select a.id as food_id, b.id as similar_id,
      0.25 * (a.category_id is not null and a.category_id is not distinct from b.category_id)::int
      + 0.10 * (a.restaurant_id = b.restaurant_id)::int
      + 0.10 * greatest(0, 1 - abs(a.price - b.price)::float8 / greatest(a.price, b.price, 1)) as s
    from public.fg_foods a
    join public.fg_foods b on b.id <> a.id and (b.category_id = a.category_id or b.restaurant_id = a.restaurant_id)
  ),
  merged as (
    select coalesce(cf.food_id, content.food_id) as food_id, coalesce(cf.similar_id, content.similar_id) as similar_id,
      0.75 * coalesce(cf.s, 0) + coalesce(content.s, 0) as s, coalesce(cf.users, 0) as users
    from cf full join content on content.food_id = cf.food_id and content.similar_id = cf.similar_id
  )
  select m.food_id, m.similar_id, m.s, m.users
  from (select merged.*, row_number() over (partition by merged.food_id order by merged.s desc) as k from merged) m
  where m.k <= 30;

  get diagnostics v_count = row_count;
  return v_count;
end $$;

-- ============================================================ 5. gợi ý cho người dùng
-- Khách chưa đăng nhập: gửi các món vừa xem / từ khóa vừa tìm trên máy (lưu ở trình duyệt).
create or replace function public.fg_recommend_foods(
  p_limit int default 12,
  p_recent_food_ids bigint[] default '{}',
  p_recent_queries text[] default '{}'
) returns table (food_id bigint, score real, reason text)
language sql stable security definer set search_path = public as $$
  with
  hist as (
    select i.food_id, i.w from public.fg_rec_interactions(auth.uid()) i where auth.uid() is not null
    union all
    select x.id, 1::float8 from unnest(p_recent_food_ids[1:20]) as x(id)
  ),
  h as (select hist.food_id, sum(hist.w) as w from hist group by hist.food_id),
  mine as (   -- món đã đặt: số đơn
    select oi.food_id, count(distinct o.id) as n
    from public.fg_orders o join public.fg_order_items oi on oi.order_id = o.id
    where o.user_id = auth.uid() and o.status <> 'cancelled' and oi.food_id is not null
    group by oi.food_id
  ),
  q as (      -- từ khóa gần đây, càng mới càng nặng
    select s.raw, public.fg_norm(s.raw) as norm, public.fg_words(s.raw) as exact, s.w from (
      select e.query as raw, exp(-extract(epoch from now() - e.created_at) / 86400.0 / 14) as w
      from public.fg_user_events e
      where e.user_id = auth.uid() and e.kind = 'search'
      order by e.created_at desc limit 10
    ) s
    union all
    select left(x.raw, 80), public.fg_norm(left(x.raw, 80)), public.fg_words(left(x.raw, 80)), 1.0::float8 / x.n
    from unnest(p_recent_queries[1:10]) with ordinality as x(raw, n)
  ),
  live as (   -- món đang bán của nhà hàng đang hoạt động
    select f.id, f.restaurant_id, f.category_id, c.name as category, f.rating, f.review_count,
      public.fg_norm(f.name || ' ' || coalesce(f.description, '') || ' ' || coalesce(c.name, '')) as doc,
      public.fg_words(f.name || ' ' || coalesce(f.description, '') || ' ' || coalesce(c.name, '')) as doc_exact
    from public.fg_foods f
    join public.fg_restaurants rs on rs.id = f.restaurant_id
    left join public.fg_categories c on c.id = f.category_id
    where f.is_available and public.fg_restaurant_live(rs)
  ),
  cf as (     -- collaborative filtering: Σ r(u,i) · sim(i,j)
    select s.similar_id as id, sum(h.w * s.score) as v,
      (array_agg(h.food_id order by h.w * s.score desc))[1] as because
    from h join public.fg_food_similarity s on s.food_id = h.food_id
    group by s.similar_id
  ),
  cat as (    -- danh mục hay chọn
    select f.category_id, sum(h.w) as v
    from h join public.fg_foods f on f.id = h.food_id
    where h.w > 0 and f.category_id is not null
    group by f.category_id
  ),
  srch as (   -- món chứa đủ mọi từ của một câu đã tìm, so nguyên từ ("com" không khớp "combo").
              -- Gõ không dấu thì so không dấu ("com" khớp "cơm"); gõ có dấu thì so có dấu ("cơm" không khớp "cốm")
    select l.id, sum(q.w) as v, (array_agg(q.raw order by q.w desc))[1] as because
    from live l join q on q.norm <> '' and not exists (
      select 1 from unnest(string_to_array(q.exact, ' ')) t
      where position(' ' || t || ' ' in ' ' || case when q.exact = q.norm then l.doc else l.doc_exact end || ' ') = 0)
    group by l.id
  ),
  sales as (  -- bán chạy 30 ngày qua
    select oi.food_id, sum(oi.qty) as n
    from public.fg_order_items oi join public.fg_orders o on o.id = oi.order_id
    where o.created_at > now() - interval '30 days' and o.status <> 'cancelled' and oi.food_id is not null
    group by oi.food_id
  ),
  scored as (
    select l.id, l.restaurant_id, l.category, cf.because as because_food, srch.because as because_query, mine.n as times,
      coalesce(cf.v / nullif((select max(x.v) from cf x), 0), 0) * 1.0                    as c_cf,
      coalesce(srch.v / nullif((select max(x.v) from srch x), 0), 0) * 0.8                as c_search,
      coalesce(cat.v / nullif((select max(x.v) from cat x), 0), 0) * 0.4                  as c_cat,
      coalesce(ln(1 + mine.n) / nullif(ln(1 + (select max(x.n) from mine x)), 0), 0) * 0.5 as c_repeat,
      (coalesce(ln(1 + sales.n) / nullif(ln(1 + (select max(x.n) from sales x)), 0), 0) * 0.7
        + l.rating / 5.0 * l.review_count / (l.review_count + 5.0) * 0.3) * 0.3            as c_pop
    from live l
    left join h on h.food_id = l.id
    left join cf on cf.id = l.id
    left join cat on cat.category_id = l.category_id
    left join srch on srch.id = l.id
    left join mine on mine.food_id = l.id
    left join sales on sales.food_id = l.id
    where coalesce(h.w, 0) >= 0   -- bỏ món người dùng đã chê
  ),
  ranked as (
    select s.*, t.total,
      row_number() over (partition by s.restaurant_id order by t.total desc) as nth
    from scored s cross join lateral (select s.c_cf + s.c_search + s.c_cat + s.c_repeat + s.c_pop as total) t
  )
  -- đa dạng: món thứ 2, 3… của cùng một nhà hàng bị trừ điểm dần
  select r.id, (r.total - 0.1 * (r.nth - 1))::real as score,
    case greatest(r.c_cf, r.c_search, r.c_cat, r.c_repeat, r.c_pop)
      when 0 then 'Món mới'
      when r.c_search then 'Hợp với tìm kiếm "' || r.because_query || '"'
      when r.c_repeat then 'Bạn đã đặt ' || r.times || ' lần'
      when r.c_cf then 'Vì bạn quan tâm ' || (select f.name from public.fg_foods f where f.id = r.because_food)
      when r.c_cat then 'Bạn hay chọn ' || r.category
      else 'Đang được yêu thích'
    end as reason
  from ranked r
  order by score desc
  limit least(greatest(coalesce(p_limit, 12), 1), 50)
$$;

-- ============================================================ 6. món tương tự (trang chi tiết món)
create or replace function public.fg_similar_foods(p_food_id bigint, p_limit int default 8)
returns table (food_id bigint, score real, reason text)
language sql stable security definer set search_path = public as $$
  select s.similar_id, s.score, case when s.co_users >= 2 then 'Khách chọn món này cũng thích' else 'Món tương tự' end
  from public.fg_food_similarity s
  join public.fg_foods f on f.id = s.similar_id
  join public.fg_restaurants rs on rs.id = f.restaurant_id
  where s.food_id = p_food_id and f.is_available and public.fg_restaurant_live(rs)
  order by s.score desc
  limit least(greatest(coalesce(p_limit, 8), 1), 20)
$$;

-- ============================================================ 7. quyền
revoke execute on function public.fg_rec_interactions(uuid) from public, anon, authenticated;
revoke execute on function public.fg_refresh_recommendations() from public, anon;
grant execute on function public.fg_refresh_recommendations() to authenticated;
revoke execute on function public.fg_track(text, bigint, text) from public, anon;
grant execute on function public.fg_track(text, bigint, text) to authenticated;
grant execute on function public.fg_recommend_foods(int, bigint[], text[]) to anon, authenticated;
grant execute on function public.fg_similar_foods(bigint, int) to anon, authenticated;

commit;

-- ============================================================ 8. huấn luyện lại mỗi 30 phút (pg_cron)
do $$
begin
  create extension if not exists pg_cron;
  perform cron.schedule('fg-refresh-recommendations', '*/30 * * * *', 'select public.fg_refresh_recommendations()');
exception when others then
  raise notice 'Chưa bật được pg_cron (%). Bật Database → Extensions → pg_cron rồi chạy lại khối này, hoặc chạy tay: select public.fg_refresh_recommendations();', sqlerrm;
end $$;

-- huấn luyện lần đầu + kiểm tra
select public.fg_refresh_recommendations() as similarity_rows,
  (select count(*) from pg_proc where proname in ('fg_track', 'fg_recommend_foods', 'fg_similar_foods', 'fg_refresh_recommendations')) - 4 as functions_missing;
