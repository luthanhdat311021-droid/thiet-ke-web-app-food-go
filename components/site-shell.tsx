'use client'

import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useRef, useState } from 'react'
import {
  Bell, ChevronDown, Heart, Home, LayoutDashboard, Loader2, LogIn, LogOut, MapPin, Minus, Package, Plus,
  Search, ShoppingBag, ShoppingCart, Trash2, User, X,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { useLocation } from '@/components/location-provider'
import { ROUGH_ACCURACY_M } from '@/lib/geo'
import { isSupabaseConfigured } from '@/lib/supabase'
import { money, shippingFee, FREE_SHIP_FROM, timeAgo } from '@/lib/format'

export function SiteShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  if (pathname.startsWith('/admin')) return <>{children}</>
  return (
    <div className="min-h-screen bg-[#fffaf7] text-[#241c19]">
      <Header />
      {!isSupabaseConfigured && <SetupBanner />}
      {children}
      <BottomNav />
      <CartDrawer />
    </div>
  )
}

function SetupBanner() {
  return (
    <div className="border-b border-[#ffd9cc] bg-[#fff0eb] px-5 py-3 text-center text-sm text-[#a33a1f]">
      Chưa kết nối Supabase. Tạo file <code className="font-bold">.env.local</code> theo hướng dẫn trong <code className="font-bold">SUPABASE_SETUP.md</code> rồi khởi động lại server.
    </div>
  )
}

export function Logo() {
  return (
    <Link href="/" className="flex shrink-0 items-center gap-2 text-xl font-extrabold tracking-tight">
      <span className="grid size-10 place-items-center rounded-xl bg-[#ff5b35] text-lg text-white">F</span>
      <span>Food<span className="text-[#ff5b35]">Go</span></span>
    </Link>
  )
}

function SearchBox() {
  const router = useRouter()
  const params = useSearchParams()
  const pathname = usePathname()
  const [q, setQ] = useState(params.get('q') ?? '')
  useEffect(() => { if (pathname === '/search') setQ(params.get('q') ?? '') }, [params, pathname])
  return (
    <form
      role="search"
      onSubmit={e => { e.preventDefault(); router.push(`/search${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`) }}
      className="relative order-last w-full sm:order-none sm:ml-auto sm:max-w-lg"
    >
      <Search className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-[#9c918c]" />
      <input
        value={q}
        onChange={e => setQ(e.target.value)}
        placeholder="Bạn muốn ăn gì hôm nay?"
        aria-label="Tìm món ăn hoặc nhà hàng"
        className="h-12 w-full rounded-xl bg-[#f8f3f0] pl-12 pr-4 text-sm outline-none ring-[#ff5b35] transition focus:ring-2"
      />
    </form>
  )
}

/** "Giao đến …" – the customer's detected address; tap to (re)locate. */
function LocationChip() {
  const { place, status, error, locate } = useLocation()
  const rough = status === 'ready' && (place?.accuracy ?? 0) > ROUGH_ACCURACY_M
  const text = status === 'locating' ? 'Đang xác định vị trí...'
    : place ? place.address
    : status === 'error' ? error
    : 'Chọn vị trí giao hàng'
  return (
    <button
      onClick={() => locate()}
      title={place?.address ?? 'Lấy vị trí hiện tại'}
      className="flex min-w-0 flex-1 items-center gap-2 rounded-xl px-2 py-1.5 text-left text-sm hover:bg-[#fff5f1] sm:max-w-[240px] sm:flex-none xl:max-w-xs"
    >
      {status === 'locating' ? <Loader2 className="size-4 shrink-0 animate-spin text-[#ff5b35]" /> : <MapPin className="size-4 shrink-0 text-[#ff5b35]" />}
      <span className="min-w-0">
        <span className="block text-[11px] leading-tight text-[#9c918c]">
          Giao đến{rough && <span className="text-[#c2410c]"> • vị trí ước tính, bấm để thử lại</span>}
        </span>
        <b className={`block truncate leading-tight ${status === 'error' ? 'font-semibold text-[#c2410c]' : 'text-[#241c19]'}`}>{text}</b>
      </span>
    </button>
  )
}

function useClickOutside(onOutside: () => void) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const handler = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) onOutside() }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [onOutside])
  return ref
}

