'use client'

import Link from 'next/link'
import { Suspense, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ArrowLeft, BarChart3, Grid2x2, LayoutDashboard, LogOut, Package, ScrollText, Star, Store, TicketPercent, Trash2, Users, UtensilsCrossed, Wallet } from 'lucide-react'
import { StatsAdmin } from '@/components/admin/stats'
import { AuditLog } from '@/components/admin/audit-log'
import { OrdersManager } from '@/components/admin/orders-manager'
import { SubscriptionsAdmin, GrantRestaurantButton, SubscriptionSummary } from '@/components/admin/subscriptions'
import { FoodsManager, VouchersManager, restaurantFields, restaurantStatus, thumb, yesNo } from '@/components/admin/catalog'
import { fetchRestaurants, storeHours, useClock } from '@/lib/store'
import { subscriptionOf } from '@/lib/shop'
import { Stars } from '@/components/reviews'
import { RequireAuth } from '@/components/require-auth'
import { useIsNativeApp } from '@/lib/native'
import { Logo } from '@/components/site-shell'
import { Spinner } from '@/components/cards'
import { EntityManager } from '@/components/admin/entity-manager'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money } from '@/lib/format'
import type { Order, Profile, Restaurant } from '@/lib/types'

const TABS = [
  { id: 'overview', label: 'Tổng quan', icon: LayoutDashboard },
  { id: 'subscriptions', label: 'Phí duy trì', icon: Wallet },
  { id: 'stats', label: 'Thống kê đơn', icon: BarChart3 },
  { id: 'orders', label: 'Đơn hàng', icon: Package },
  { id: 'foods', label: 'Món ăn', icon: UtensilsCrossed },
  { id: 'vouchers', label: 'Mã giảm giá', icon: TicketPercent },
  { id: 'restaurants', label: 'Nhà hàng', icon: Store },
  { id: 'reviews', label: 'Đánh giá', icon: Star },
  { id: 'categories', label: 'Danh mục', icon: Grid2x2 },
  { id: 'users', label: 'Người dùng', icon: Users },
  { id: 'audit', label: 'Nhật ký bảo mật', icon: ScrollText },
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
        {tab === 'stats' && <StatsAdmin title="Thống kê đơn hàng" />}
        {tab === 'subscriptions' && <SubscriptionsAdmin />}
        {tab === 'vouchers' && <VouchersManager />}
        {tab === 'orders' && <OrdersManager />}
        {tab === 'foods' && <FoodsManager />}
        {tab === 'restaurants' && <RestaurantsAdmin />}
        {tab === 'categories' && <CategoriesAdmin />}
        {tab === 'users' && <UsersAdmin />}
        {tab === 'reviews' && <ReviewsAdmin />}
        {tab === 'audit' && <AuditLog />}
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
      <h1 className="text-2xl font-extrabold">Tổng quan</h1>
      <SubscriptionSummary />
      <h2 className="mt-6 font-extrabold sm:mt-8">Đơn hàng toàn hệ thống</h2>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <Stat label="Doanh số hôm nay" value={money(stats.todayRevenue)} hint={`${stats.deliveredTodayCount} đơn đã giao`} />
        <Stat label="Đơn đặt hôm nay" value={String(stats.todayOrders)} />
        <Stat label="Doanh số 30 ngày" value={money(stats.monthRevenue)} hint="Tiền món các nhà hàng, đơn đã giao" />
        <Stat label="Đơn chờ xác nhận" value={String(stats.pending)} highlight={stats.pending > 0} />
      </div>
      <div className="mt-4 grid gap-4 sm:mt-6 sm:gap-6 xl:grid-cols-[1fr_320px]">
        <section className="rounded-2xl bg-white p-4 shadow-sm sm:p-5">
          <h2 className="font-extrabold">Doanh số đơn hàng 7 ngày gần nhất</h2>
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
      <RestaurantSwitches />
    </div>
  )
}

