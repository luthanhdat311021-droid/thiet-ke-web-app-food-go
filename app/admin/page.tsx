'use client'

import Link from 'next/link'
import { Suspense, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ArrowLeft, BarChart3, Grid2x2, LogOut, Package, Store, Users, UtensilsCrossed } from 'lucide-react'
import { RequireAuth } from '@/components/require-auth'
import { Logo } from '@/components/site-shell'
import { Spinner } from '@/components/cards'
import { EntityManager } from '@/components/admin/entity-manager'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money, ORDER_STEPS, STATUS_LABEL, STATUS_STYLE } from '@/lib/format'
import type { Category, Order, OrderStatus, Profile, Restaurant } from '@/lib/types'

const TABS = [
  { id: 'overview', label: 'Tổng quan', icon: BarChart3 },
  { id: 'orders', label: 'Đơn hàng', icon: Package },
  { id: 'foods', label: 'Món ăn', icon: UtensilsCrossed },
  { id: 'restaurants', label: 'Nhà hàng', icon: Store },
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
  const tab = (TABS.some(t => t.id === params.get('tab')) ? params.get('tab') : 'overview') as Tab

  return (
    <div className="lg:flex">
      <aside className="sticky top-0 z-20 flex items-center gap-2 overflow-x-auto border-b border-[#f1e7e2] bg-white px-4 py-3 lg:h-screen lg:w-64 lg:shrink-0 lg:flex-col lg:items-stretch lg:border-b-0 lg:border-r lg:p-5">
        <div className="hidden lg:mb-6 lg:block"><Logo /><p className="mt-2 text-xs font-bold text-[#9c918c]">TRANG QUẢN TRỊ</p></div>
        {TABS.map(({ id, label, icon: Icon }) => (
          <button key={id} onClick={() => router.replace(`${pathname}?tab=${id}`)}
            className={`flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm ${tab === id ? 'bg-[#fff0eb] font-bold text-[#ff5b35]' : 'text-[#746b67] hover:bg-[#fffaf7]'}`}>
            <Icon className="size-4" />{label}
          </button>
        ))}
        <div className="lg:mt-auto" />
        <Link href="/" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-[#746b67] hover:bg-[#fffaf7]"><ArrowLeft className="size-4" />Về trang web</Link>
        <button onClick={signOut} className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-red-500 hover:bg-red-50"><LogOut className="size-4" />Đăng xuất</button>
      </aside>
      <main className="min-w-0 flex-1 p-5 lg:p-10">
        {tab === 'overview' && <Overview />}
        {tab === 'orders' && <OrdersAdmin />}
        {tab === 'foods' && <FoodsAdmin />}
        {tab === 'restaurants' && <RestaurantsAdmin />}
        {tab === 'categories' && <CategoriesAdmin />}
        {tab === 'users' && <UsersAdmin />}
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
    supabase.from('fg_orders').select('*').gte('created_at', since).order('created_at', { ascending: false })
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
    const valid = orders.filter(o => o.status !== 'cancelled')
    const todays = valid.filter(o => new Date(o.created_at).toDateString() === today)
    const days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(Date.now() - (6 - i) * 86400000)
      const list = valid.filter(o => new Date(o.created_at).toDateString() === d.toDateString())
      return { label: d.toLocaleDateString('vi-VN', { weekday: 'short' }), revenue: list.reduce((s, o) => s + o.total, 0) }
    })
    return {
      todayRevenue: todays.reduce((s, o) => s + o.total, 0),
      todayOrders: todays.length,
      monthRevenue: valid.reduce((s, o) => s + o.total, 0),
      pending: orders.filter(o => o.status === 'pending').length,
      days,
    }
  }, [orders])

  if (!stats) return <Spinner />
  const max = Math.max(1, ...stats.days.map(d => d.revenue))
  return (
    <div>
      <h1 className="text-2xl font-extrabold">Tổng quan</h1>
      <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Doanh thu hôm nay" value={money(stats.todayRevenue)} />
        <Stat label="Đơn hôm nay" value={String(stats.todayOrders)} />
        <Stat label="Doanh thu 30 ngày" value={money(stats.monthRevenue)} />
        <Stat label="Đơn chờ xác nhận" value={String(stats.pending)} highlight={stats.pending > 0} />
      </div>
      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_320px]">
        <section className="rounded-2xl bg-white p-5 shadow-sm">
          <h2 className="font-extrabold">Doanh thu 7 ngày gần nhất</h2>
          <div className="mt-6 flex h-48 items-end gap-3">
            {stats.days.map(d => (
              <div key={d.label} className="flex h-full flex-1 flex-col items-center justify-end gap-2">
                <span className="text-[11px] text-[#9c918c]">{d.revenue ? `${Math.round(d.revenue / 1000)}k` : ''}</span>
                <div className="w-full max-w-12 rounded-t-lg bg-[#ff5b35]" style={{ height: `${Math.max(2, (d.revenue / max) * 100)}%` }} title={money(d.revenue)} />
                <span className="text-xs text-[#746b67]">{d.label}</span>
              </div>
            ))}
          </div>
        </section>
        <section className="grid gap-4">
          <Stat label="Món ăn" value={String(counts.foods)} />
          <Stat label="Nhà hàng" value={String(counts.restaurants)} />
          <Stat label="Người dùng" value={String(counts.users)} />
        </section>
      </div>
    </div>
  )
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className={`rounded-2xl p-5 shadow-sm ${highlight ? 'bg-[#ff5b35] text-white' : 'bg-white'}`}>
      <p className={`text-sm ${highlight ? 'text-white/80' : 'text-[#746b67]'}`}>{label}</p>
      <p className="mt-2 text-2xl font-extrabold">{value}</p>
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

  const shown = orders?.filter(o => filter === 'all' ? true : filter === 'active' ? !['delivered', 'cancelled'].includes(o.status) : o.status === filter)

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
              <div className="flex flex-wrap items-center gap-3">
                <button onClick={() => setOpen(open === o.id ? null : o.id)} className="min-w-0 flex-1 text-left">
                  <b>#{o.code}</b> <span className="text-sm text-[#746b67]">• {o.restaurant_name}</span>
                  <p className="text-xs text-[#9c918c]">{formatDateTime(o.created_at)} • {o.recipient} • {o.phone}</p>
                </button>
                <b className="text-[#ff5b35]">{money(o.total)}</b>
                <span className={`rounded-full px-3 py-1 text-xs font-bold ${o.payment_status === 'paid' ? 'bg-[#e4f8eb] text-[#3eaa68]' : 'bg-[#fff7df] text-[#bd8300]'}`}>
                  {o.payment_method === 'qr' ? 'QR' : 'COD'} • {o.payment_status === 'paid' ? 'Đã TT' : 'Chưa TT'}
                </span>
                <span className={`rounded-full px-3 py-1 text-xs font-bold ${STATUS_STYLE[o.status]}`}>{STATUS_LABEL[o.status]}</span>
              </div>
              {open === o.id && (
                <div className="mt-4 grid gap-4 border-t border-[#f1e7e2] pt-4 text-sm md:grid-cols-2">
                  <div>
                    {(o.order_items ?? []).map(i => <p key={i.id}>{i.qty} × {i.name} <span className="text-[#9c918c]">({money(i.price)})</span></p>)}
                    <p className="mt-2 text-[#746b67]">Phí ship: {money(o.shipping_fee)}</p>
                  </div>
                  <div className="text-[#746b67]"><p>{o.address}</p>{o.note && <p className="mt-1"><b className="text-[#241c19]">Ghi chú:</b> {o.note}</p>}</div>
                </div>
              )}
              <div className="mt-3 flex flex-wrap gap-2">
                {next && <button onClick={() => update(o, { status: next })} className="h-9 rounded-lg bg-[#ff5b35] px-3 text-xs font-bold text-white hover:bg-[#e94c29]">→ {ORDER_STEPS.find(s => s.status === next)?.label}</button>}
                {o.payment_status !== 'paid' && o.status !== 'cancelled' && (
                  <button onClick={() => update(o, { payment_status: 'paid' })} className="h-9 rounded-lg border border-[#3eaa68] px-3 text-xs font-bold text-[#3eaa68] hover:bg-[#e4f8eb]">Xác nhận đã thanh toán</button>
                )}
                {!['delivered', 'cancelled'].includes(o.status) && (
                  <button onClick={() => window.confirm(`Hủy đơn #${o.code}?`) && update(o, { status: 'cancelled' })} className="h-9 rounded-lg border border-[#eaded8] px-3 text-xs font-bold text-red-500 hover:bg-red-50">Hủy đơn</button>
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
  if (!restaurants.length) return <p className="rounded-2xl bg-white p-8 text-center text-sm text-[#746b67] shadow-sm">Hãy tạo ít nhất một nhà hàng trước khi thêm món ăn.</p>
  return (
    <EntityManager
      table="fg_foods" title="Món ăn" select="*, restaurants:fg_restaurants(name), categories:fg_categories(name)" orderBy="restaurant_id"
      fields={[
        { key: 'name', label: 'Tên món', type: 'text', required: true },
        { key: 'restaurant_id', label: 'Nhà hàng', type: 'select', required: true, options: restaurants.map(r => ({ value: r.id, label: r.name })) },
        { key: 'category_id', label: 'Danh mục', type: 'select', options: categories.map(c => ({ value: c.id, label: c.name })) },
        { key: 'price', label: 'Giá bán (đ)', type: 'number', required: true },
        { key: 'old_price', label: 'Giá gốc (đ, để trống nếu không giảm)', type: 'number' },
        { key: 'rating', label: 'Đánh giá (0-5)', type: 'number' },
        { key: 'description', label: 'Mô tả', type: 'textarea' },
        { key: 'image', label: 'Ảnh', type: 'image' },
        { key: 'is_available', label: 'Đang bán', type: 'checkbox' },
        { key: 'is_popular', label: 'Món nổi bật', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Ảnh', render: r => thumb(r.image) },
        { label: 'Tên món', render: r => <b>{String(r.name)}</b> },
        { label: 'Nhà hàng', render: r => (r.restaurants as { name: string } | null)?.name },
        { label: 'Danh mục', render: r => (r.categories as { name: string } | null)?.name ?? '—' },
        { label: 'Giá', render: r => money(Number(r.price)) },
        { label: 'Đã bán', render: r => String(r.sold_count) },
        { label: 'Trạng thái', render: r => yesNo(r.is_available, 'Đang bán', 'Tạm hết') },
      ]}
    />
  )
}

function RestaurantsAdmin() {
  return (
    <EntityManager
      table="fg_restaurants" title="Nhà hàng" orderBy="name"
      fields={[
        { key: 'name', label: 'Tên nhà hàng', type: 'text', required: true },
        { key: 'cuisine', label: 'Loại món (VD: Cơm Việt • Món gia đình)', type: 'text' },
        { key: 'address', label: 'Địa chỉ', type: 'text', wide: true },
        { key: 'delivery_time', label: 'Thời gian giao', type: 'text', required: true },
        { key: 'distance_km', label: 'Khoảng cách (km)', type: 'number' },
        { key: 'rating', label: 'Đánh giá (0-5)', type: 'number' },
        { key: 'review_count', label: 'Số đánh giá', type: 'number' },
        { key: 'tag', label: 'Nhãn (Freeship, Giảm 20%...)', type: 'text' },
        { key: 'image', label: 'Ảnh bìa', type: 'image' },
        { key: 'logo', label: 'Logo', type: 'image' },
        { key: 'is_active', label: 'Đang hoạt động', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Logo', render: r => thumb(r.logo) },
        { label: 'Tên', render: r => <b>{String(r.name)}</b> },
        { label: 'Loại món', render: r => String(r.cuisine ?? '') },
        { label: 'Đánh giá', render: r => `${r.rating} ★` },
        { label: 'Trạng thái', render: r => yesNo(r.is_active, 'Hoạt động', 'Tạm ngưng') },
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
      <div className="mt-5 overflow-x-auto rounded-2xl bg-white shadow-sm">
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
