-- Doanh thu chỉ tính khi đơn ĐÃ GIAO, theo ngày giao → lưu thời điểm giao xong.
-- Chạy SAU 005_single_store.sql. An toàn khi chạy lại.

alter table public.fg_orders add column if not exists delivered_at timestamptz;

-- đơn đã giao trước đây: lấy lần cập nhật cuối làm thời điểm giao
update public.fg_orders set delivered_at = updated_at where status = 'delivered' and delivered_at is null;

-- trigger cập nhật đơn tự ghi delivered_at khi chuyển sang 'delivered' (sửa hàm hiện có, chỉ chèn 1 dòng)
do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  if position('delivered_at' in d) = 0 then
    execute replace(d,
      'if new.status is distinct from old.status then',
      'if new.status is distinct from old.status then
    if new.status = ''delivered'' then new.delivered_at := coalesce(new.delivered_at, now()); end if;');
  end if;
end $$;

select (pg_get_functiondef('public.fg_notify_order_change'::regproc) like '%delivered_at%') as trigger_ok,
  count(*) filter (where status = 'delivered') as delivered_orders,
  count(*) filter (where status = 'delivered' and delivered_at is null) as missing_delivered_at
from public.fg_orders;
