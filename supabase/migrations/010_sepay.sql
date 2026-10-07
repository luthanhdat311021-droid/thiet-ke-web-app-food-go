-- SePay: tự xác nhận đơn "Chuyển khoản QR" khi tiền vào tài khoản ngân hàng.
-- Webhook gọi fg_sepay_confirm() bằng anon key; khóa API được kiểm tra NGAY TRONG DB.
-- Chạy SAU 009_customer_cancel.sql. An toàn khi chạy lại.

-- 1. Nhật ký giao dịch ngân hàng (id = id giao dịch của SePay) → chống xử lý trùng khi SePay gửi lại
create table if not exists public.fg_bank_transactions (
  id bigint primary key,
  order_code text,
  amount bigint not null,
  content text,
  gateway text,
  result text,
  received_at timestamptz not null default now()
);
alter table public.fg_bank_transactions enable row level security;
drop policy if exists "fg bank tx admin read" on public.fg_bank_transactions;
create policy "fg bank tx admin read" on public.fg_bank_transactions for select using (public.fg_is_admin());

-- 2. Khóa API của webhook (ngẫu nhiên, chỉ đọc được trong SQL Editor; dán vào SePay → Webhooks → API Key)
insert into public.fg_secrets (key, value)
values ('sepay_api_key', encode(extensions.gen_random_bytes(24), 'hex'))
on conflict (key) do nothing;

-- 3. Xử lý webhook
create or replace function public.fg_sepay_confirm(p jsonb, p_key text)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_key text;
  v_code text;
  v_amount bigint := coalesce((p->>'transferAmount')::bigint, 0);
  v_order public.fg_orders;
  v_inserted int;
begin
  select value into v_key from public.fg_secrets where key = 'sepay_api_key';
  if v_key is null or p_key is distinct from v_key then return 'unauthorized'; end if;
  if p->>'transferType' is distinct from 'in' or p->>'id' is null then return 'ignored'; end if;

  -- ngân hàng có thể chèn thêm chữ vào nội dung CK → tìm mã đơn FGxxxxxxxx
  v_code := substring(upper(coalesce(p->>'code', '') || ' ' || coalesce(p->>'content', '')) from 'FG[0-9A-F]{8}');

  insert into public.fg_bank_transactions (id, order_code, amount, content, gateway)
  values ((p->>'id')::bigint, v_code, v_amount, p->>'content', p->>'gateway')
  on conflict (id) do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then return 'duplicate'; end if;

  if v_code is null then
    update public.fg_bank_transactions set result = 'no_code' where id = (p->>'id')::bigint;
    return 'no_code';
  end if;

  select * into v_order from public.fg_orders where code = v_code for update;
  if v_order.id is null then
    update public.fg_bank_transactions set result = 'not_found' where id = (p->>'id')::bigint;
    return 'not_found';
  end if;
  if v_order.payment_status <> 'unpaid' then
    update public.fg_bank_transactions set result = 'already_paid' where id = (p->>'id')::bigint;
    return 'already_paid';
  end if;
  if v_amount < v_order.total then
    update public.fg_bank_transactions set result = 'amount_mismatch' where id = (p->>'id')::bigint;
    return 'amount_mismatch';
  end if;

  -- tiền đã vào → đã thanh toán + tự xác nhận đơn (trừ khi khách đã hủy: admin sẽ thấy "Cần hoàn tiền")
  update public.fg_orders
  set payment_status = 'paid',
      status = case when status = 'pending' then 'confirmed' else status end
  where id = v_order.id;
  update public.fg_bank_transactions set result = 'ok' where id = (p->>'id')::bigint;
  return 'ok';
end $$;

grant execute on function public.fg_sepay_confirm(jsonb, text) to anon, authenticated;

select public.fg_sepay_confirm('{"id": 1, "transferType": "in"}'::jsonb, 'wrong-key') as wrong_key_result;
