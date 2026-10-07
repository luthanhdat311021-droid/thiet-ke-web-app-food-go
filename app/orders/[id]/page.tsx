'use client'

import Link from 'next/link'
import { Suspense, useEffect, useState } from 'react'
import { useParams, useRouter, useSearchParams } from 'next/navigation'
import { Bike, Check, CheckCircle2, Copy, Loader2, MapPin, Package, Phone, RotateCcw, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { RequireAuth } from '@/components/require-auth'
import { EmptyState, Panel, Spinner } from '@/components/cards'
import { OrderTrackingMap } from '@/components/order-tracking-map'
import { OrderReviewPanel } from '@/components/reviews'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money, ORDER_STEPS, PAYMENT_LABEL, STATUS_LABEL, STATUS_STYLE, vietQrUrl } from '@/lib/format'
import { MOMO_RETURN_KEYS, startMomoPayment } from '@/lib/momo'
import { MomoIcon } from '@/components/momo-icon'
import type { CartItem, Food, Order } from '@/lib/types'

export default function OrderDetailPage() {
  return <RequireAuth><Suspense fallback={<Spinner />}><OrderDetail /></Suspense></RequireAuth>
}

function OrderDetail() {
  const { id } = useParams<{ id: string }>()
  const params = useSearchParams()
  const isNew = params.get('new') === '1'
  const router = useRouter()
  const { replaceCart, setCartOpen, toast } = useApp()
  const [order, setOrder] = useState<Order | null | undefined>(undefined)
  const [busy, setBusy] = useState(false)
  const [momoBusy, setMomoBusy] = useState(false)

  // back from MoMo: /orders/<id>?partnerCode=...&resultCode=...&signature=...
  useEffect(() => {
    if (!params.get('signature') || params.get('resultCode') === null) return
    const payload = Object.fromEntries(MOMO_RETURN_KEYS.map(k => [k, params.get(k) ?? '']))
    supabase.rpc('fg_momo_confirm', { p: payload }).then(({ data, error }) => {
      if (error) toast(errorMessage(error), 'error')
      else if (data === 'ok' || data === 'already_paid') toast('Thanh toán MoMo thành công')
      else if (data === 'failed') toast(`Thanh toán MoMo chưa thành công${payload.message ? `: ${payload.message}` : ''}`, 'error')
      else toast('Không xác minh được giao dịch MoMo', 'error')
      router.replace(`/orders/${id}`)
    })
  }, [params, id, router, toast])

  useEffect(() => {
    const oid = Number(id)
    const load = () => supabase.from('fg_orders').select('*, order_items:fg_order_items(*), restaurant:fg_restaurants(lat, lng, address)').eq('id', oid).maybeSingle()
      .then(({ data }) => setOrder(data as Order | null))
    load()
    const channel = supabase.channel(`order-${oid}`)
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'fg_orders', filter: `id=eq.${oid}` }, load)
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [id])

  // from "Đánh giá" in the orders list: the review panel only exists once the order has loaded
  const loaded = !!order
  useEffect(() => {
    if (loaded && window.location.hash === '#danh-gia') document.getElementById('danh-gia')?.scrollIntoView({ behavior: 'smooth' })
  }, [loaded])

  if (order === undefined) return <Spinner />
  if (order === null) return <main className="mx-auto max-w-2xl px-5 py-16"><EmptyState icon={<Package />} title="Không tìm thấy đơn hàng"><Link href="/orders" className="font-bold text-[#ff5b35]">Về danh sách đơn</Link></EmptyState></main>

  const cancelled = order.status === 'cancelled'
  const stepIndex = ORDER_STEPS.findIndex(s => s.status === order.status)
  const awaitingQr = order.payment_method === 'qr' && order.payment_status === 'unpaid' && !cancelled
  const qr = awaitingQr ? vietQrUrl(order.total, order.code) : null
  const awaitingMomo = order.payment_method === 'momo' && order.payment_status === 'unpaid' && !cancelled

  const payWithMomo = async () => {
    setMomoBusy(true)
    try { await startMomoPayment(order.id) } catch (e) { toast(errorMessage(e), 'error'); setMomoBusy(false) }
  }

  const cancel = async () => {
    const msg = order.payment_status === 'paid'
      ? 'Bạn chắc chắn muốn hủy đơn? Đơn đã thanh toán, quán sẽ hoàn tiền cho bạn.'
      : 'Bạn chắc chắn muốn hủy đơn hàng này?'
    if (!window.confirm(msg)) return
    setBusy(true)
    const { error } = await supabase.rpc('fg_cancel_order', { p_order_id: order.id })
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    setOrder({ ...order, status: 'cancelled' })
  }

  const reorder = async () => {
    const ids = (order.order_items ?? []).map(i => i.food_id).filter((x): x is number => x !== null)
    const { data } = await supabase.from('fg_foods').select('*').in('id', ids)
    const foods = (data ?? []) as Food[]
    const items: CartItem[] = []
    for (const item of order.order_items ?? []) {
      const food = foods.find(f => f.id === item.food_id)
      if (food?.is_available) {
        items.push({ food_id: food.id, name: food.name, price: food.price, image: food.image, restaurant_id: food.restaurant_id, restaurant_name: order.restaurant_name, qty: item.qty })
      }
    }
    if (!items.length) return toast('Các món trong đơn hiện không còn bán', 'error')
    if (replaceCart(items)) setCartOpen(true)
  }

  return (
    <main className="mx-auto max-w-[1100px] px-5 pb-24 pt-8 lg:px-10">
      <Link href="/orders" className="-my-3 inline-block py-3 text-sm font-bold text-[#ff5b35]">← Đơn hàng của tôi</Link>

      {isNew && (
        <div className="mt-5 flex items-center gap-3 rounded-2xl bg-[#e4f8eb] p-4 text-[#2f7d4f]">
          <CheckCircle2 className="shrink-0" />
          <p className="text-sm"><b>Đặt hàng thành công!</b> {awaitingQr ? 'Quét mã QR bên dưới để thanh toán.' : 'Quán sẽ xác nhận đơn trong giây lát.'}</p>
        </div>
      )}

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-extrabold">Đơn #{order.code}</h1>
        <span className={`rounded-full px-3 py-1 text-sm font-bold ${STATUS_STYLE[order.status]}`}>{STATUS_LABEL[order.status]}</span>
      </div>
      <p className="mt-1 text-sm text-[#9c918c]">{formatDateTime(order.created_at)}</p>

      <div className="mt-8 grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[minmax(0,1fr)_420px]">
        <div className="flex flex-col gap-6">
          {awaitingMomo && (
            <Panel title="Thanh toán bằng ví MoMo">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                <MomoIcon className="h-12 w-16 text-sm" />
                <div className="flex-1 text-sm">
                  <p><b>{money(order.total)}</b> — đơn chưa được thanh toán.</p>
                  <p className="mt-1 text-[#746b67]">Bấm nút để mở trang thanh toán MoMo. Trang này sẽ tự cập nhật khi thanh toán xong.</p>
                </div>
                <Button onClick={payWithMomo} disabled={momoBusy} className="h-12 rounded-xl bg-[#a50064] px-5 text-white hover:bg-[#8a0054]">
                  {momoBusy && <Loader2 className="animate-spin" />}Thanh toán MoMo
                </Button>
              </div>
            </Panel>
          )}
          {awaitingQr && (
            <Panel title="Thanh toán chuyển khoản">
              <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-start">
                {qr ? <img src={qr} alt={`Mã QR thanh toán ${money(order.total)}`} className="w-60 rounded-xl border border-[#f1e7e2]" />
                  : <p className="rounded-xl bg-[#fff7df] p-4 text-sm text-[#8a6100]">Chưa cấu hình tài khoản nhận tiền (NEXT_PUBLIC_VIETQR_* trong .env.local).</p>}
                <div className="w-full text-sm">
                  <p className="text-[#746b67]">Mở app ngân hàng, quét mã QR. Số tiền và nội dung đã được điền sẵn.</p>
                  <dl className="mt-4 flex flex-col gap-3">
                    <CopyRow label="Số tiền" value={String(order.total)} display={money(order.total)} />
                    <CopyRow label="Nội dung" value={order.code} />
                    {process.env.NEXT_PUBLIC_VIETQR_ACCOUNT_NO && <CopyRow label="Số tài khoản" value={process.env.NEXT_PUBLIC_VIETQR_ACCOUNT_NO} />}
                  </dl>
                  <p className="mt-4 flex items-center gap-2 font-semibold text-[#bd8300]"><Loader2 className="size-4 animate-spin" />Đang chờ thanh toán...</p>
                  <p className="mt-1 text-xs text-[#9c918c]">Trang sẽ tự cập nhật khi nhận được tiền.</p>
                </div>
              </div>
            </Panel>
          )}

          <OrderReviewPanel order={order} />

          <OrderTrackingMap order={order} />

          <Panel title="Trạng thái đơn hàng">
            {cancelled ? (
              <div className="flex items-center gap-3 rounded-2xl bg-[#f4f0ee] p-5 text-[#746b67]"><X /><b>Đơn hàng đã bị hủy</b></div>
            ) : (
              <div className="rounded-2xl bg-[#fff5f1] p-5">
                <div className="flex items-center gap-3">
                  <span className="grid size-11 place-items-center rounded-full bg-[#ff5b35] text-white"><Bike /></span>
                  <div><b>{ORDER_STEPS[stepIndex]?.label}</b><p className="text-sm text-[#746b67]">Dự kiến giao trong 20 - 30 phút</p></div>
                </div>
                <ol className="mt-7 flex flex-col gap-5">
                  {ORDER_STEPS.map((s, i) => (
                    <li key={s.status} className="flex items-center gap-3 text-sm">
                      <span className={`grid size-7 place-items-center rounded-full ${i <= stepIndex ? 'bg-[#ff5b35] text-white' : 'bg-white text-[#aaa09b]'}`}>{i <= stepIndex ? <Check className="size-4" /> : i + 1}</span>
                      <span className={i === stepIndex ? 'font-bold text-[#ff5b35]' : i < stepIndex ? 'text-[#241c19]' : 'text-[#aaa09b]'}>{s.label}</span>
                    </li>
                  ))}
                </ol>
              </div>
            )}
          </Panel>

          <Panel title="Giao đến">
            <div className="flex gap-3 text-sm">
              <MapPin className="size-5 shrink-0 text-[#ff5b35]" />
              <div><b>{order.recipient}</b> <span className="text-[#746b67]">• {order.phone}</span><p className="mt-1 text-[#746b67]">{order.address}</p>
                {order.note && <p className="mt-2 text-[#746b67]"><b className="text-[#241c19]">Ghi chú:</b> {order.note}</p>}</div>
            </div>
          </Panel>
        </div>

        <div className="flex flex-col gap-6">
          <Panel title="Món đã đặt">
            <div className="flex flex-col gap-4">
              {(order.order_items ?? []).map(i => (
                <div key={i.id} className="flex items-center gap-3">
                  {i.image && <img src={i.image} alt="" className="size-14 rounded-lg object-cover" />}
                  <div className="min-w-0 flex-1 text-sm"><b className="block truncate">{i.name}</b><p className="text-[#9c918c]">x{i.qty} • {money(i.price)}</p></div>
                  <span className="font-bold">{money(i.price * i.qty)}</span>
                </div>
              ))}
              <div className="border-t border-[#f1e7e2] pt-4 text-sm">
                <div className="flex justify-between text-[#746b67]"><span>Tạm tính</span><span>{money(order.subtotal)}</span></div>
                <div className="mt-3 flex justify-between text-[#746b67]"><span>Phí giao hàng</span>{order.shipping_fee ? <span>{money(order.shipping_fee)}</span> : <span className="text-[#72a77f]">Miễn phí</span>}</div>
                {order.discount > 0 && <div className="mt-3 flex justify-between text-[#2f7d4f]"><span>Giảm giá{order.voucher_code && ` (${order.voucher_code})`}</span><span>-{money(order.discount)}</span></div>}
                <div className="mt-4 flex justify-between text-lg font-extrabold"><span>Tổng cộng</span><span className="text-[#ff5b35]">{money(order.total)}</span></div>
                <div className="mt-3 flex justify-between text-[#746b67]">
                  <span>{PAYMENT_LABEL[order.payment_method]}</span>
                  {order.payment_status === 'paid' ? <b className="text-[#3eaa68]">Đã thanh toán</b>
                    : order.payment_method === 'cod' ? <b className="text-[#746b67]">Trả khi nhận hàng</b>
                    : <b className="text-[#bd8300]">Chưa thanh toán</b>}
                </div>
              </div>
            </div>
          </Panel>

          <div className="flex flex-col gap-3">
            {/* cancellable until the kitchen starts cooking (same rule as fg_cancel_order) */}
            {(order.status === 'pending' || order.status === 'confirmed') && (
              <div>
                <Button variant="outline" disabled={busy} onClick={cancel} className="h-12 w-full rounded-xl text-red-500">Hủy đơn hàng</Button>
                <p className="mt-1.5 text-center text-xs text-[#9c918c]">Chỉ hủy được khi quán chưa bắt đầu chuẩn bị món</p>
              </div>
            )}
            {cancelled && order.payment_status === 'paid' && (
              <p className="rounded-xl bg-[#fff7df] px-4 py-3 text-sm text-[#8a6100]">Đơn đã thanh toán trước, quán sẽ hoàn tiền cho bạn.</p>
            )}
            {cancelled && order.payment_status === 'refunded' && (
              <p className="rounded-xl bg-[#e4f8eb] px-4 py-3 text-sm text-[#2f7d4f]">Quán đã hoàn tiền cho đơn này.</p>
            )}
            {(order.status === 'delivered' || cancelled) && (
              <Button onClick={reorder} className="h-12 rounded-xl bg-[#ff5b35] hover:bg-[#e94c29]"><RotateCcw />Đặt lại đơn này</Button>
            )}
            <Button variant="outline" onClick={() => router.push('/')} className="h-12 rounded-xl">Tiếp tục mua hàng</Button>
          </div>
        </div>
      </div>
    </main>
  )
}

function CopyRow({ label, value, display }: { label: string; value: string; display?: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl bg-[#f8f3f0] px-4 py-2">
      <dt className="text-[#746b67]">{label}</dt>
      <dd className="flex items-center gap-2 font-bold">
        {display ?? value}
        <button aria-label={`Sao chép ${label}`} onClick={() => { navigator.clipboard?.writeText(value); setCopied(true); setTimeout(() => setCopied(false), 1500) }}
          className="grid size-8 place-items-center rounded-lg text-[#ff5b35] hover:bg-white">
          {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
        </button>
      </dd>
    </div>
  )
}
