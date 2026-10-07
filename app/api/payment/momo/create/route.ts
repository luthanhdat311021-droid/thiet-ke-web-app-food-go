import { createHmac } from 'node:crypto'
import { createClient } from '@supabase/supabase-js'
import { NextResponse } from 'next/server'

// MoMo Payment Gateway v2 – https://developers.momo.vn/v3/docs/payment/api/wallet/onetime
// Defaults are MoMo's public SANDBOX credentials (no real money); set MOMO_* env vars for production.
const ENDPOINT = process.env.MOMO_ENDPOINT ?? 'https://test-payment.momo.vn/v2/gateway/api/create'
const PARTNER_CODE = process.env.MOMO_PARTNER_CODE ?? 'MOMO'
const ACCESS_KEY = process.env.MOMO_ACCESS_KEY ?? 'F8BBA842ECF85'
const SECRET_KEY = process.env.MOMO_SECRET_KEY ?? 'K951B6PE1waDMi640xX08PD3vg6EkVlz'

const clean = (v?: string) => v?.replace(/^﻿/, '').trim()

/** Starts a MoMo payment for one of the signed-in customer's unpaid orders; returns MoMo's pay URL. */
export async function POST(req: Request) {
  const token = req.headers.get('authorization')?.replace(/^Bearer /, '')
  const { orderId } = await req.json().catch(() => ({})) as { orderId?: number }
  if (!token || !orderId) return NextResponse.json({ error: 'Thiếu thông tin đơn hàng' }, { status: 400 })

  // query as the customer, so RLS guarantees they can only pay their own order
  const supabase = createClient(clean(process.env.NEXT_PUBLIC_SUPABASE_URL)!, clean(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY)!, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false },
  })
  const { data: order } = await supabase.from('fg_orders')
    .select('id, code, total, payment_method, payment_status, status').eq('id', orderId).maybeSingle()
  if (!order) return NextResponse.json({ error: 'Không tìm thấy đơn hàng' }, { status: 404 })
  if (order.payment_method !== 'momo') return NextResponse.json({ error: 'Đơn này không thanh toán bằng MoMo' }, { status: 400 })
  if (order.payment_status === 'paid') return NextResponse.json({ error: 'Đơn đã được thanh toán' }, { status: 409 })
  if (order.status === 'cancelled') return NextResponse.json({ error: 'Đơn đã bị hủy' }, { status: 409 })

  const origin = new URL(req.url).origin
  const body: Record<string, string | number | boolean> = {
    partnerCode: PARTNER_CODE,
    requestType: 'payWithMethod', // MoMo page offers wallet QR, MoMo app and (in sandbox) test cards
    // a fresh MoMo orderId per attempt; fg_momo_confirm() maps it back via the part before "_"
    orderId: `${order.code}_${Date.now()}`,
    requestId: `${order.code}-${crypto.randomUUID()}`,
    amount: order.total,
    orderInfo: `Thanh toan don ${order.code} FoodGo`,
    redirectUrl: `${origin}/orders/${order.id}`,
    ipnUrl: `${origin}/api/payment/momo/ipn`,
    extraData: '',
    lang: 'vi',
    autoCapture: true,
  }
  const raw = ['accessKey=' + ACCESS_KEY, 'amount=' + body.amount, 'extraData=' + body.extraData, 'ipnUrl=' + body.ipnUrl,
    'orderId=' + body.orderId, 'orderInfo=' + body.orderInfo, 'partnerCode=' + body.partnerCode, 'redirectUrl=' + body.redirectUrl,
    'requestId=' + body.requestId, 'requestType=' + body.requestType].join('&')
  body.signature = createHmac('sha256', SECRET_KEY).update(raw).digest('hex')

  const res = await fetch(ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  }).catch(() => null)
  const data = await res?.json().catch(() => null) as { resultCode?: number; message?: string; payUrl?: string; deeplink?: string } | null
  if (!data || data.resultCode !== 0 || !data.payUrl) {
    return NextResponse.json({ error: data?.message ? `MoMo: ${data.message}` : 'Không kết nối được MoMo, vui lòng thử lại' }, { status: 502 })
  }
  return NextResponse.json({ payUrl: data.payUrl, deeplink: data.deeplink ?? null })
}
