'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { CheckCircle2, Loader2, Store, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/cards'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money } from '@/lib/format'
import { fetchRestaurants } from '@/lib/store'
import { SUBSCRIPTION_FEE, SUBSCRIPTION_MONTHS, subscriptionOf } from '@/lib/shop'
import type { Profile, Restaurant, SubscriptionPayment } from '@/lib/types'

type PaymentRow = SubscriptionPayment & { restaurant: { name: string } | null }

const monthKey = (d: Date) => `${d.getFullYear()}-${d.getMonth()}`

function useSubscriptionData() {
  const [payments, setPayments] = useState<PaymentRow[] | null>(null)
  const [shops, setShops] = useState<Restaurant[]>([])
  const load = () => {
    supabase.from('fg_subscription_payments').select('*, restaurant:fg_restaurants(name)').order('created_at', { ascending: false }).limit(500)
      .then(({ data }) => setPayments((data ?? []) as PaymentRow[]))
    supabase.from('fg_restaurants').select('*').not('owner_id', 'is', null).order('paid_until', { ascending: true, nullsFirst: true })
      .then(({ data }) => setShops((data ?? []) as Restaurant[]))
  }
  useEffect(() => { load() }, [])
  const totals = useMemo(() => {
    const paid = (payments ?? []).filter(p => p.status === 'paid' && p.amount > 0)
    const now = new Date()
    const thisMonth = paid.filter(p => monthKey(new Date(p.paid_at!)) === monthKey(now))
    return {
      month: thisMonth.reduce((s, p) => s + p.amount, 0),
      monthCount: thisMonth.length,
      all: paid.reduce((s, p) => s + p.amount, 0),
      active: shops.filter(r => subscriptionOf(r).active).length,
      expiring: shops.filter(r => { const s = subscriptionOf(r); return s.active && s.daysLeft <= 7 }).length,
      pending: (payments ?? []).filter(p => p.status === 'pending').length,
      // last 6 calendar months, oldest first
      months: Array.from({ length: 6 }, (_, i) => {
        const d = new Date(now.getFullYear(), now.getMonth() - (5 - i), 1)
        return { label: `T${d.getMonth() + 1}`, value: paid.filter(p => monthKey(new Date(p.paid_at!)) === monthKey(d)).reduce((s, p) => s + p.amount, 0) }
      }),
    }
  }, [payments, shops])
  return { payments, shops, totals, load }
}

/** Top of "Tổng quan": the platform's own income = restaurants' monthly fees. */
export function SubscriptionSummary() {
  const { payments, totals } = useSubscriptionData()
  if (!payments) return null
  return (
    <Link href="/admin?tab=subscriptions" className="mt-5 grid grid-cols-2 gap-3 rounded-2xl bg-[#241c19] p-4 text-white shadow-sm transition hover:bg-[#33281f] sm:grid-cols-4 sm:gap-4 sm:p-5">
      <div className="col-span-2 sm:col-span-1">
        <p className="text-xs text-white/70">Doanh thu phí duy trì tháng này</p>
        <p className="mt-1 text-2xl font-extrabold">{money(totals.month)}</p>
        <p className="text-[11px] text-white/60">{totals.monthCount} lượt gia hạn</p>
      </div>
      <div><p className="text-xs text-white/70">Tổng đã thu</p><p className="mt-1 text-lg font-extrabold">{money(totals.all)}</p></div>
      <div><p className="text-xs text-white/70">Nhà hàng đang hoạt động</p><p className="mt-1 text-lg font-extrabold">{totals.active}</p></div>
      <div className="hidden sm:block"><p className="text-xs text-white/70">Sắp hết hạn (≤ 7 ngày)</p><p className="mt-1 text-lg font-extrabold">{totals.expiring}</p></div>
    </Link>
  )
}

