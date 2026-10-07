-- Đơn thanh toán online (QR chuyển khoản / MoMo): quán chỉ xác nhận sau khi đã thấy tiền vào
-- → chuyển sang 'confirmed' = đã thanh toán. Chạy SAU 007_cod_paid_on_delivery.sql. An toàn khi chạy lại.

do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  if position('payment_method in (''qr'', ''momo'')' in d) = 0 then
    execute replace(d,
      'if new.status = ''delivered'' and new.payment_method = ''cod'' then new.payment_status := ''paid''; end if;',
      'if new.status = ''delivered'' and new.payment_method = ''cod'' then new.payment_status := ''paid''; end if;
    if new.status = ''confirmed'' and new.payment_method in (''qr'', ''momo'') then new.payment_status := ''paid''; end if;');
  end if;
end $$;

-- đơn online đã qua bước xác nhận trước đây nhưng còn ghi "chưa thanh toán"
update public.fg_orders set payment_status = 'paid', paid_at = coalesce(paid_at, updated_at)
where payment_method in ('qr', 'momo') and status in ('confirmed', 'preparing', 'picking_up', 'delivering', 'delivered')
  and payment_status <> 'paid';

select (pg_get_functiondef('public.fg_notify_order_change'::regproc) like '%payment_method in (''qr'', ''momo'')%') as trigger_ok;