function Header() {
  const { user, profile, cartCount, setCartOpen, notifications, unreadCount, markAllRead, signOut } = useApp()
  const [menu, setMenu] = useState<'profile' | 'notifications' | null>(null)
  const pathname = usePathname()
  useEffect(() => setMenu(null), [pathname])
  const close = () => setMenu(null)
  const ref = useClickOutside(close)
  const initials = (profile?.full_name || user?.email || '?').split(' ').map(w => w[0]).slice(-2).join('').toUpperCase()

  return (
    <header className="sticky top-0 z-30 border-b border-[#f1e7e2] bg-white/95 backdrop-blur">
      <div ref={ref} className="relative mx-auto flex max-w-[1400px] flex-wrap items-center gap-x-5 gap-y-3 px-5 py-3 sm:h-20 sm:flex-nowrap sm:py-0 lg:px-10">
        <Logo />
        <LocationChip />
        <Suspense fallback={<div className="order-last h-12 w-full sm:order-none sm:ml-auto sm:max-w-lg" />}><SearchBox /></Suspense>
        <Link href="/account?tab=favorites" aria-label="Món yêu thích" className="hidden rounded-xl p-3 text-[#746b67] hover:bg-[#fff1ec] hover:text-[#ff5b35] lg:block"><Heart /></Link>
        {user && (
          <button
            aria-label="Thông báo"
            onClick={() => { setMenu(menu === 'notifications' ? null : 'notifications'); markAllRead() }}
            className="relative hidden rounded-xl p-3 text-[#746b67] hover:bg-[#fff1ec] hover:text-[#ff5b35] sm:block"
          >
            <Bell />{unreadCount > 0 && <i className="absolute right-2 top-2 size-2 rounded-full bg-[#ff5b35]" />}
          </button>
        )}
        <button aria-label="Giỏ hàng" onClick={() => setCartOpen(true)} className="relative ml-auto rounded-xl p-3 text-[#746b67] hover:bg-[#fff1ec] hover:text-[#ff5b35] sm:ml-0">
          <ShoppingCart />
          {cartCount > 0 && <b className="absolute -right-1 -top-1 grid size-5 place-items-center rounded-full bg-[#ff5b35] text-[10px] text-white">{cartCount}</b>}
        </button>
        {user ? (
          <button onClick={() => setMenu(menu === 'profile' ? null : 'profile')} className="hidden items-center gap-2 sm:flex" aria-label="Tài khoản">
            {profile?.avatar_url
              ? <img src={profile.avatar_url} alt="" className="size-10 rounded-full object-cover" />
              : <span className="grid size-10 place-items-center rounded-full bg-[#ffe0d5] font-bold text-[#ff5b35]">{initials}</span>}
            <ChevronDown className="size-4 text-[#746b67]" />
          </button>
        ) : (
          <Link href="/login" className="hidden h-11 items-center gap-2 rounded-xl bg-[#ff5b35] px-4 text-sm font-bold text-white hover:bg-[#e94c29] sm:flex"><LogIn className="size-4" />Đăng nhập</Link>
        )}

        {menu === 'profile' && user && (
          <div className="absolute right-5 top-[72px] w-56 rounded-2xl border border-[#f1e7e2] bg-white p-2 shadow-xl lg:right-10">
            <p className="truncate px-3 py-2 text-sm font-bold">{profile?.full_name || user.email}</p>
            {([
              { href: '/account', label: 'Hồ sơ cá nhân', Icon: User },
              { href: '/orders', label: 'Đơn hàng của tôi', Icon: Package },
              { href: '/account?tab=addresses', label: 'Địa chỉ', Icon: MapPin },
              { href: '/account?tab=favorites', label: 'Món yêu thích', Icon: Heart },
              ...(profile?.role === 'admin' ? [{ href: '/admin', label: 'Trang quản trị', Icon: LayoutDashboard }] : []),
            ]).map(({ href, label, Icon }) => (
              <Link key={href} href={href} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm hover:bg-[#fff5f1]">
                <Icon className="size-4 text-[#ff5b35]" />{label}
              </Link>
            ))}
            <button onClick={() => { close(); signOut() }} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm text-red-500 hover:bg-red-50"><LogOut className="size-4" />Đăng xuất</button>
          </div>
        )}
        {menu === 'notifications' && (
          <div className="absolute right-5 top-[72px] w-80 rounded-2xl border border-[#f1e7e2] bg-white p-4 shadow-xl lg:right-24">
            <h3 className="font-bold">Thông báo</h3>
            <div className="mt-2 max-h-96 overflow-y-auto">
              {notifications.length === 0 && <p className="py-8 text-center text-sm text-[#9c918c]">Chưa có thông báo nào</p>}
              {notifications.map(n => (
                <Link key={n.id} href={n.order_id ? `/orders/${n.order_id}` : '/orders'} className="mt-2 flex gap-3 rounded-xl p-2 text-sm hover:bg-[#fff5f1]">
                  <span className="grid size-8 shrink-0 place-items-center rounded-full bg-[#fff0eb] text-[#ff5b35]"><Bell className="size-4" /></span>
                  <span><b className="block">{n.title}</b>{n.body}<small className="mt-1 block text-[#9c918c]">{timeAgo(n.created_at)}</small></span>
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </header>
  )
}

function BottomNav() {
  const pathname = usePathname()
  const { setCartOpen, cartCount, user } = useApp()
  const item = (href: string, label: string, icon: React.ReactNode, active: boolean) => (
    <Link href={href} className={`flex flex-col items-center gap-1 text-xs ${active ? 'text-[#ff5b35]' : 'text-[#9c918c]'}`}>{icon}<span>{label}</span></Link>
  )
  return (
    <nav className="fixed bottom-0 left-0 right-0 z-20 flex justify-around border-t border-[#f1e7e2] bg-white p-3 sm:hidden">
      {item('/', 'Trang chủ', <Home />, pathname === '/')}
      {item('/search', 'Tìm kiếm', <Search />, pathname.startsWith('/search') || pathname.startsWith('/restaurants'))}
      {item('/orders', 'Đơn hàng', <Package />, pathname.startsWith('/orders'))}
      <button onClick={() => setCartOpen(true)} className="relative flex flex-col items-center gap-1 text-xs text-[#9c918c]">
        <ShoppingBag />
        {cartCount > 0 && <b className="absolute -right-2 -top-1 grid size-4 place-items-center rounded-full bg-[#ff5b35] text-[9px] text-white">{cartCount}</b>}
        <span>Giỏ hàng</span>
      </button>
      {item(user ? '/account' : '/login', 'Tài khoản', <User />, pathname.startsWith('/account') || pathname === '/login')}
    </nav>
  )
}

function CartDrawer() {
  const { cart, cartOpen, setCartOpen, setQty, cartSubtotal, clearCart } = useApp()
  const router = useRouter()
  useEffect(() => {
    if (!cartOpen) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setCartOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [cartOpen, setCartOpen])
  if (!cartOpen) return null
  const fee = cart.length ? shippingFee(cartSubtotal) : 0

  return (
    <div className="fixed inset-0 z-40 bg-black/30" onClick={() => setCartOpen(false)}>
      <aside role="dialog" aria-label="Giỏ hàng" onClick={e => e.stopPropagation()} className="absolute right-0 top-0 flex h-full w-full max-w-md flex-col bg-white shadow-2xl">
        <div className="flex items-center justify-between p-6 pb-0">
          <div>
            <p className="text-xs font-bold text-[#ff5b35]">GIỎ HÀNG CỦA BẠN</p>
            <h2 className="mt-1 text-2xl font-extrabold">{cart[0]?.restaurant_name ?? 'Giỏ hàng'}</h2>
          </div>
          <button aria-label="Đóng giỏ hàng" onClick={() => setCartOpen(false)} className="grid size-10 place-items-center rounded-full bg-[#f8f3f0]"><X /></button>
        </div>
        <div className="flex-1 overflow-y-auto px-6">
          <div className="mt-8 flex flex-col gap-5">
            {cart.map(x => (
              <div key={x.food_id} className="flex gap-3 border-b border-[#f1e7e2] pb-5">
                {x.image && <img src={x.image} alt="" className="size-20 rounded-xl object-cover" />}
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-bold">{x.name}</h3>
                  <p className="mt-1 text-sm text-[#ff5b35]">{money(x.price)}</p>
                  <div className="mt-3 flex items-center gap-3">
                    <button aria-label="Giảm" onClick={() => setQty(x.food_id, x.qty - 1)} className="grid size-8 place-items-center rounded-lg bg-[#f8f3f0]"><Minus className="size-3" /></button>
                    <span className="w-5 text-center text-sm font-bold">{x.qty}</span>
                    <button aria-label="Tăng" onClick={() => setQty(x.food_id, x.qty + 1)} className="grid size-8 place-items-center rounded-lg bg-[#fff0eb] text-[#ff5b35]"><Plus className="size-3" /></button>
                  </div>
                </div>
                <button aria-label="Xóa món" onClick={() => setQty(x.food_id, 0)} className="grid size-8 place-items-center self-start text-[#aaa09b] hover:text-red-500"><Trash2 className="size-4" /></button>
              </div>
            ))}
          </div>
          {!cart.length && (
            <div className="py-20 text-center">
              <ShoppingBag className="mx-auto size-12 text-[#ffb9a5]" />
              <h3 className="mt-4 font-bold">Giỏ hàng đang trống</h3>
              <Button onClick={() => { setCartOpen(false); router.push('/search') }} variant="outline" className="mt-5 h-11 rounded-xl px-5">Khám phá món ăn</Button>
            </div>
          )}
          {cart.length > 0 && <button onClick={clearCart} className="mt-4 py-2 text-sm font-semibold text-[#9c918c] hover:text-red-500">Xóa tất cả</button>}
        </div>
        <div className="border-t border-[#f1e7e2] p-6">
          <div className="flex justify-between text-sm text-[#746b67]"><span>Tạm tính</span><b className="text-[#241c19]">{money(cartSubtotal)}</b></div>
          <div className="mt-3 flex justify-between text-sm text-[#746b67]"><span>Phí giao hàng</span>{fee ? <b className="text-[#241c19]">{money(fee)}</b> : <b className="text-[#72a77f]">Miễn phí</b>}</div>
          {cart.length > 0 && fee > 0 && <p className="mt-2 text-xs text-[#9c918c]">Mua thêm {money(FREE_SHIP_FROM - cartSubtotal)} để được miễn phí giao hàng</p>}
          <div className="mt-5 flex justify-between text-lg font-extrabold"><span>Tổng cộng</span><span className="text-[#ff5b35]">{money(cartSubtotal + fee)}</span></div>
          <Button disabled={!cart.length} onClick={() => { setCartOpen(false); router.push('/checkout') }} className="mt-5 h-12 w-full rounded-xl bg-[#ff5b35] hover:bg-[#e94c29]">Tiến hành thanh toán</Button>
        </div>
      </aside>
    </div>
  )
}
