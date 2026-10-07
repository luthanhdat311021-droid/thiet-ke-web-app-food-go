'use client'

import Link from 'next/link'
import { Suspense, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ArrowLeft, BarChart3, Grid2x2, LayoutDashboard, LogOut, Package, Star, Store, TicketPercent, Trash2, Users, UtensilsCrossed } from 'lucide-react'
import { StatsAdmin } from '@/components/admin/stats'
import { fetchStore, storeHours, useStoreHours } from '@/lib/store'
import { Stars } from '@/components/reviews'
import { RequireAuth } from '@/components/require-auth'
import { useIsNativeApp } from '@/lib/native'
import { Logo } from '@/components/site-shell'
import { Spinner } from '@/components/cards'
import { EntityManager } from '@/components/admin/entity-manager'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money, ORDER_STEPS, STATUS_LABEL, STATUS_STYLE } from '@/lib/format'
import type { Category, Order, OrderStatus, Profile, Restaurant } from '@/lib/types'

const TABS = [
  { id: 'overview', label: 'Tổng quan', icon: LayoutDashboard },
  { id: 'stats', label: 'Thống kê', icon: BarChart3 },
  { id: 'orders', label: 'Đơn hàng', icon: Package },
  { id: 'foods', label: 'Món ăn', icon: UtensilsCrossed },
  { id: 'vouchers', label: 'Mã giảm giá', icon: TicketPercent },
  { id: 'restaurants', label: 'Thông tin quán', icon: Store },
  { id: 'reviews', label: 'Đánh giá', icon: Star },
  { id: 'categories', label: 'Danh mục', icon: Grid2x2 },
  { id: 'users', label: 'Người dùng', icon: Users },
] as const
type Tab = (typeof TABS)[number]['id']

export default function AdminPage() {
  return (
    <div className="min-h-screen bg-[#fffaf7] text-[#241c19]">
      <RequireAuth admin><Suspense fallback={<Spinner />}><Admin /></Suspense></RequireAuth>
    </div>
  )
}