/** One-tap "tạm đóng cửa" per restaurant for busy moments; regular hours are set under "Nhà hàng". */
function RestaurantSwitches() {
  const { toast } = useApp()
  const [list, setList] = useState<Restaurant[] | null>(null)
  const [busy, setBusy] = useState<number | null>(null)
  useClock()
  useEffect(() => { fetchRestaurants(true).then(setList) }, [])
  if (!list?.length) return null

  const toggle = async (r: Restaurant) => {
    const next = !r.is_open
    if (!next && !window.confirm(`Tạm đóng cửa ${r.name}? Khách sẽ không đặt được đơn mới cho tới khi bạn mở lại.`)) return
    setBusy(r.id)
    const { error } = await supabase.from('fg_restaurants').update({ is_open: next }).eq('id', r.id)
    setBusy(null)
    if (error) return toast(errorMessage(error), 'error')
    setList(l => l?.map(x => (x.id === r.id ? { ...x, is_open: next } : x)) ?? null)
    fetchRestaurants(true)
    toast(next ? `${r.name} đã mở cửa nhận đơn` : `${r.name} đã tạm đóng cửa`)
  }

  const openCount = list.filter(r => r.is_active && storeHours(r).open).length
  return (
    <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm sm:mt-6 sm:p-5">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="font-extrabold">Nhà hàng</h2>
        <span className="text-xs text-[#746b67]"><b className="text-[#241c19]">{openCount}</b> / {list.length} đang mở cửa</span>
      </div>
      <ul className="mt-3 divide-y divide-[#f8f3f0]">
        {list.map(r => {
          const h = storeHours(r)
          const status = !r.is_active ? 'Đang ẩn' : h.open ? 'Đang mở cửa' : h.paused ? 'Tạm đóng cửa' : `Ngoài giờ (mở ${h.reopens})`
          return (
            <li key={r.id} className="flex items-center gap-3 py-2.5">
              <span className={`size-2.5 shrink-0 rounded-full ${r.is_active && h.open ? 'bg-[#3eaa68]' : 'bg-[#c9bdb7]'}`} />
              <div className="min-w-0 flex-1 text-sm">
                <b className="block truncate">{r.name}</b>
                <span className="text-xs text-[#746b67]">{status}{h.hours && ` • ${h.hours}`}</span>
              </div>
              <button onClick={() => toggle(r)} disabled={busy === r.id || !r.is_active}
                className={`h-9 shrink-0 rounded-lg px-3 text-xs font-bold disabled:opacity-40 ${r.is_open ? 'border border-[#eaded8] text-red-500 hover:bg-red-50' : 'bg-[#ff5b35] text-white hover:bg-[#e94c29]'}`}>
                {r.is_open ? 'Tạm đóng' : 'Mở lại'}
              </button>
            </li>
          )
        })}
      </ul>
    </section>
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

// ---------------------------------------------------------------- restaurants
function RestaurantsAdmin() {
  return (
    <EntityManager
      table="fg_restaurants" title="Nhà hàng" orderBy="id"
      deleteWarning="Toàn bộ món ăn của nhà hàng này cũng bị xóa (đơn hàng cũ vẫn được giữ). Muốn ngừng tạm thời thì nên tắt “Hiển thị nhà hàng”."
      onChanged={() => fetchRestaurants(true)}
      fields={restaurantFields(true)}
      columns={[
        { label: 'Ảnh', render: r => thumb(r.image) },
        { label: 'Tên nhà hàng', render: r => <b>{String(r.name)}</b> },
        { label: 'Địa chỉ', render: r => <span className="line-clamp-2 max-w-xs text-xs">{r.lat == null ? <span className="font-bold text-[#c2410c]">Chưa ghim vị trí • </span> : null}{String(r.address ?? '')}</span> },
        { label: 'Giờ mở cửa', render: r => storeHours(r as unknown as Restaurant).hours ?? 'Cả ngày' },
        { label: 'Phí duy trì', render: r => {
          const s = subscriptionOf(r as unknown as Restaurant)
          return !r.owner_id ? <span className="text-xs text-[#9c918c]">Admin quản lý</span>
            : s.active ? <span className="text-xs">Còn {s.daysLeft} ngày</span>
            : <span className="text-xs font-bold text-[#c2410c]">Hết hạn</span>
        } },
        { label: 'Trạng thái', render: r => restaurantStatus(r) },
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
  // user id → the restaurant they own
  const [owned, setOwned] = useState<Record<string, string>>({})
  const load = () => {
    supabase.from('fg_profiles').select('*').order('created_at', { ascending: false })
      .then(({ data }) => setUsers((data ?? []) as (Profile & { created_at: string })[]))
    supabase.from('fg_restaurants').select('name, owner_id').not('owner_id', 'is', null)
      .then(({ data }) => setOwned(Object.fromEntries((data ?? []).map(r => [r.owner_id as string, r.name as string]))))
  }
  useEffect(() => { load() }, [])
  const role = (p: Profile) => p.role === 'admin' ? yesNo(true, 'Admin', '')
    : owned[p.id] ? <span className="rounded-full bg-[#fff0eb] px-2 py-1 text-xs font-bold text-[#ff5b35]">Chủ: {owned[p.id]}</span>
    : yesNo(false, '', 'Khách hàng')

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
              <div className="mt-1">{role(p)}</div>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1">
              <GrantRestaurantButton user={p} ownedName={owned[p.id]} onDone={load} />
              {p.id !== user?.id && (
                <button onClick={() => setRole(p, p.role === 'admin' ? 'customer' : 'admin')} className="rounded-lg border border-[#eaded8] px-3 py-2 text-xs font-bold text-[#ff5b35]">
                  {p.role === 'admin' ? 'Gỡ admin' : 'Cấp admin'}
                </button>
              )}
            </div>
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
                <td className="px-4 py-3">{role(p)}</td>
                <td className="flex items-center justify-end gap-4 px-4 py-3">
                  <GrantRestaurantButton user={p} ownedName={owned[p.id]} onDone={load} />
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