export function SubscriptionsAdmin() {
  const { toast } = useApp()
  const { payments, shops, totals, load } = useSubscriptionData()
  const [busy, setBusy] = useState<number | null>(null)
  const [status, setStatus] = useState<'all' | 'paid' | 'pending'>('all')

  // fallback when SePay didn't match the transfer (wrong content…): admin checks the bank app, then confirms
  const confirm = async (p: PaymentRow) => {
    if (!window.confirm(`Xác nhận đã nhận ${money(p.amount)} (nội dung ${p.code}) của ${p.restaurant?.name}?`)) return
    setBusy(p.id)
    const { error } = await supabase.rpc('fg_admin_confirm_subscription', { p_payment_id: p.id })
    setBusy(null)
    if (error) return toast(errorMessage(error), 'error')
    toast('Đã gia hạn cho nhà hàng')
    fetchRestaurants(true)
    load()
  }

  if (!payments) return <Spinner />
  const max = Math.max(1, ...totals.months.map(m => m.value))
  const shown = payments.filter(p => status === 'all' || p.status === status)
  return (
    <div>
      <h1 className="text-2xl font-extrabold">Phí duy trì nhà hàng</h1>
      <p className="mt-1 text-sm text-[#746b67]">{money(SUBSCRIPTION_FEE)} / tháng / nhà hàng • chuyển khoản QR, SePay tự xác nhận</p>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <Kpi label="Thu tháng này" value={money(totals.month)} hint={`${totals.monthCount} lượt gia hạn`} />
        <Kpi label="Tổng đã thu" value={money(totals.all)} />
        <Kpi label="Nhà hàng đang hoạt động" value={String(totals.active)} hint={`${totals.expiring} sắp hết hạn`} />
        <Kpi label="Hóa đơn chờ thanh toán" value={String(totals.pending)} />
      </div>

      <div className="mt-4 grid gap-4 sm:mt-6 sm:gap-6 xl:grid-cols-[1fr_380px]">
        <section className="rounded-2xl bg-white p-4 shadow-sm sm:p-5">
          <h2 className="font-extrabold">Doanh thu 6 tháng gần nhất</h2>
          <div className="mt-5 flex h-44 gap-3">
            {totals.months.map(m => (
              <div key={m.label} className="flex min-w-0 flex-1 flex-col items-center gap-1" title={`${m.label}: ${money(m.value)}`}>
                <span className="h-4 text-[11px] text-[#9c918c]">{m.value ? `${Math.round(m.value / 1000)}k` : ''}</span>
                <div className="flex w-full flex-1 items-end justify-center">
                  <div className={`w-full max-w-12 rounded-t-md ${m.value ? 'bg-[#ff5b35]' : 'bg-[#f1e7e2]'}`} style={{ height: `${Math.max(3, (m.value / max) * 100)}%` }} />
                </div>
                <span className="text-xs text-[#746b67]">{m.label}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-2xl bg-white p-4 shadow-sm sm:p-5">
          <h2 className="font-extrabold">Hạn dùng các nhà hàng</h2>
          {shops.length === 0 ? <p className="py-8 text-center text-sm text-[#9c918c]">Chưa có nhà hàng nào đăng ký</p> : (
            <ul className="mt-3 max-h-64 divide-y divide-[#f8f3f0] overflow-y-auto">
              {shops.map(r => {
                const s = subscriptionOf(r)
                return (
                  <li key={r.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                    <b className="min-w-0 truncate">{r.name}</b>
                    <span className={`shrink-0 text-xs font-bold ${!s.active ? 'text-[#c2410c]' : s.daysLeft <= 7 ? 'text-[#bd8300]' : 'text-[#2f7d4f]'}`}>
                      {!s.until ? 'Chưa thanh toán' : !s.active ? 'Đã hết hạn' : `Còn ${s.daysLeft} ngày (${s.until.toLocaleDateString('vi-VN')})`}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      </div>

      <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm sm:mt-6 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-extrabold">Lịch sử thanh toán</h2>
          <select aria-label="Lọc trạng thái" value={status} onChange={e => setStatus(e.target.value as typeof status)} className="h-10 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
            <option value="all">Tất cả</option><option value="paid">Đã thanh toán</option><option value="pending">Chờ thanh toán</option>
          </select>
        </div>
        {shown.length === 0 ? <p className="py-8 text-center text-sm text-[#9c918c]">Chưa có giao dịch</p> : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="text-xs text-[#9c918c]"><tr><th className="py-2 font-semibold">Mã CK</th><th className="py-2 font-semibold">Nhà hàng</th><th className="py-2 font-semibold">Gói</th><th className="py-2 text-right font-semibold">Số tiền</th><th className="py-2 font-semibold">Thời gian</th><th className="py-2 font-semibold">Trạng thái</th></tr></thead>
              <tbody>
                {shown.map(p => (
                  <tr key={p.id} className="border-t border-[#f8f3f0]">
                    <td className="py-2.5 font-mono text-xs font-bold">{p.code}</td>
                    <td className="py-2.5">{p.restaurant?.name ?? '—'}</td>
                    <td className="py-2.5">{p.months} tháng</td>
                    <td className="py-2.5 text-right font-bold">{p.method === 'admin' ? <span className="font-normal text-[#9c918c]">Admin cấp</span> : money(p.amount)}</td>
                    <td className="py-2.5 text-xs text-[#746b67]">{formatDateTime(p.paid_at ?? p.created_at)}</td>
                    <td className="py-2.5">
                      {p.status === 'paid' ? <span className="rounded-full bg-[#e4f8eb] px-2 py-1 text-xs font-bold text-[#3eaa68]">Đã thanh toán</span>
                        : p.status === 'pending' ? (
                          <button onClick={() => confirm(p)} disabled={busy === p.id} className="rounded-lg border border-[#eaded8] px-2 py-1 text-xs font-bold text-[#ff5b35] hover:bg-[#fff5f1]">
                            {busy === p.id ? 'Đang xử lý...' : 'Chờ tiền • Xác nhận đã nhận'}
                          </button>
                        ) : <span className="text-xs text-[#9c918c]">Đã hủy</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

function Kpi({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="min-w-0 rounded-2xl bg-white p-4 shadow-sm sm:p-5">
      <p className="text-xs text-[#746b67] sm:text-sm">{label}</p>
      <p className="mt-1 truncate text-xl font-extrabold sm:mt-2 sm:text-2xl">{value}</p>
      {hint && <p className="mt-0.5 truncate text-[11px] text-[#9c918c]">{hint}</p>}
    </div>
  )
}

/**
 * DEMO: admin gives a user a restaurant (new or an existing unowned one) with free months.
 * Remove once self-registration + SePay is the only path.
 */
export function GrantRestaurantButton({ user, ownedName, onDone }: { user: Profile; ownedName?: string; onDone: () => void }) {
  const { toast } = useApp()
  const [open, setOpen] = useState(false)
  const [unowned, setUnowned] = useState<Restaurant[]>([])
  const [target, setTarget] = useState<'new' | number>('new')
  const [name, setName] = useState('')
  const [months, setMonths] = useState(1)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    supabase.from('fg_restaurants').select('*').is('owner_id', null).order('name').then(({ data }) => setUnowned((data ?? []) as Restaurant[]))
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    const { error } = await supabase.rpc('fg_admin_grant_restaurant', {
      p_user: user.id, p_restaurant_id: target === 'new' ? null : target, p_name: target === 'new' ? name : null, p_months: months,
    })
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    toast(ownedName ? `Đã gia hạn ${months} tháng cho ${ownedName}` : `Đã cấp nhà hàng cho ${user.full_name}`)
    fetchRestaurants(true)
    setOpen(false)
    onDone()
  }

  return (
    <>
      <button onClick={() => setOpen(true)} className="inline-flex items-center gap-1 rounded-lg px-1 py-2 text-xs font-bold text-[#241c19] hover:text-[#ff5b35]">
        <Store className="size-3.5" />{ownedName ? 'Gia hạn quán' : 'Cấp nhà hàng'}
      </button>
      {open && createPortal(
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/30 sm:items-center" onClick={() => setOpen(false)}>
          <form onSubmit={submit} onClick={e => e.stopPropagation()} className="w-full max-w-md rounded-t-3xl bg-white p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] sm:rounded-3xl">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-extrabold">{ownedName ? 'Gia hạn nhà hàng' : 'Cấp nhà hàng'}</h2>
                <p className="mt-0.5 text-sm text-[#746b67]">Cho <b>{user.full_name || 'người dùng'}</b> • miễn phí (demo)</p>
              </div>
              <button type="button" aria-label="Đóng" onClick={() => setOpen(false)} className="grid size-10 place-items-center rounded-full bg-[#f8f3f0]"><X /></button>
            </div>
            <div className="mt-4 flex flex-col gap-4 text-sm">
              {ownedName ? (
                <p className="flex items-center gap-2 rounded-xl bg-[#f8f3f0] px-3 py-2.5"><CheckCircle2 className="size-4 text-[#3eaa68]" />Đang sở hữu <b>{ownedName}</b></p>
              ) : (
                <>
                  <label className="block font-semibold">Nhà hàng
                    <select value={String(target)} onChange={e => setTarget(e.target.value === 'new' ? 'new' : Number(e.target.value))} className="mt-2 h-11 w-full rounded-xl border border-[#eaded8] bg-white px-3 font-normal">
                      <option value="new">+ Tạo nhà hàng mới</option>
                      {unowned.map(r => <option key={r.id} value={r.id}>Giao {r.name} (đang do admin quản lý)</option>)}
                    </select>
                  </label>
                  {target === 'new' && (
                    <label className="block font-semibold">Tên nhà hàng
                      <input value={name} onChange={e => setName(e.target.value)} required placeholder="VD: Bún bò Cô Ba" className="mt-2 h-11 w-full rounded-xl border border-[#eaded8] px-3 font-normal outline-none focus:border-[#ff5b35]" />
                      <span className="mt-1 block text-xs font-normal text-[#9c918c]">Chủ quán tự ghim vị trí và thêm món trong “Kênh nhà hàng”.</span>
                    </label>
                  )}
                </>
              )}
              <label className="block font-semibold">Thời hạn
                <select value={months} onChange={e => setMonths(Number(e.target.value))} className="mt-2 h-11 w-full rounded-xl border border-[#eaded8] bg-white px-3 font-normal">
                  {SUBSCRIPTION_MONTHS.map(m => <option key={m} value={m}>{m} tháng</option>)}
                </select>
              </label>
            </div>
            <Button type="submit" disabled={busy} className="mt-5 h-12 w-full rounded-xl bg-[#ff5b35] hover:bg-[#e94c29]">
              {busy && <Loader2 className="animate-spin" />}{ownedName ? `Gia hạn ${months} tháng` : 'Cấp nhà hàng'}
            </Button>
          </form>
        </div>,
        document.body,
      )}
    </>
  )
}