function Admin() {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const { signOut } = useApp()
  const native = useIsNativeApp()
  const tab = (TABS.some(t => t.id === params.get('tab')) ? params.get('tab') : 'overview') as Tab

  return (
    <div className="lg:flex">
      {/* mobile: horizontal tab strip under the status bar (safe-area aware); desktop: sidebar */}
      <aside className="sticky top-0 z-20 flex items-center gap-1 overflow-x-auto border-b border-[#f1e7e2] bg-white px-3 pb-2 pt-[max(0.5rem,env(safe-area-inset-top))] [scrollbar-width:none] lg:h-screen lg:w-64 lg:shrink-0 lg:flex-col lg:items-stretch lg:gap-2 lg:border-b-0 lg:border-r lg:p-5 [&::-webkit-scrollbar]:hidden">
        <div className="hidden lg:mb-6 lg:block"><Logo /><p className="mt-2 text-xs font-bold text-[#9c918c]">TRANG QUẢN TRỊ</p></div>
        {TABS.map(({ id, label, icon: Icon }) => (
          <button key={id} onClick={() => router.replace(`${pathname}?tab=${id}`)}
            className={`flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm ${tab === id ? 'bg-[#fff0eb] font-bold text-[#ff5b35]' : 'text-[#746b67] hover:bg-[#fffaf7]'}`}>
            <Icon className="size-4" />{label}
          </button>
        ))}
        <div className="lg:mt-auto" />
        <Link href="/" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-[#746b67] hover:bg-[#fffaf7]"><ArrowLeft className="size-4" />{native ? 'Về ứng dụng' : 'Về trang web'}</Link>
        <button onClick={signOut} className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-red-500 hover:bg-red-50"><LogOut className="size-4" />Đăng xuất</button>
      </aside>
      <main className="min-w-0 flex-1 p-4 pb-[max(1.5rem,env(safe-area-inset-bottom))] sm:p-5 lg:p-10">
        {tab === 'overview' && <Overview />}
        {tab === 'stats' && <StatsAdmin />}
        {tab === 'vouchers' && <VouchersAdmin />}
        {tab === 'orders' && <OrdersAdmin />}
        {tab === 'foods' && <FoodsAdmin />}
        {tab === 'restaurants' && <RestaurantsAdmin />}
        {tab === 'categories' && <CategoriesAdmin />}
        {tab === 'users' && <UsersAdmin />}
        {tab === 'reviews' && <ReviewsAdmin />}
      </main>
    </div>
  )
}

// ---------------------------------------------------------------- overview
function Overview() {
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [counts, setCounts] = useState({ foods: 0, restaurants: 0, users: 0 })
  useEffect(() => {
    const since = new Date(Date.now() - 30 * 86400000).toISOString()
    // orders placed in the window plus older ones delivered in it (revenue counts by delivery day)
    supabase.from('fg_orders').select('*').or(`created_at.gte.${since},delivered_at.gte.${since}`).order('created_at', { ascending: false })
      .then(({ data }) => setOrders((data ?? []) as Order[]))
    Promise.all([
      supabase.from('fg_foods').select('id', { count: 'exact', head: true }),
      supabase.from('fg_restaurants').select('id', { count: 'exact', head: true }),
      supabase.from('fg_profiles').select('id', { count: 'exact', head: true }),
    ]).then(([f, r, u]) => setCounts({ foods: f.count ?? 0, restaurants: r.count ?? 0, users: u.count ?? 0 }))
  }, [])

  const stats = useMemo(() => {
    if (!orders) return null
    const today = new Date().toDateString()
    const since = Date.now() - 30 * 86400000
    // revenue = delivered orders only, dated by when they were delivered
    const deliveredDay = (o: Order) => new Date(o.delivered_at ?? o.updated_at)
    const delivered = orders.filter(o => o.status === 'delivered')
    const deliveredToday = delivered.filter(o => deliveredDay(o).toDateString() === today)
    const placedToday = orders.filter(o => o.status !== 'cancelled' && new Date(o.created_at).toDateString() === today)
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(Date.now() - (6 - i) * 86400000)
      const list = delivered.filter(o => deliveredDay(o).toDateString() === d.toDateString())
      // short labels so 7 columns fit a phone: "T2" … "CN", today = "Nay"
      const label = i === 6 ? 'Nay' : ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'][d.getDay()]
      return { label, date: d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }), revenue: list.reduce((s, o) => s + o.total, 0) }
    })
    return {
      todayRevenue: deliveredToday.reduce((s, o) => s + o.total, 0),
      deliveredTodayCount: deliveredToday.length,
      todayOrders: placedToday.length,
      monthRevenue: delivered.filter(o => deliveredDay(o).getTime() >= since).reduce((s, o) => s + o.total, 0),
      // placed but not finished yet: money still to come in
      inProgress: orders.filter(o => !['delivered', 'cancelled'].includes(o.status)).reduce((s, o) => s + o.total, 0),
      pending: orders.filter(o => o.status === 'pending').length,
      days,
    }
  }, [orders])

  if (!stats) return <Spinner />
  const max = Math.max(1, ...stats.days.map(d => d.revenue))
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Tổng quan</h1>
        <StoreSwitch />
      </div>
      <div className="mt-5 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <Stat label="Doanh thu hôm nay" value={money(stats.todayRevenue)} hint={`${stats.deliveredTodayCount} đơn đã giao`} />
        <Stat label="Đơn đặt hôm nay" value={String(stats.todayOrders)} />
        <Stat label="Doanh thu 30 ngày" value={money(stats.monthRevenue)} hint="Chỉ tính đơn đã giao" />
        <Stat label="Đơn chờ xác nhận" value={String(stats.pending)} highlight={stats.pending > 0} />
      </div>
      <div className="mt-4 grid gap-4 sm:mt-6 sm:gap-6 xl:grid-cols-[1fr_320px]">
        <section className="rounded-2xl bg-white p-4 shadow-sm sm:p-5">
          <h2 className="font-extrabold">Doanh thu 7 ngày gần nhất</h2>
          <p className="mt-0.5 text-xs text-[#9c918c]">Tính theo ngày giao xong</p>
          <div className="mt-5 flex h-48 gap-2 sm:gap-3">
            {stats.days.map(d => (
              <div key={d.date} className="flex min-w-0 flex-1 flex-col items-center gap-1" title={`${d.date}: ${money(d.revenue)}`}>
                <span className="h-4 text-[10px] leading-4 text-[#9c918c] sm:text-[11px]">{d.revenue ? `${Math.round(d.revenue / 1000)}k` : ''}</span>
                {/* bars scale within this area only, so labels never get pushed around */}
                <div className="flex w-full flex-1 items-end justify-center">
                  <div className={`w-full max-w-10 rounded-t-md ${d.revenue ? 'bg-[#ff5b35]' : 'bg-[#f1e7e2]'}`} style={{ height: `${Math.max(3, (d.revenue / max) * 100)}%` }} />
                </div>
                <span className={`text-[11px] sm:text-xs ${d.label === 'Nay' ? 'font-bold text-[#ff5b35]' : 'text-[#746b67]'}`}>{d.label}</span>
              </div>
            ))}
          </div>
        </section>
        <section className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-1">
          <div className="col-span-2 xl:col-span-1">
            <Stat label="Đơn đang xử lý (chưa tính doanh thu)" value={money(stats.inProgress)} />
          </div>
          <Stat label="Món ăn" value={String(counts.foods)} />
          <Stat label="Người dùng" value={String(counts.users)} />
        </section>
      </div>
    </div>
  )
}

