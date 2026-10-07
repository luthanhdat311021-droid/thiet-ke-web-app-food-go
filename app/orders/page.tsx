'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { Package } from 'lucide-react'
import { RequireAuth } from '@/components/require-auth'
import { EmptyState, Spinner } from '@/components/cards'
import { useApp } from '@/components/app-provider'
import { supabase } from '@/lib/supabase'
import { formatDateTime, money, PAYMENT_LABEL, STATUS_LABEL, STATUS_STYLE } from '@/lib/format'
import type { Order, OrderStatus } from '@/lib/types'

const TABS: { label: string; statuses: OrderStatus[] | null }[] = [
  { label: 'Tất cả', statuses: null },
  { label: 'Đang xử lý', statuses: ['pending', 'confirmed', 'preparing'] },
  { label: 'Đang giao', statuses: ['picking_up', 'delivering'] },
  { label: 'Hoàn thành', statuses: ['delivered'] },
  { label: 'Đã hủy', statuses: ['cancelled'] },
]

export default function OrdersPage() {
  return <RequireAuth><Orders /></RequireAuth>
}

function Orders() {
  const { user } = useApp()
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [reviewed, setReviewed] = useState<Set<string>>(new Set()) // "orderId:foodId"
  const [tab, setTab] = useState(0)

  useEffect(() => {
    if (!user) return
    const load = () => {
      supabase.from('fg_orders').select('*, order_items:fg_order_items(*)').eq('user_id', user.id).order('created_at', { ascending: false })
        .then(({ data }) => setOrders((data ?? []) as Order[]))
      supabase.from('fg_reviews').select('order_id, food_id').eq('user_id', user.id)
        .then(({ data }) => setReviewed(new Set((data ?? []).map(r => `${r.order_id}:${r.food_id}`))))
    }
    load()
    const channel = supabase.channel(`orders-list-${user.id}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'fg_orders', filter: `user_id=eq.${user.id}` }, load)
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [user])

  const statuses = TABS[tab].statuses
  const shown = orders?.filter(o => !statuses || statuses.includes(o.status))

  return (
    <main className="mx-auto max-w-[1000px] px-5 pb-24 pt-8 lg:px-10">
      <h1 className="text-3xl font-extrabold">Đơn hàng của tôi</h1>
      <div className="mt-7 flex gap-2 overflow-x-auto border-b border-[#eaded8]">
        {TABS.map((t, i) => (
          <button key={t.label} onClick={() => setTab(i)} className={`whitespace-nowrap border-b-2 px-4 pb-3 text-sm font-bold ${i === tab ? 'border-[#ff5b35] text-[#ff5b35]' : 'border-transparent text-[#746b67]'}`}>{t.label}</button>
        ))}
      </div>
      <div className="mt-6 flex flex-col gap-4">
        {shown === undefined && <Spinner />}
        {shown?.length === 0 && <EmptyState icon={<Package />} title="Chưa có đơn hàng nào"><Link href="/search" className="font-bold text-[#ff5b35]">Đặt món ngay</Link></EmptyState>}
        {shown?.map(o => {
          const items = o.order_items ?? []
          const awaitingReview = o.status === 'delivered' && items.some(i => i.food_id !== null && !reviewed.has(`${o.id}:${i.food_id}`))
          return (
            <Link key={o.id} href={awaitingReview ? `/orders/${o.id}#danh-gia` : `/orders/${o.id}`} className="block rounded-2xl bg-white p-5 shadow-sm transition hover:shadow-md">
              <div className="flex items-start justify-between gap-3">
                <div><b>Đơn #{o.code}</b><p className="mt-1 text-xs text-[#9c918c]">{formatDateTime(o.created_at)}</p></div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${STATUS_STYLE[o.status]}`}>{STATUS_LABEL[o.status]}</span>
                  {awaitingReview && (
                    <span className="rounded-full bg-[#fff7df] px-3 py-1 text-xs font-bold text-[#bd8300]">★ Chờ đánh giá</span>
                  )}
                </div>
              </div>
              <div className="mt-5 flex items-center gap-3 border-y border-[#f1e7e2] py-4">
                {items[0]?.image && <img src={items[0].image} alt="" className="size-14 rounded-lg object-cover" />}
                <div className="min-w-0 text-sm">
                  <b className="block truncate">{items.map(i => `${i.name} x${i.qty}`).join(', ')}</b>
                  <p className="mt-1 text-[#746b67]">{items.reduce((s, i) => s + i.qty, 0)} phần • {PAYMENT_LABEL[o.payment_method]}
                    {o.payment_status === 'paid' ? <span className="ml-2 font-semibold text-[#3eaa68]">Đã thanh toán</span>
                      : o.payment_method !== 'cod' && o.status !== 'cancelled' ? <span className="ml-2 font-semibold text-[#bd8300]">Chờ thanh toán</span> : null}
                  </p>
                </div>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <b>Tổng cộng <span className="text-[#ff5b35]">{money(o.total)}</span></b>
                {awaitingReview
                  ? <span className="rounded-xl bg-[#ff5b35] px-4 py-2 text-sm font-bold text-white">★ Đánh giá</span>
                  : <span className="rounded-xl border border-[#eaded8] px-4 py-2 text-sm font-semibold">Xem chi tiết</span>}
              </div>
            </Link>
          )
        })}
      </div>
    </main>
  )
}
