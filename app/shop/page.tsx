'use client'

import Link from 'next/link'
import { Suspense, useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ArrowLeft, BarChart3, ExternalLink, LayoutDashboard, Loader2, Package, Store, TicketPercent, UtensilsCrossed } from 'lucide-react'
import { RequireAuth } from '@/components/require-auth'
import { Logo } from '@/components/site-shell'
import { Spinner } from '@/components/cards'
import { useApp } from '@/components/app-provider'
import { EntityManager } from '@/components/admin/entity-manager'
import { FoodsManager, VouchersManager, restaurantFields, restaurantStatus, thumb } from '@/components/admin/catalog'
import { OrdersManager } from '@/components/admin/orders-manager'
import { StatsAdmin } from '@/components/admin/stats'
import { SubscriptionPanel } from '@/components/shop/subscription-panel'
import { RegisterRestaurantForm } from '@/components/shop/register-form'
import { errorMessage, supabase } from '@/lib/supabase'
import { fetchRestaurants, storeHours, useClock } from '@/lib/store'
import { subscriptionOf, useMyRestaurant } from '@/lib/shop'
import { money } from '@/lib/format'
import type { Restaurant } from '@/lib/types'

const TABS = [
  { id: 'overview', label: 'Tổng quan', icon: LayoutDashboard },
  { id: 'orders', label: 'Đơn hàng', icon: Package },
  { id: 'foods', label: 'Món ăn', icon: UtensilsCrossed },
  { id: 'vouchers', label: 'Mã giảm giá', icon: TicketPercent },
  { id: 'stats', label: 'Doanh thu', icon: BarChart3 },
  { id: 'info', label: 'Thông tin quán', icon: Store },
] as const
type Tab = (typeof TABS)[number]['id']

export default function ShopPage() {
  return (
    <div className="min-h-screen bg-[#fffaf7] text-[#241c19]">
      <RequireAuth secure><Suspense fallback={<Spinner />}><Shop /></Suspense></RequireAuth>
    </div>
  )
}

function Shop() {
  const { user } = useApp()
  const { restaurant, reload } = useMyRestaurant(user?.id)
  if (restaurant === undefined) return <Spinner />
  if (restaurant === null) {
    return (
      <>
        <header className="flex items-center justify-between border-b border-[#f1e7e2] bg-white px-5 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))]">
          <Logo />
          <Link href="/" className="text-sm font-bold text-[#ff5b35]">← Về trang chủ</Link>
        </header>
        <RegisterRestaurantForm onRegistered={reload} />
      </>
    )
  }
  return <Dashboard restaurant={restaurant} reload={reload} />
}

