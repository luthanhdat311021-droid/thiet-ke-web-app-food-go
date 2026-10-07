'use client'

import { useEffect, useState } from 'react'
import { Spinner } from '@/components/cards'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money, ORDER_STEPS, STATUS_LABEL, STATUS_STYLE } from '@/lib/format'
import type { Order, OrderStatus } from '@/lib/types'

const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  pending: 'confirmed', confirmed: 'preparing', preparing: 'picking_up', picking_up: 'delivering', delivering: 'delivered',
}

/** Order queue with status buttons. Admin sees every restaurant; a restaurant owner passes their `restaurantId`. */
export function OrdersManager({ restaurantId }: { restaurantId?: number }) {
  const { toast } = useApp()
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [filter, setFilter] = useState<'active' | OrderStatus | 'all'>('active')
  const [restaurant, setRestaurant] = useState('')
  const [open, setOpen] = useState<number | null>(null)

  const load = () => {
    let q = supabase.from('fg_orders').select('*, order_items:fg_order_items(*)')
    if (restaurantId) q = q.eq('restaurant_id', restaurantId)
    return q.order('created_at', { ascending: false }).limit(200).then(({ data }) => setOrders((data ?? []) as Order[]))
  }
  useEffect(() => {
    load()
    const channel = supabase.channel(`orders-${restaurantId ?? 'all'}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'fg_orders', ...(restaurantId ? { filter: `restaurant_id=eq.${restaurantId}` } : {}) }, payload => {
        if (payload.eventType === 'INSERT') toast(`Có đơn mới #${(payload.new as Order).code}`)
        load()
      })
      .subscribe()
    return () => { supabase.removeChannel(channel) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restaurantId])

  const update = async (o: Order, patch: Partial<Order>) => {
    const { error } = await supabase.from('fg_orders').update(patch).eq('id', o.id)
    if (error) return toast(errorMessage(error), 'error')
    setOrders(list => list?.map(x => (x.id === o.id ? { ...x, ...patch } : x)) ?? null)
  }

  // cancelled after paying = the shop still owes a refund, so it stays in the "to do" list
  const needsRefund = (o: Order) => o.status === 'cancelled' && o.payment_status === 'paid'
  const restaurantNames = [...new Set((orders ?? []).map(o => o.restaurant_name))].sort()
  const shown = orders?.filter(o => (filter === 'all' ? true
    : filter === 'active' ? !['delivered', 'cancelled'].includes(o.status) || needsRefund(o)
    : o.status === filter) && (!restaurant || o.restaurant_name === restaurant))

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Đơn hàng</h1>
        <div className="flex flex-wrap gap-2">
          {restaurantNames.length > 1 && (
            <select aria-label="Lọc nhà hàng" value={restaurant} onChange={e => setRestaurant(e.target.value)} className="h-10 max-w-[200px] rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
              <option value="">Mọi nhà hàng</option>
              {restaurantNames.map(n => <option key={n} value={n}>{n}</option>)}
            </select>
          )}
          <select aria-label="Lọc trạng thái" value={filter} onChange={e => setFilter(e.target.value as typeof filter)} className="h-10 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
            <option value="active">Đang xử lý</option>
            <option value="all">Tất cả</option>
            {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {shown === undefined && <Spinner />}
        {shown?.length === 0 && <p className="rounded-2xl bg-white py-12 text-center text-sm text-[#9c918c] shadow-sm">Không có đơn nào</p>}
        {shown?.map(o => {
          const next = NEXT_STATUS[o.status]
          return (
            <div key={o.id} className="rounded-2xl bg-white p-4 shadow-sm">
              {/* row 1: code + order status */}
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <b className="block truncate">#{o.code}</b>
                  <p className="mt-0.5 text-xs text-[#9c918c]"><span className="font-bold text-[#ff5b35]">{o.restaurant_name}</span> • {formatDateTime(o.created_at)}</p>
                </div>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-bold ${STATUS_STYLE[o.status]}`}>{STATUS_LABEL[o.status]}</span>
              </div>
              {/* row 2: customer */}
              <p className="mt-3 text-sm text-[#746b67]">
                <b className="text-[#241c19]">{o.recipient}</b> • <a href={`tel:${o.phone}`} className="text-[#ff5b35]">{o.phone}</a>
              </p>
              <p className="mt-0.5 line-clamp-2 text-xs text-[#9c918c]">{o.address}</p>
              {/* row 3: total + payment */}
              <div className="mt-3 flex items-center justify-between gap-3 border-t border-[#f8f3f0] pt-3">
                <div>
                  <b className="text-lg text-[#ff5b35]">{money(o.total)}</b>
                  <span className="ml-2 text-xs text-[#9c918c]">{(o.order_items ?? []).reduce((s, i) => s + i.qty, 0)} món</span>
                </div>
                <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-bold ${
                  needsRefund(o) ? 'bg-red-50 text-red-600'
                  : o.payment_status === 'paid' ? 'bg-[#e4f8eb] text-[#3eaa68]'
                  : o.payment_status === 'refunded' ? 'bg-[#f4f0ee] text-[#746b67]'
                  : 'bg-[#fff7df] text-[#bd8300]'}`}>
                  {{ qr: 'QR', cod: 'COD', momo: 'MoMo' }[o.payment_method]} • {
                    needsRefund(o) ? 'Cần hoàn tiền'
                    : o.payment_status === 'paid' ? 'Đã TT'
                    : o.payment_status === 'refunded' ? 'Đã hoàn tiền'
                    : o.payment_method === 'cod' ? 'Thu khi giao' : 'Chờ tiền vào'}
                </span>
              </div>
              <button onClick={() => setOpen(open === o.id ? null : o.id)} className="mt-2 py-1 text-xs font-bold text-[#ff5b35]">
                {open === o.id ? 'Ẩn chi tiết ▴' : 'Xem món & ghi chú ▾'}
              </button>
              {open === o.id && (
                <div className="mt-2 grid gap-3 rounded-xl bg-[#fffaf7] p-3 text-sm md:grid-cols-2">
                  <div>
                    {(o.order_items ?? []).map(i => <p key={i.id}>{i.qty} × {i.name} <span className="text-[#9c918c]">({money(i.price)})</span></p>)}
                    <p className="mt-2 text-[#746b67]">Phí ship: {money(o.shipping_fee)}</p>
                    {o.discount > 0 && <p className="text-[#2f7d4f]">Mã {o.voucher_code}: -{money(o.discount)}</p>}
                  </div>
                  <div className="text-[#746b67]">{o.note ? <p><b className="text-[#241c19]">Ghi chú:</b> {o.note}</p> : <p className="text-[#9c918c]">Không có ghi chú</p>}</div>
                </div>
              )}
              {/* actions: main step full-width on phones, secondary buttons share the next row */}
              <div className="mt-3 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
                {next && (() => {
                  // payment is settled by a status step (the DB does the same in its trigger):
                  // online (QR/MoMo) → confirming means the money arrived; COD → delivering means cash collected
                  const settles = o.payment_status !== 'paid' && (
                    (next === 'confirmed' && o.payment_method !== 'cod') || (next === 'delivered' && o.payment_method === 'cod'))
                  const label = !settles ? ORDER_STEPS.find(s => s.status === next)?.label
                    : o.payment_method === 'cod' ? 'Đã giao & thu tiền' : 'Đã nhận tiền & xác nhận'
                  return (
                    <button onClick={() => update(o, settles ? { status: next, payment_status: 'paid' } : { status: next })}
                      className="col-span-2 h-10 rounded-lg bg-[#ff5b35] px-3 text-sm font-bold text-white hover:bg-[#e94c29]">
                      → {label}
                    </button>
                  )
                })()}
                {!['delivered', 'cancelled'].includes(o.status) && (
                  <button onClick={() => window.confirm(`Hủy đơn #${o.code}?${o.payment_status === 'paid' ? ' Đơn đã thanh toán, bạn sẽ cần hoàn tiền cho khách.' : ''}`) && update(o, { status: 'cancelled' })} className="h-10 rounded-lg border border-[#eaded8] px-3 text-xs font-bold text-red-500 hover:bg-red-50">Hủy đơn</button>
                )}
                {needsRefund(o) && (
                  <button onClick={() => window.confirm(`Xác nhận đã hoàn ${money(o.total)} cho khách (đơn #${o.code})?`) && update(o, { payment_status: 'refunded' })}
                    className="col-span-2 h-10 rounded-lg bg-red-500 px-3 text-sm font-bold text-white hover:bg-red-600">Đã hoàn tiền cho khách</button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
