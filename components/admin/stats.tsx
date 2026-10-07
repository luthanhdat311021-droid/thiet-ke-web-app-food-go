'use client'

import { useEffect, useMemo, useState } from 'react'
import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { Spinner } from '@/components/cards'
import { supabase } from '@/lib/supabase'
import { money, PAYMENT_LABEL } from '@/lib/format'
import type { Order } from '@/lib/types'

const RANGES = [7, 30, 90] as const
type Range = (typeof RANGES)[number]
const DAY = 86400000

const startOfDay = (t: number) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime() }
const short = (n: number) => n >= 1e6 ? `${(n / 1e6).toFixed(1).replace('.0', '')}tr` : n >= 1000 ? `${Math.round(n / 1000)}k` : String(n)
const pct = (n: number) => `${(n * 100).toFixed(1).replace('.0', '')}%`

/** Revenue is counted on the day an order was delivered (same rule as "Tổng quan"); order counts use the day it was placed. */
export function StatsAdmin() {
  const [range, setRange] = useState<Range>(30)
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [reviews, setReviews] = useState<{ rating: number; updated_at: string }[]>([])

  // fetch two periods (current + the one before) so every number can show its trend
  useEffect(() => {
    setOrders(null)
    let cancelled = false
    const since = new Date(startOfDay(Date.now()) - (range * 2 - 1) * DAY).toISOString()
    ;(async () => {
      const all: Order[] = []
      for (let from = 0; ; from += 1000) {
        const { data } = await supabase.from('fg_orders').select('*, order_items:fg_order_items(*)')
          .or(`created_at.gte.${since},delivered_at.gte.${since}`).order('id').range(from, from + 999)
        all.push(...((data ?? []) as Order[]))
        if (!data || data.length < 1000) break
      }
      const { data: r } = await supabase.from('fg_reviews').select('rating, updated_at').gte('updated_at', since)
      if (cancelled) return
      setOrders(all)
      setReviews(r ?? [])
    })()
    return () => { cancelled = true }
  }, [range])

  const s = useMemo(() => orders && compute(orders, reviews, range), [orders, reviews, range])

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Thống kê</h1>
          <p className="mt-0.5 text-xs text-[#9c918c]">So với {range} ngày liền trước</p>
        </div>
        <div className="flex rounded-xl bg-white p-1 shadow-sm" role="tablist" aria-label="Khoảng thời gian">
          {RANGES.map(r => (
            <button key={r} role="tab" aria-selected={r === range} onClick={() => setRange(r)}
              className={`h-9 rounded-lg px-4 text-sm font-bold ${r === range ? 'bg-[#ff5b35] text-white' : 'text-[#746b67] hover:bg-[#fffaf7]'}`}>
              {r} ngày
            </button>
          ))}
        </div>
      </div>

      {!s ? <Spinner /> : (
        <>
          <div className="mt-5 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
            <Kpi label="Doanh thu" value={money(s.cur.revenue)} delta={trend(s.cur.revenue, s.prev.revenue)} />
            <Kpi label="Đơn hoàn thành" value={String(s.cur.delivered)} delta={trend(s.cur.delivered, s.prev.delivered)} />
            <Kpi label="Giá trị TB / đơn" value={money(s.cur.aov)} delta={trend(s.cur.aov, s.prev.aov)} />
            <Kpi label="Tỉ lệ hủy đơn" value={pct(s.cur.cancelRate)} hint={`${s.cur.cancelled} / ${s.cur.placed} đơn đặt`} delta={trend(s.cur.cancelRate, s.prev.cancelRate)} lowerIsBetter />
          </div>

          <Card title="Doanh thu theo ngày" sub={range === 90 ? 'Gộp theo tuần • tính theo ngày giao xong' : 'Tính theo ngày giao xong'} className="mt-4 sm:mt-6">
            <ColumnChart data={s.revenueSeries} format={money} detail={d => `${d.orders} đơn`} />
          </Card>

          <div className="mt-4 grid gap-4 sm:mt-6 sm:gap-6 xl:grid-cols-2">
            <Card title="Món bán chạy" sub="Theo số phần đã giao">
              {s.topFoods.length === 0 ? <Empty /> : (
                <ol className="flex flex-col gap-3">
                  {s.topFoods.map((f, i) => (
                    <li key={f.name} className="text-sm">
                      <div className="flex items-baseline justify-between gap-3">
                        <span className="min-w-0 truncate"><span className="mr-2 inline-block w-5 text-[#9c918c]">{i + 1}.</span><b>{f.name}</b></span>
                        <span className="shrink-0 text-xs text-[#746b67]"><b className="text-[#241c19]">{f.qty}</b> phần • {money(f.revenue)}</span>
                      </div>
                      <Bar ratio={f.qty / s.topFoods[0].qty} />
                    </li>
                  ))}
                </ol>
              )}
            </Card>

            <Card title="Giờ cao điểm" sub="Số đơn đặt theo giờ trong ngày">
              <ColumnChart data={s.hours} format={n => `${n} đơn`} compact />
            </Card>

            <Card title="Phương thức thanh toán" sub="Đơn đã giao">
              {s.payments.every(p => p.count === 0) ? <Empty /> : (
                <div className="flex flex-col gap-3">
                  {s.payments.map(p => (
                    <div key={p.method} className="text-sm">
                      <div className="flex justify-between gap-3">
                        <b>{PAYMENT_LABEL[p.method]}</b>
                        <span className="text-xs text-[#746b67]"><b className="text-[#241c19]">{p.count}</b> đơn • {money(p.revenue)} • {pct(p.share)}</span>
                      </div>
                      <Bar ratio={p.share} />
                    </div>
                  ))}
                </div>
              )}
            </Card>

            <Card title="Khuyến mãi & đánh giá">
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div><dt className="text-[#746b67]">Tiền đã giảm cho khách</dt><dd className="mt-1 text-xl font-extrabold">{money(s.cur.discount)}</dd></div>
                <div><dt className="text-[#746b67]">Đơn dùng mã giảm giá</dt><dd className="mt-1 text-xl font-extrabold">{s.cur.voucherOrders}</dd></div>
                <div><dt className="text-[#746b67]">Điểm đánh giá TB</dt><dd className="mt-1 text-xl font-extrabold">{s.cur.reviews ? `${s.cur.rating.toFixed(1)} ★` : '—'}</dd></div>
                <div><dt className="text-[#746b67]">Lượt đánh giá mới</dt><dd className="mt-1 text-xl font-extrabold">{s.cur.reviews}</dd></div>
              </dl>
              {s.vouchers.length > 0 && (
                <table className="mt-5 w-full text-left text-sm">
                  <thead className="text-xs text-[#9c918c]"><tr><th className="pb-2 font-semibold">Mã</th><th className="pb-2 text-right font-semibold">Lượt dùng</th><th className="pb-2 text-right font-semibold">Đã giảm</th></tr></thead>
                  <tbody>
                    {s.vouchers.map(v => (
                      <tr key={v.code} className="border-t border-[#f8f3f0]"><td className="py-2 font-mono font-bold">{v.code}</td><td className="py-2 text-right">{v.count}</td><td className="py-2 text-right">{money(v.discount)}</td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  )
}

type Point = { label: string; title: string; value: number; orders?: number }

function compute(orders: Order[], reviews: { rating: number; updated_at: string }[], range: Range) {
  const end = startOfDay(Date.now()) + DAY
  const start = end - range * DAY
  const prevStart = start - range * DAY
  const deliveredAt = (o: Order) => new Date(o.delivered_at ?? o.updated_at).getTime()
  const createdAt = (o: Order) => new Date(o.created_at).getTime()

  const period = (from: number, to: number) => {
    const delivered = orders.filter(o => o.status === 'delivered' && deliveredAt(o) >= from && deliveredAt(o) < to)
    const placed = orders.filter(o => createdAt(o) >= from && createdAt(o) < to)
    const cancelled = placed.filter(o => o.status === 'cancelled').length
    const revenue = delivered.reduce((t, o) => t + o.total, 0)
    const rv = reviews.filter(r => { const t = new Date(r.updated_at).getTime(); return t >= from && t < to })
    return {
      delivered: delivered.length, deliveredList: delivered, placedList: placed, placed: placed.length, cancelled, revenue,
      aov: delivered.length ? Math.round(revenue / delivered.length) : 0,
      cancelRate: placed.length ? cancelled / placed.length : 0,
      discount: delivered.reduce((t, o) => t + (o.discount ?? 0), 0),
      voucherOrders: delivered.filter(o => o.voucher_code).length,
      reviews: rv.length, rating: rv.length ? rv.reduce((t, r) => t + r.rating, 0) / rv.length : 0,
    }
  }
  const cur = period(start, end)
  const prev = period(prevStart, start)

  // daily bars (weekly for 90 days, so bars stay readable)
  const bucket = range === 90 ? 7 : 1
  const revenueSeries: Point[] = []
  for (let t = start; t < end; t += bucket * DAY) {
    const to = Math.min(end, t + bucket * DAY)
    const list = cur.deliveredList.filter(o => deliveredAt(o) >= t && deliveredAt(o) < to)
    const d = new Date(t)
    const dm = (x: Date) => x.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
    revenueSeries.push({
      label: bucket > 1 ? dm(d) : range === 7 ? ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'][d.getDay()] : String(d.getDate()),
      title: bucket > 1 ? `${dm(d)} – ${dm(new Date(to - DAY))}` : d.toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit' }),
      value: list.reduce((s, o) => s + o.total, 0),
      orders: list.length,
    })
  }

  const hours: Point[] = Array.from({ length: 24 }, (_, h) => ({
    label: h % 3 === 0 ? `${h}h` : '', title: `${h}:00 – ${h}:59`,
    value: cur.placedList.filter(o => o.status !== 'cancelled' && new Date(o.created_at).getHours() === h).length,
  }))

  const foods = new Map<string, { name: string; qty: number; revenue: number }>()
  for (const o of cur.deliveredList) for (const i of o.order_items ?? []) {
    const key = i.food_id ? `#${i.food_id}` : i.name
    const f = foods.get(key) ?? { name: i.name, qty: 0, revenue: 0 }
    f.qty += i.qty; f.revenue += i.qty * i.price
    foods.set(key, f)
  }
  const topFoods = [...foods.values()].sort((a, b) => b.qty - a.qty || b.revenue - a.revenue).slice(0, 8)

  const payments = (['momo', 'qr', 'cod'] as const).map(method => {
    const list = cur.deliveredList.filter(o => o.payment_method === method)
    return { method, count: list.length, revenue: list.reduce((t, o) => t + o.total, 0), share: cur.delivered ? list.length / cur.delivered : 0 }
  })

  const vmap = new Map<string, { code: string; count: number; discount: number }>()
  for (const o of cur.deliveredList) if (o.voucher_code) {
    const v = vmap.get(o.voucher_code) ?? { code: o.voucher_code, count: 0, discount: 0 }
    v.count++; v.discount += o.discount
    vmap.set(o.voucher_code, v)
  }
  const vouchers = [...vmap.values()].sort((a, b) => b.count - a.count)

  return { cur, prev, revenueSeries, hours, topFoods, payments, vouchers }
}

/** relative change; null when there's nothing to compare against */
const trend = (cur: number, prev: number) => (prev ? (cur - prev) / prev : null)

function Kpi({ label, value, hint, delta, lowerIsBetter }: { label: string; value: string; hint?: string; delta: number | null; lowerIsBetter?: boolean }) {
  const good = delta !== null && (lowerIsBetter ? delta < 0 : delta > 0)
  return (
    <div className="min-w-0 rounded-2xl bg-white p-4 shadow-sm sm:p-5">
      <p className="text-xs text-[#746b67] sm:text-sm">{label}</p>
      <p className="mt-1 truncate text-xl font-extrabold sm:mt-2 sm:text-2xl">{value}</p>
      <p className="mt-0.5 truncate text-[11px] text-[#9c918c]">
        {delta === null || Math.abs(delta) < 0.0005 ? (hint ?? 'Chưa có dữ liệu để so sánh') : (
          <span className={`inline-flex items-center gap-0.5 font-bold ${good ? 'text-[#2f7d4f]' : 'text-[#c2410c]'}`}>
            {delta > 0 ? <ArrowUpRight className="size-3" /> : <ArrowDownRight className="size-3" />}{pct(Math.abs(delta))}
            {hint && <span className="ml-1 font-normal text-[#9c918c]">• {hint}</span>}
          </span>
        )}
      </p>
    </div>
  )
}

function Card({ title, sub, className = '', children }: { title: string; sub?: string; className?: string; children: React.ReactNode }) {
  return (
    <section className={`min-w-0 rounded-2xl bg-white p-4 shadow-sm sm:p-5 ${className}`}>
      <h2 className="font-extrabold">{title}</h2>
      {sub && <p className="mt-0.5 text-xs text-[#9c918c]">{sub}</p>}
      <div className="mt-5">{children}</div>
    </section>
  )
}

const Bar = ({ ratio }: { ratio: number }) => (
  <div className="mt-1.5 h-2 rounded-full bg-[#f8f3f0]"><div className="h-full rounded-full bg-[#ff5b35]" style={{ width: `${Math.max(2, ratio * 100)}%` }} /></div>
)

const Empty = () => <p className="py-8 text-center text-sm text-[#9c918c]">Chưa có dữ liệu trong khoảng này</p>

/** Single-series column chart; hover (or tap) a column for its exact value. */
function ColumnChart({ data, format, detail, compact }: { data: Point[]; format: (n: number) => string; detail?: (p: Point) => string; compact?: boolean }) {
  const [active, setActive] = useState<number | null>(null)
  const max = Math.max(1, ...data.map(d => d.value))
  const total = data.reduce((s, d) => s + d.value, 0)
  const shown = active !== null ? data[active] : null
  return (
    <div>
      <p className="h-5 text-xs text-[#746b67]" aria-live="polite">
        {shown ? <><b className="text-[#241c19]">{shown.title}:</b> {format(shown.value)}{detail && ` • ${detail(shown)}`}</>
          : total === 0 ? 'Chưa có dữ liệu trong khoảng này' : 'Rê chuột hoặc chạm vào cột để xem chi tiết'}
      </p>
      <div className={`mt-2 flex ${compact ? 'h-36' : 'h-48'} gap-[2px] border-b border-[#f1e7e2]`} onMouseLeave={() => setActive(null)}>
        {data.map((d, i) => (
          <button key={i} type="button" aria-label={`${d.title}: ${format(d.value)}`}
            onMouseEnter={() => setActive(i)} onFocus={() => setActive(i)} onClick={() => setActive(i)}
            className="group flex h-full min-w-0 flex-1 items-end justify-center outline-none">
            <span className={`w-full max-w-10 rounded-t ${d.value ? (active === i ? 'bg-[#c43d1d]' : 'bg-[#ff5b35] group-hover:bg-[#e94c29]') : 'bg-[#f1e7e2]'}`}
              style={{ height: d.value ? `${Math.max(3, (d.value / max) * 100)}%` : '2px' }} />
          </button>
        ))}
      </div>
      <div className="mt-1.5 flex gap-[2px]">
        {data.map((d, i) => (
          <span key={i} className={`min-w-0 flex-1 overflow-visible whitespace-nowrap text-center text-[10px] sm:text-[11px] ${active === i ? 'font-bold text-[#ff5b35]' : 'text-[#9c918c]'}`}>
            {data.length > 16 && !compact ? (i % 5 === 0 || i === data.length - 1 ? d.label : '') : d.label}
          </span>
        ))}
      </div>
      {total > 0 && <p className="mt-2 text-right text-[11px] text-[#9c918c]">Cao nhất: {format === money ? `${short(max)}đ` : format(max)}</p>}
    </div>
  )
}
