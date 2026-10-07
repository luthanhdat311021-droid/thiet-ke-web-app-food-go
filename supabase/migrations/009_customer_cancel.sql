-- Khách được hủy đơn khi quán CHƯA bắt đầu chuẩn bị (Chờ xác nhận / Quán đã xác nhận), mọi hình thức
-- thanh toán. Đơn đã thanh toán bị hủy vẫn giữ payment_status = 'paid' → admin thấy "Cần hoàn tiền",
-- hoàn xong bấm "Đã hoàn tiền" (= 'refunded'). Chạy SAU 008. An toàn khi chạy lại.

create or replace function public.fg_cancel_order(p_order_id bigint)
returns public.fg_orders language plpgsql security definer set search_path = public as $$
declare v_order public.fg_orders;
begin
  update public.fg_orders set status = 'cancelled'
  where id = p_order_id and user_id = auth.uid() and status in ('pending', 'confirmed')
  returning * into v_order;
  if v_order.id is null then
    raise exception 'Quán đã bắt đầu chuẩn bị món nên không thể hủy đơn. Vui lòng liên hệ quán nếu cần hỗ trợ.';
  end if;
  return v_order;
end $$;

grant execute on function public.fg_cancel_order(bigint) to authenticated;

-- thông báo hủy: nhắc hoàn tiền nếu khách đã trả trước
do $$
declare d text;
begin
  d := pg_get_functiondef('public.fg_notify_order_change'::regproc);
  if position('sẽ hoàn tiền' in d) = 0 then
    execute replace(d,
      'when ''cancelled''  then ''Đơn hàng đã bị hủy.''',
      'when ''cancelled''  then case when new.payment_status = ''paid'' then ''Đơn hàng đã bị hủy. Quán sẽ hoàn tiền cho bạn sớm nhất.'' else ''Đơn hàng đã bị hủy.'' end');
  end if;
end $$;

select position('status in (''pending'', ''confirmed'')' in pg_get_functiondef('public.fg_cancel_order(bigint)'::regprocedure)) > 0 as cancel_ok,
  position('sẽ hoàn tiền' in pg_get_functiondef('public.fg_notify_order_change'::regproc)) > 0 as notify_ok;
