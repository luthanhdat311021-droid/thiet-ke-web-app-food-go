-- Đơn COD: khách trả tiền khi nhận hàng → giao xong = đã thanh toán.
-- Chạy SAU 006_delivered_at.sql. An toàn khi chạy lại.

do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  if position('payment_method = ''cod''' in d) = 0 then
    execute replace(d,
      'if new.status = ''delivered'' then new.delivered_at := coalesce(new.delivered_at, now()); end if;',
      'if new.status = ''delivered'' then new.delivered_at := coalesce(new.delivered_at, now()); end if;
    if new.status = ''delivered'' and new.payment_method = ''cod'' then new.payment_status := ''paid''; end if;');
  end if;
end $$;

-- đơn COD đã giao trước đây nhưng còn ghi "chưa thanh toán"
update public.fg_orders set payment_status = 'paid', paid_at = coalesce(paid_at, delivered_at, updated_at)
where status = 'delivered' and payment_method = 'cod' and payment_status <> 'paid';

select (pg_get_functiondef('public.fg_notify_order_change'::regproc) like '%payment_method = ''cod''%') as trigger_ok;
