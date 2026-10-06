import { createClient } from '@supabase/supabase-js'
import { NextResponse } from 'next/server'

/**
 * Webhook nhận biến động số dư từ SePay (https://sepay.vn) để tự động xác nhận
 * đơn chuyển khoản QR. Cấu hình trong SePay: URL = https://<domain>/api/payment/webhook,
 * kiểu xác thực "API Key" = giá trị SEPAY_WEBHOOK_KEY.
 *
 * Payload SePay (rút gọn): { id, gateway, transactionDate, accountNumber, content,
 *   transferType: 'in' | 'out', transferAmount, referenceCode, ... }
 */
export async function POST(req: Request) {
  const expected = process.env.SEPAY_WEBHOOK_KEY
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  if (!expected || !serviceKey || !url) {
    return NextResponse.json({ success: false, message: 'Webhook chưa được cấu hình' }, { status: 503 })
  }
  if (req.headers.get('authorization') !== `Apikey ${expected}`) {
    return NextResponse.json({ success: false, message: 'Unauthorized' }, { status: 401 })
  }

  const body = await req.json().catch(() => null) as { transferType?: string; transferAmount?: number; content?: string; code?: string } | null
  if (!body || body.transferType !== 'in') return NextResponse.json({ success: true, message: 'ignored' })

  // Ngân hàng có thể chèn thêm ký tự vào nội dung CK, nên tìm mã đơn FGxxxxxxxx trong chuỗi
  const text = `${body.code ?? ''} ${body.content ?? ''}`.toUpperCase()
  const code = text.match(/FG[0-9A-F]{8}/)?.[0]
  if (!code) return NextResponse.json({ success: true, message: 'no order code' })

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } })
  const { data: order } = await admin.from('fg_orders').select('id, total, payment_status, status').eq('code', code).maybeSingle()
  if (!order) return NextResponse.json({ success: true, message: 'order not found' })
  if (order.payment_status === 'paid') return NextResponse.json({ success: true, message: 'already paid' })
  if (Number(body.transferAmount) < order.total) {
    return NextResponse.json({ success: true, message: `amount ${body.transferAmount} < ${order.total}` })
  }

  const { error } = await admin.from('fg_orders').update({
    payment_status: 'paid',
    // tự xác nhận đơn khi đã nhận tiền
    ...(order.status === 'pending' ? { status: 'confirmed' } : {}),
  }).eq('id', order.id)
  if (error) return NextResponse.json({ success: false, message: error.message }, { status: 500 })
  return NextResponse.json({ success: true, message: `paid ${code}` })
}