/** One-tap "tạm đóng cửa" for busy moments; regular hours are set under "Thông tin quán". */
function StoreSwitch() {
  const { toast } = useApp()
  const [store, setStore] = useState<Restaurant | null>(null)
  const [busy, setBusy] = useState(false)
  const hours = useStoreHours(store)
  useEffect(() => { fetchStore(true).then(setStore) }, [])
  if (!store || !hours) return null

  const toggle = async () => {
    const next = !store.is_open
    if (!next && !window.confirm('Tạm đóng cửa? Khách sẽ không đặt được đơn mới cho tới khi bạn mở lại.')) return
    setBusy(true)
    const { error } = await supabase.from('fg_restaurants').update({ is_open: next }).eq('id', store.id)
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    setStore({ ...store, is_open: next })
    fetchStore(true)
    toast(next ? 'Đã mở cửa nhận đơn' : 'Đã tạm đóng cửa')
  }

  const status = hours.open ? 'Đang mở cửa' : hours.paused ? 'Đang tạm đóng' : `Ngoài giờ (mở ${hours.reopens})`
  return (
    <div className="flex items-center gap-3 rounded-xl bg-white px-3 py-2 shadow-sm">
      <span className={`size-2.5 rounded-full ${hours.open ? 'bg-[#3eaa68]' : 'bg-[#c9bdb7]'}`} />
      <span className="text-sm"><b>{status}</b>{hours.hours && <span className="ml-1 text-xs text-[#9c918c]">• {hours.hours}</span>}</span>
      <button onClick={toggle} disabled={busy}
        className={`h-9 rounded-lg px-3 text-xs font-bold ${store.is_open ? 'border border-[#eaded8] text-red-500 hover:bg-red-50' : 'bg-[#ff5b35] text-white hover:bg-[#e94c29]'}`}>
        {store.is_open ? 'Tạm đóng cửa' : 'Mở cửa lại'}
      </button>
    </div>
  )
}

function Stat({ label, value, highlight, hint }: { label: string; value: string; highlight?: boolean; hint?: string }) {
  return (
    <div className={`h-full min-w-0 rounded-2xl p-4 shadow-sm sm:p-5 ${highlight ? 'bg-[#ff5b35] text-white' : 'bg-white'}`}>
      <p className={`text-xs sm:text-sm ${highlight ? 'text-white/80' : 'text-[#746b67]'}`}>{label}</p>
      <p className="mt-1 truncate text-xl font-extrabold sm:mt-2 sm:text-2xl">{value}</p>
      {hint && <p className={`mt-0.5 truncate text-[11px] ${highlight ? 'text-white/70' : 'text-[#9c918c]'}`}>{hint}</p>}
    </div>
  )
}

// ---------------------------------------------------------------- orders
const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  pending: 'confirmed', confirmed: 'preparing', preparing: 'picking_up', picking_up: 'delivering', delivering: 'delivered',
}