function Dashboard({ restaurant, reload }: { restaurant: Restaurant; reload: () => void }) {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const tab = (TABS.some(t => t.id === params.get('tab')) ? params.get('tab') : 'overview') as Tab
  const sub = subscriptionOf(restaurant)

  return (
    <div className="lg:flex">
      <aside className="sticky top-0 z-20 flex items-center gap-1 overflow-x-auto border-b border-[#f1e7e2] bg-white px-3 pb-2 pt-[max(0.5rem,env(safe-area-inset-top))] [scrollbar-width:none] lg:h-screen lg:w-64 lg:shrink-0 lg:flex-col lg:items-stretch lg:gap-2 lg:border-b-0 lg:border-r lg:p-5 [&::-webkit-scrollbar]:hidden">
        <div className="hidden lg:mb-6 lg:block">
          <Logo />
          <p className="mt-2 text-xs font-bold text-[#9c918c]">KÊNH NHÀ HÀNG</p>
          <p className="mt-1 truncate text-sm font-extrabold">{restaurant.name}</p>
        </div>
        {TABS.map(({ id, label, icon: Icon }) => (
          <button key={id} onClick={() => router.replace(`${pathname}?tab=${id}`)}
            className={`flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm ${tab === id ? 'bg-[#fff0eb] font-bold text-[#ff5b35]' : 'text-[#746b67] hover:bg-[#fffaf7]'}`}>
            <Icon className="size-4" />{label}
          </button>
        ))}
        <div className="lg:mt-auto" />
        <Link href={`/restaurant/${restaurant.id}`} className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-[#746b67] hover:bg-[#fffaf7]"><ExternalLink className="size-4" />Xem trang quán</Link>
        <Link href="/" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-[#746b67] hover:bg-[#fffaf7]"><ArrowLeft className="size-4" />Về trang chủ</Link>
      </aside>
      <main className="min-w-0 flex-1 p-4 pb-[max(1.5rem,env(safe-area-inset-bottom))] sm:p-5 lg:p-10">
        {!sub.active && tab !== 'overview' && (
          <button onClick={() => router.replace(`${pathname}?tab=overview`)} className="mb-4 block w-full rounded-xl bg-red-50 px-4 py-3 text-left text-sm text-red-700">
            Nhà hàng {sub.until ? 'đã hết hạn' : 'chưa kích hoạt'} nên khách chưa thấy. <b className="underline">Thanh toán phí duy trì</b>
          </button>
        )}
        {tab === 'overview' && <Overview restaurant={restaurant} reload={reload} />}
        {tab === 'orders' && <OrdersManager restaurantId={restaurant.id} />}
        {tab === 'foods' && <FoodsManager restaurantId={restaurant.id} />}
        {tab === 'vouchers' && (
          <>
            <p className="mb-4 text-sm text-[#746b67]">Mã do bạn tạo chỉ dùng được cho đơn của {restaurant.name}. Mã bị trừ vào doanh thu của quán.</p>
            <VouchersManager restaurantId={restaurant.id} />
          </>
        )}
        {tab === 'stats' && <StatsAdmin restaurantId={restaurant.id} title="Doanh thu" />}
        {tab === 'info' && (
          <EntityManager
            table="fg_restaurants" title="Thông tin quán" match={{ id: restaurant.id }} allowCreate={false} allowDelete={false}
            onChanged={() => { reload(); fetchRestaurants(true) }}
            fields={restaurantFields(false)}
            columns={[
              { label: 'Ảnh', render: r => thumb(r.image) },
              { label: 'Tên nhà hàng', render: r => <b>{String(r.name)}</b> },
              { label: 'Địa chỉ', render: r => <span className="line-clamp-2 max-w-xs text-xs">{r.lat == null ? <span className="font-bold text-[#c2410c]">Chưa ghim vị trí • </span> : null}{String(r.address ?? '')}</span> },
              { label: 'Giờ mở cửa', render: r => storeHours(r as unknown as Restaurant).hours ?? 'Cả ngày' },
              { label: 'Trạng thái', render: r => restaurantStatus(r) },
            ]}
          />
        )}
      </main>
    </div>
  )
}

function Overview({ restaurant, reload }: { restaurant: Restaurant; reload: () => void }) {
  const { toast } = useApp()
  const [today, setToday] = useState<{ revenue: number; delivered: number; pending: number } | null>(null)
  const [busy, setBusy] = useState(false)
  useClock()
  const hours = storeHours(restaurant)

  useEffect(() => {
    const start = new Date(); start.setHours(0, 0, 0, 0)
    supabase.from('fg_orders').select('status, total, delivered_at, created_at').eq('restaurant_id', restaurant.id)
      .or(`created_at.gte.${start.toISOString()},delivered_at.gte.${start.toISOString()}`)
      .then(({ data }) => {
        const list = data ?? []
        const delivered = list.filter(o => o.status === 'delivered' && o.delivered_at && new Date(o.delivered_at) >= start)
        setToday({ revenue: delivered.reduce((s, o) => s + o.total, 0), delivered: delivered.length, pending: list.filter(o => o.status === 'pending').length })
      })
  }, [restaurant.id])

  const toggleOpen = async () => {
    const next = !restaurant.is_open
    if (!next && !window.confirm('Tạm đóng cửa? Khách sẽ không đặt được đơn mới cho tới khi bạn mở lại.')) return
    setBusy(true)
    const { error } = await supabase.from('fg_restaurants').update({ is_open: next }).eq('id', restaurant.id)
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    toast(next ? 'Đã mở cửa nhận đơn' : 'Đã tạm đóng cửa')
    reload()
    fetchRestaurants(true)
  }

  return (
    <div>
      <h1 className="text-2xl font-extrabold">{restaurant.name}</h1>
      {restaurant.lat == null && (
        <Link href="/shop?tab=info" className="mt-3 block rounded-xl bg-[#fff7df] px-4 py-3 text-sm text-[#8a6100]">
          Nhà hàng chưa có vị trí. <b className="underline">Ghim vị trí trên bản đồ</b> để khách biết quán ở đâu.
        </Link>
      )}
      <div className="mt-5 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <Card label="Doanh thu hôm nay" value={today ? money(today.revenue) : '…'} hint={today ? `${today.delivered} đơn đã giao` : undefined} />
        <Card label="Đơn chờ xác nhận" value={today ? String(today.pending) : '…'} highlight={!!today?.pending} href="/shop?tab=orders" />
        <Card label="Đánh giá" value={restaurant.review_count > 0 ? `${Number(restaurant.rating).toFixed(1)} ★` : '—'} hint={`${restaurant.review_count} lượt`} />
        <div className="min-w-0 rounded-2xl bg-white p-4 shadow-sm sm:p-5">
          <p className="text-xs text-[#746b67] sm:text-sm">Trạng thái</p>
          <p className="mt-1 truncate font-extrabold sm:mt-2">{hours.open ? 'Đang mở cửa' : hours.paused ? 'Tạm đóng cửa' : `Ngoài giờ (mở ${hours.reopens})`}</p>
          <button onClick={toggleOpen} disabled={busy}
            className={`mt-2 h-9 rounded-lg px-3 text-xs font-bold ${restaurant.is_open ? 'border border-[#eaded8] text-red-500 hover:bg-red-50' : 'bg-[#ff5b35] text-white'}`}>
            {busy ? <Loader2 className="size-4 animate-spin" /> : restaurant.is_open ? 'Tạm đóng cửa' : 'Mở cửa lại'}
          </button>
        </div>
      </div>
      <div className="mt-4 sm:mt-6"><SubscriptionPanel restaurant={restaurant} onPaid={() => { reload(); fetchRestaurants(true) }} /></div>
    </div>
  )
}

function Card({ label, value, hint, highlight, href }: { label: string; value: string; hint?: string; highlight?: boolean; href?: string }) {
  const body = (
    <>
      <p className={`text-xs sm:text-sm ${highlight ? 'text-white/80' : 'text-[#746b67]'}`}>{label}</p>
      <p className="mt-1 truncate text-xl font-extrabold sm:mt-2 sm:text-2xl">{value}</p>
      {hint && <p className={`mt-0.5 truncate text-[11px] ${highlight ? 'text-white/70' : 'text-[#9c918c]'}`}>{hint}</p>}
    </>
  )
  const cls = `block min-w-0 rounded-2xl p-4 shadow-sm sm:p-5 ${highlight ? 'bg-[#ff5b35] text-white' : 'bg-white'}`
  return href ? <Link href={href} className={cls}>{body}</Link> : <div className={cls}>{body}</div>
}
