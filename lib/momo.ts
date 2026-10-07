import { supabase } from '@/lib/supabase'

/** Creates a MoMo payment for the order and sends the customer to MoMo's payment page. */
export async function startMomoPayment(orderId: number) {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token
  if (!token) throw new Error('Bạn cần đăng nhập lại để thanh toán')
  const res = await fetch('/api/payment/momo/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ orderId }),
  })
  const json = await res.json().catch(() => ({})) as { payUrl?: string; error?: string }
  if (!res.ok || !json.payUrl) throw new Error(json.error ?? 'Không tạo được giao dịch MoMo')
  // same tab/WebView: MoMo sends the customer back to /orders/<id> when done
  window.location.href = json.payUrl
}

/** Keys MoMo appends to redirectUrl after payment (same set as the IPN body). */
export const MOMO_RETURN_KEYS = ['partnerCode', 'orderId', 'requestId', 'amount', 'orderInfo', 'orderType', 'transId',
  'resultCode', 'message', 'payType', 'responseTime', 'extraData', 'signature'] as const
