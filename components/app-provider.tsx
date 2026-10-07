'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import type { User } from '@supabase/supabase-js'
import { Check, X } from 'lucide-react'
import { isSupabaseConfigured, supabase, errorMessage } from '@/lib/supabase'
import type { CartItem, Food, Notification, Profile } from '@/lib/types'

type AppContextValue = {
  user: User | null
  profile: Profile | null
  authLoading: boolean
  refreshProfile: () => Promise<void>
  signOut: () => Promise<void>

  cart: CartItem[]
  cartCount: number
  cartSubtotal: number
  addToCart: (food: Food, qty?: number) => void
  setQty: (foodId: number, qty: number) => void
  replaceCart: (items: CartItem[]) => boolean
  clearCart: () => void
  cartOpen: boolean
  setCartOpen: (open: boolean) => void

  favoriteIds: number[]
  toggleFavorite: (foodId: number) => Promise<void>

  notifications: Notification[]
  unreadCount: number
  markAllRead: () => Promise<void>

  toast: (message: string, kind?: 'success' | 'error') => void
}

const AppContext = createContext<AppContextValue | null>(null)

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used inside <AppProvider>')
  return ctx
}

const CART_KEY = 'foodgo-cart'

export function AppProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const [user, setUser] = useState<User | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [authLoading, setAuthLoading] = useState(isSupabaseConfigured)
  const [cart, setCart] = useState<CartItem[]>([])
  const [cartOpen, setCartOpen] = useState(false)
  const [favoriteIds, setFavoriteIds] = useState<number[]>([])
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [toastState, setToastState] = useState<{ message: string; kind: 'success' | 'error' } | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout>>(undefined)

  const toast = useCallback((message: string, kind: 'success' | 'error' = 'success') => {
    clearTimeout(toastTimer.current)
    setToastState({ message, kind })
    toastTimer.current = setTimeout(() => setToastState(null), 2600)
  }, [])

  // ---------- cart (localStorage) ----------
  useEffect(() => {
    try {
      const saved = localStorage.getItem(CART_KEY)
      if (saved) setCart(JSON.parse(saved))
    } catch {}
  }, [])
  useEffect(() => {
    try { localStorage.setItem(CART_KEY, JSON.stringify(cart)) } catch {}
  }, [cart])

  // one order = one restaurant (fg_place_order enforces it): switching restaurants starts a new cart
  const addToCart = useCallback((food: Food, qty = 1) => {
    if (!food.is_available) return toast('Món này tạm hết', 'error')
    const other = cart.find(x => x.restaurant_id !== food.restaurant_id)
    const restaurantName = food.restaurants?.name ?? 'Nhà hàng'
    if (other && !window.confirm(`Giỏ hàng đang có món của ${other.restaurant_name}. Mỗi đơn chỉ đặt từ một nhà hàng.\n\nXóa giỏ hiện tại để thêm món của ${restaurantName}?`)) return
    const item: CartItem = { food_id: food.id, name: food.name, price: food.price, image: food.image, restaurant_id: food.restaurant_id, restaurant_name: restaurantName, qty }
    setCart(c => {
      const same = c.filter(x => x.restaurant_id === food.restaurant_id)
      return same.some(x => x.food_id === food.id)
        ? same.map(x => (x.food_id === food.id ? { ...x, qty: Math.min(50, x.qty + qty) } : x))
        : [...same, item]
    })
    toast(`Đã thêm ${food.name} vào giỏ`)
  }, [cart, toast])

  const replaceCart = useCallback((items: CartItem[]) => {
    if (cart.length && !window.confirm('Thay giỏ hàng hiện tại bằng các món trong đơn này?')) return false
    setCart(items)
    return true
  }, [cart])

  const setQty = useCallback((foodId: number, qty: number) => {
    setCart(c => c.map(x => (x.food_id === foodId ? { ...x, qty: Math.min(50, qty) } : x)).filter(x => x.qty > 0))
  }, [])
  const clearCart = useCallback(() => setCart([]), [])
  const cartCount = cart.reduce((s, x) => s + x.qty, 0)
  const cartSubtotal = cart.reduce((s, x) => s + x.qty * x.price, 0)

  // ---------- auth ----------
  const loadProfile = useCallback(async (uid: string) => {
    const { data } = await supabase.from('fg_profiles').select('*').eq('id', uid).maybeSingle()
    if (data) return setProfile(data as Profile)
    // account created before FoodGo's signup trigger existed
    const { data: { user: u } } = await supabase.auth.getUser()
    const meta = u?.user_metadata ?? {}
    const { data: created } = await supabase.from('fg_profiles')
      .insert({ id: uid, full_name: meta.full_name ?? meta.name ?? u?.email?.split('@')[0] ?? null, avatar_url: meta.avatar_url ?? null })
      .select().maybeSingle()
    setProfile(created as Profile | null)
  }, [])

  useEffect(() => {
    if (!isSupabaseConfigured) return
    supabase.auth.getSession().then(({ data }) => {
      setUser(data.session?.user ?? null)
      setAuthLoading(false)
    })
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      setUser(session?.user ?? null)
      setAuthLoading(false)
    })
    return () => sub.subscription.unsubscribe()
  }, [])

  useEffect(() => {
    if (!user) {
      setProfile(null); setFavoriteIds([]); setNotifications([])
      return
    }
    loadProfile(user.id)
    supabase.from('fg_favorites').select('food_id').then(({ data }) => setFavoriteIds((data ?? []).map(f => f.food_id)))
    supabase.from('fg_notifications').select('*').order('created_at', { ascending: false }).limit(20)
      .then(({ data }) => setNotifications((data ?? []) as Notification[]))

    const channel = supabase
      .channel(`notifications-${user.id}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'fg_notifications', filter: `user_id=eq.${user.id}` }, payload => {
        const n = payload.new as Notification
        setNotifications(list => [n, ...list].slice(0, 20))
        toast(n.body ?? n.title)
      })
      .subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [user, loadProfile, toast])

  const refreshProfile = useCallback(async () => { if (user) await loadProfile(user.id) }, [user, loadProfile])

  const signOut = useCallback(async () => {
    await supabase.auth.signOut()
    toast('Đã đăng xuất')
    router.push('/')
  }, [router, toast])

  // ---------- favorites ----------
  const toggleFavorite = useCallback(async (foodId: number) => {
    if (!user) { router.push('/login'); return }
    const liked = favoriteIds.includes(foodId)
    setFavoriteIds(ids => (liked ? ids.filter(i => i !== foodId) : [...ids, foodId]))
    const { error } = liked
      ? await supabase.from('fg_favorites').delete().eq('user_id', user.id).eq('food_id', foodId)
      : await supabase.from('fg_favorites').insert({ user_id: user.id, food_id: foodId })
    if (error) {
      setFavoriteIds(ids => (liked ? [...ids, foodId] : ids.filter(i => i !== foodId)))
      toast(errorMessage(error), 'error')
    }
  }, [user, favoriteIds, router, toast])

  // ---------- notifications ----------
  const unreadCount = notifications.filter(n => !n.is_read).length
  const markAllRead = useCallback(async () => {
    if (!user || !unreadCount) return
    setNotifications(list => list.map(n => ({ ...n, is_read: true })))
    await supabase.from('fg_notifications').update({ is_read: true }).eq('user_id', user.id).eq('is_read', false)
  }, [user, unreadCount])

  const value = useMemo<AppContextValue>(() => ({
    user, profile, authLoading, refreshProfile, signOut,
    cart, cartCount, cartSubtotal, addToCart, setQty, replaceCart, clearCart, cartOpen, setCartOpen,
    favoriteIds, toggleFavorite,
    notifications, unreadCount, markAllRead,
    toast,
  }), [user, profile, authLoading, refreshProfile, signOut, cart, cartCount, cartSubtotal, addToCart, setQty, replaceCart, clearCart, cartOpen,
    favoriteIds, toggleFavorite, notifications, unreadCount, markAllRead, toast])

  return (
    <AppContext.Provider value={value}>
      {children}
      {toastState && (
        <div role="status" className="fixed bottom-24 left-1/2 z-[60] flex max-w-[90vw] -translate-x-1/2 items-center gap-2 rounded-full bg-[#241c19] px-5 py-3 text-sm font-semibold text-white shadow-xl sm:bottom-8">
          {toastState.kind === 'success' ? <Check className="size-4 shrink-0 text-[#72d09a]" /> : <X className="size-4 shrink-0 text-[#ff8a70]" />}
          <span className="truncate">{toastState.message}</span>
        </div>
      )}
    </AppContext.Provider>
  )
}