function OrdersAdmin() {
  const { toast } = useApp()
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [filter, setFilter] = useState<'active' | OrderStatus | 'all'>('active')
  const [open, setOpen] = useState<number | null>(null)

  const load = () => supabase.from('fg_orders').select('*, order_items:fg_order_items(*)').order('created_at', { ascending: false }).limit(200)
    .then(({ data }) => setOrders((data ?? []) as Order[]))
  useEffect(() => {
    load()
    const channel = supabase.channel('admin-orders')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'fg_orders' }, payload => {
        if (payload.eventType === 'INSERT') toast(`Có đơn mới #${(payload.new as Order).code}`)
        load()
      })
      .subscribe()
    return () => { supabase.removeChannel(channel) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const update = async (o: Order, patch: Partial<Order>) => {
    const { error } = await supabase.from('fg_orders').update(patch).eq('id', o.id)
    if (error) return toast(errorMessage(error), 'error')
    setOrders(list => list?.map(x => (x.id === o.id ? { ...x, ...patch } : x)) ?? null)
  }

  // cancelled after paying = the shop still owes a refund, so it stays in the "to do" list
  const needsRefund = (o: Order) => o.status === 'cancelled' && o.payment_status === 'paid'
  const shown = orders?.filter(o => filter === 'all' ? true
    : filter === 'active' ? !['delivered', 'cancelled'].includes(o.status) || needsRefund(o)
    : o.status === filter)

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Đơn hàng</h1>
        <select aria-label="Lọc trạng thái" value={filter} onChange={e => setFilter(e.target.value as typeof filter)} className="h-10 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
          <option value="active">Đang xử lý</option>
          <option value="all">Tất cả</option>
          {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
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
                  <p className="mt-0.5 text-xs text-[#9c918c]">{formatDateTime(o.created_at)}</p>
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

// ---------------------------------------------------------------- catalog
function useLookups() {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [loaded, setLoaded] = useState(false)
  useEffect(() => {
    Promise.all([
      supabase.from('fg_restaurants').select('*').order('name'),
      supabase.from('fg_categories').select('*').order('sort'),
    ]).then(([r, c]) => {
      setRestaurants((r.data ?? []) as Restaurant[])
      setCategories((c.data ?? []) as Category[])
      setLoaded(true)
    })
  }, [])
  return { restaurants, categories, loaded }
}

const thumb = (src: unknown) => src ? <img src={String(src)} alt="" className="size-12 rounded-lg object-cover" /> : <span className="block size-12 rounded-lg bg-[#f8f3f0]" />
const yesNo = (v: unknown, yes: string, no: string) => <span className={`rounded-full px-2 py-1 text-xs font-bold ${v ? 'bg-[#e4f8eb] text-[#3eaa68]' : 'bg-[#f4f0ee] text-[#9c918c]'}`}>{v ? yes : no}</span>

function FoodsAdmin() {
  const { restaurants, categories, loaded } = useLookups()
  if (!loaded) return <Spinner />
  if (!restaurants.length) return <p className="rounded-2xl bg-white p-8 text-center text-sm text-[#746b67] shadow-sm">Chưa có thông tin quán. Chạy supabase/migrations/005_single_store.sql.</p>
  return (
    <EntityManager
      table="fg_foods" title="Món ăn" select="*, categories:fg_categories(name)" orderBy="category_id"
      fields={[
        { key: 'name', label: 'Tên món', type: 'text', required: true },
        // single shop: every dish belongs to it
        { key: 'restaurant_id', label: 'Quán', type: 'hidden', default: restaurants[0].id },
        { key: 'category_id', label: 'Danh mục', type: 'select', options: categories.map(c => ({ value: c.id, label: c.name })) },
        { key: 'price', label: 'Giá bán (đ)', type: 'number', required: true },
        { key: 'old_price', label: 'Giá gốc (đ, để trống nếu không giảm)', type: 'number' },
        { key: 'description', label: 'Mô tả', type: 'textarea' },
        { key: 'image', label: 'Ảnh', type: 'image' },
        { key: 'is_available', label: 'Đang bán', type: 'checkbox' },
        { key: 'is_popular', label: 'Món nổi bật', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Ảnh', render: r => thumb(r.image) },
        { label: 'Tên món', render: r => <b>{String(r.name)}</b> },
        { label: 'Danh mục', render: r => (r.categories as { name: string } | null)?.name ?? '—' },
        { label: 'Giá', render: r => money(Number(r.price)) },
        { label: 'Đã bán', render: r => String(r.sold_count) },
        { label: 'Đánh giá', render: r => Number(r.review_count) > 0 ? `${Number(r.rating).toFixed(1)} ★ (${r.review_count})` : 'Chưa có' },
        { label: 'Trạng thái', render: r => yesNo(r.is_available, 'Đang bán', 'Tạm hết') },
      ]}
    />
  )
}

function RestaurantsAdmin() {
  return (
    <EntityManager
      table="fg_restaurants" title="Thông tin quán" orderBy="id" allowCreate={false} allowDelete={false}
      fields={[
        { key: 'name', label: 'Tên quán', type: 'text', required: true },
        { key: 'cuisine', label: 'Món chính (VD: Cơm • Gà rán • Trà sữa)', type: 'text' },
        { key: 'address', label: 'Địa chỉ', type: 'text', wide: true },
        { key: 'lat', label: 'Vĩ độ (VD: 10.7739) – chuột phải trên Google Maps để lấy', type: 'number' },
        { key: 'lng', label: 'Kinh độ (VD: 106.7009)', type: 'number' },
        { key: 'delivery_time', label: 'Thời gian giao', type: 'text', required: true },
        { key: 'distance_km', label: 'Khoảng cách (km)', type: 'number' },
        { key: 'tag', label: 'Nhãn (Freeship, Giảm 20%...)', type: 'text' },
        { key: 'open_time', label: 'Giờ mở cửa', type: 'time', hint: 'Giờ Việt Nam. Để trống cả hai = mở cả ngày' },
        { key: 'close_time', label: 'Giờ đóng cửa', type: 'time', hint: 'Đóng sau nửa đêm: vd mở 18:00, đóng 02:00' },
        { key: 'image', label: 'Ảnh bìa', type: 'image' },
        { key: 'logo', label: 'Logo', type: 'image' },
        { key: 'is_open', label: 'Đang mở bán (tắt = tạm đóng cửa)', type: 'checkbox' },
        { key: 'is_active', label: 'Hiển thị quán (tắt = ẩn hoàn toàn)', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Ảnh', render: r => thumb(r.image) },
        { label: 'Tên quán', render: r => <b>{String(r.name)}</b> },
        { label: 'Giờ mở cửa', render: r => storeHours(r as unknown as Restaurant).hours ?? 'Cả ngày' },
        { label: 'Đánh giá', render: r => `${r.rating} ★` },
        { label: 'Trạng thái', render: r => yesNo(storeHours(r as unknown as Restaurant).open, 'Đang mở cửa', 'Đã đóng cửa') },
      ]}
    />
  )
}

// ---------------------------------------------------------------- vouchers
const VOUCHER_TYPES = [
  { value: 'amount', label: 'Giảm số tiền (đ)' },
  { value: 'percent', label: 'Giảm theo %' },
  { value: 'freeship', label: 'Miễn phí giao hàng' },
]

const describeVoucher = (r: Record<string, unknown>) => {
  const value = Number(r.discount_value)
  const main = r.discount_type === 'percent' ? `Giảm ${value}%${r.max_discount ? ` (tối đa ${money(Number(r.max_discount))})` : ''}`
    : r.discount_type === 'freeship' ? 'Freeship' : `Giảm ${money(value)}`
  return Number(r.min_subtotal) > 0 ? `${main} • đơn từ ${money(Number(r.min_subtotal))}` : main
}

const fmtDate = (d: unknown) => d ? new Date(`${String(d).slice(0, 10)}T00:00:00`).toLocaleDateString('vi-VN') : null

function VouchersAdmin() {
  return (
    <EntityManager
      table="fg_vouchers" title="Mã giảm giá" select="*, fg_used_count" orderBy="created_at" searchKey="code"
      fields={[
        { key: 'code', label: 'Mã (VD: GIAM20K)', type: 'text', required: true, hint: 'Tự chuyển thành chữ HOA, bỏ khoảng trắng' },
        { key: 'discount_type', label: 'Loại giảm giá', type: 'select', required: true, options: VOUCHER_TYPES },
        { key: 'discount_value', label: 'Mức giảm (số tiền hoặc %)', type: 'number', required: true, default: 0, hint: 'Freeship: nhập 0' },
        { key: 'max_discount', label: 'Giảm tối đa (đ, cho mã %)', type: 'number' },
        { key: 'min_subtotal', label: 'Đơn tối thiểu (đ, tiền món)', type: 'number', required: true, default: 0 },
        { key: 'usage_limit', label: 'Tổng lượt dùng (trống = không giới hạn)', type: 'number' },
        { key: 'per_user_limit', label: 'Lượt dùng mỗi khách', type: 'number', default: 1, hint: 'Trống = không giới hạn' },
        { key: 'starts_on', label: 'Áp dụng từ ngày', type: 'date' },
        { key: 'expires_on', label: 'Hết hạn sau ngày', type: 'date' },
        { key: 'description', label: 'Mô tả cho khách (VD: Giảm 20k cho đơn từ 80k)', type: 'text', wide: true },
        { key: 'is_active', label: 'Đang bật', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Biểu tượng', render: () => <span className="grid size-12 place-items-center rounded-lg bg-[#fff0eb] text-[#ff5b35]"><TicketPercent className="size-5" /></span> },
        { label: 'Mã', render: r => <b className="font-mono">{String(r.code)}</b> },
        { label: 'Ưu đãi', render: r => describeVoucher(r) },
        { label: 'Đã dùng', render: r => `${Number(r.fg_used_count ?? 0)}${r.usage_limit ? ` / ${r.usage_limit}` : ''}` },
        { label: 'Hạn dùng', render: r => [fmtDate(r.starts_on), fmtDate(r.expires_on)].some(Boolean) ? `${fmtDate(r.starts_on) ?? '…'} → ${fmtDate(r.expires_on) ?? '…'}` : 'Không thời hạn' },
        { label: 'Trạng thái', render: r => yesNo(r.is_active && !(r.expires_on && String(r.expires_on) < new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })), 'Đang bật', r.is_active ? 'Hết hạn' : 'Đã tắt') },
      ]}
    />
  )
}

function CategoriesAdmin() {
  return (
    <EntityManager
      table="fg_categories" title="Danh mục" orderBy="sort"
      fields={[
        { key: 'name', label: 'Tên danh mục', type: 'text', required: true },
        { key: 'sort', label: 'Thứ tự', type: 'number', required: true },
        { key: 'image', label: 'Ảnh', type: 'image' },
      ]}
      columns={[
        { label: 'Ảnh', render: r => thumb(r.image) },
        { label: 'Tên', render: r => <b>{String(r.name)}</b> },
        { label: 'Thứ tự', render: r => String(r.sort) },
      ]}
    />
  )
}

// ---------------------------------------------------------------- users
// ---------------------------------------------------------------- reviews
type ReviewRow = { id: number; reviewer_name: string; rating: number; comment: string | null; updated_at: string; order_id: number; food: { name: string; image: string | null } | null }

function ReviewsAdmin() {
  const { toast } = useApp()
  const [reviews, setReviews] = useState<ReviewRow[] | null>(null)
  const [stars, setStars] = useState<number | 'all'>('all')

  const load = () => supabase.from('fg_reviews').select('id, reviewer_name, rating, comment, updated_at, order_id, food:fg_foods(name, image)')
    .order('updated_at', { ascending: false }).limit(200)
    .then(({ data }) => setReviews((data ?? []) as unknown as ReviewRow[]))
  useEffect(() => { load() }, [])

  // removing a review also recalculates the dish/shop rating (DB trigger)
  const remove = async (r: ReviewRow) => {
    if (!window.confirm(`Xóa đánh giá của ${r.reviewer_name}? Điểm của món sẽ được tính lại.`)) return
    const { error } = await supabase.from('fg_reviews').delete().eq('id', r.id)
    if (error) return toast(errorMessage(error), 'error')
    setReviews(list => list?.filter(x => x.id !== r.id) ?? null)
  }

  const shown = reviews?.filter(r => stars === 'all' || r.rating === stars)
  const avg = reviews?.length ? reviews.reduce((s, r) => s + r.rating, 0) / reviews.length : 0

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Đánh giá</h1>
          {!!reviews?.length && <p className="mt-1 text-sm text-[#746b67]">Trung bình <b className="text-[#241c19]">{avg.toFixed(1)} ★</b> từ {reviews.length} đánh giá</p>}
        </div>
        <select aria-label="Lọc theo số sao" value={stars} onChange={e => setStars(e.target.value === 'all' ? 'all' : Number(e.target.value))} className="h-10 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
          <option value="all">Tất cả</option>
          {[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} sao</option>)}
        </select>
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {shown === undefined && <Spinner />}
        {shown?.length === 0 && <p className="rounded-2xl bg-white py-12 text-center text-sm text-[#9c918c] shadow-sm">Chưa có đánh giá nào</p>}
        {shown?.map(r => (
          <div key={r.id} className="flex gap-3 rounded-2xl bg-white p-4 shadow-sm">
            {r.food?.image && <img src={r.food.image} alt="" className="size-12 shrink-0 rounded-lg object-cover" />}
            <div className="min-w-0 flex-1 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-x-3">
                <b className="truncate">{r.food?.name ?? 'Món đã xóa'}</b>
                <span className="text-xs text-[#9c918c]">{formatDateTime(r.updated_at)}</span>
              </div>
              <div className="mt-1 flex items-center gap-2"><Stars value={r.rating} size="size-3.5" /><span className="text-xs text-[#746b67]">{r.reviewer_name}</span></div>
              {r.comment && <p className="mt-1.5 text-[#4a403c]">{r.comment}</p>}
            </div>
            <button aria-label="Xóa đánh giá" onClick={() => remove(r)} className="grid size-9 shrink-0 place-items-center self-start rounded-lg text-[#746b67] hover:bg-red-50 hover:text-red-500"><Trash2 className="size-4" /></button>
          </div>
        ))}
      </div>
    </div>
  )
}

function UsersAdmin() {
  const { user, toast } = useApp()
  const [users, setUsers] = useState<(Profile & { created_at: string })[] | null>(null)
  const load = () => supabase.from('fg_profiles').select('*').order('created_at', { ascending: false })
    .then(({ data }) => setUsers((data ?? []) as (Profile & { created_at: string })[]))
  useEffect(() => { load() }, [])

  const setRole = async (p: Profile, role: Profile['role']) => {
    if (!window.confirm(role === 'admin' ? `Cấp quyền admin cho ${p.full_name}?` : `Gỡ quyền admin của ${p.full_name}?`)) return
    const { error } = await supabase.from('fg_profiles').update({ role }).eq('id', p.id)
    if (error) return toast(errorMessage(error), 'error')
    load()
  }

  return (
    <div>
      <h1 className="text-2xl font-extrabold">Người dùng</h1>
      {/* phones: cards instead of a wide table */}
      <div className="mt-4 flex flex-col gap-3 sm:hidden">
        {users === null && <Spinner />}
        {users?.map(p => (
          <div key={p.id} className="flex items-center gap-3 rounded-2xl bg-white p-4 shadow-sm">
            <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[#ffe0d5] text-sm font-bold text-[#ff5b35]">
              {(p.full_name || '?').split(' ').map(w => w[0]).slice(-2).join('').toUpperCase()}
            </span>
            <div className="min-w-0 flex-1 text-sm">
              <b className="block truncate">{p.full_name || '—'}</b>
              <p className="truncate text-xs text-[#9c918c]">{p.phone || 'Chưa có SĐT'} • {formatDateTime(p.created_at)}</p>
              <div className="mt-1">{yesNo(p.role === 'admin', 'Admin', 'Khách hàng')}</div>
            </div>
            {p.id !== user?.id && (
              <button onClick={() => setRole(p, p.role === 'admin' ? 'customer' : 'admin')} className="shrink-0 rounded-lg border border-[#eaded8] px-3 py-2 text-xs font-bold text-[#ff5b35]">
                {p.role === 'admin' ? 'Gỡ admin' : 'Cấp admin'}
              </button>
            )}
          </div>
        ))}
      </div>
      <div className="mt-5 hidden overflow-x-auto rounded-2xl bg-white shadow-sm sm:block">
        <table className="w-full min-w-[560px] text-left text-sm">
          <thead className="border-b border-[#f1e7e2] text-xs uppercase text-[#9c918c]">
            <tr><th className="px-4 py-3">Tên</th><th className="px-4 py-3">Điện thoại</th><th className="px-4 py-3">Ngày tạo</th><th className="px-4 py-3">Vai trò</th><th /></tr>
          </thead>
          <tbody>
            {users === null && <tr><td colSpan={5} className="py-10 text-center text-[#9c918c]">Đang tải...</td></tr>}
            {users?.map(p => (
              <tr key={p.id} className="border-b border-[#f8f3f0] last:border-0">
                <td className="px-4 py-3"><b>{p.full_name || '—'}</b></td>
                <td className="px-4 py-3">{p.phone || '—'}</td>
                <td className="px-4 py-3">{formatDateTime(p.created_at)}</td>
                <td className="px-4 py-3">{yesNo(p.role === 'admin', 'Admin', 'Khách hàng')}</td>
                <td className="px-4 py-3 text-right">
                  {p.id !== user?.id && (
                    <button onClick={() => setRole(p, p.role === 'admin' ? 'customer' : 'admin')} className="py-2 text-xs font-bold text-[#ff5b35]">
                      {p.role === 'admin' ? 'Gỡ admin' : 'Cấp admin'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
