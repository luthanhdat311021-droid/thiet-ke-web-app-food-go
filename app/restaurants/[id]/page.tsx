'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'next/navigation'
import { Clock3, Heart, MapPin, Minus, Plus, Star, Store } from 'lucide-react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Category, Food, Restaurant } from '@/lib/types'
import { money } from '@/lib/format'
import { useApp } from '@/components/app-provider'
import { EmptyState, Spinner } from '@/components/cards'

export default function RestaurantPage() {
  const { id } = useParams<{ id: string }>()
  const [restaurant, setRestaurant] = useState<Restaurant | null | undefined>(undefined)
  const [foods, setFoods] = useState<Food[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const { cart, setQty, addToCart, favoriteIds, toggleFavorite, setCartOpen, cartSubtotal } = useApp()

  useEffect(() => {
    if (!isSupabaseConfigured) { setRestaurant(null); return }
    const rid = Number(id)
    supabase.from('fg_restaurants').select('*').eq('id', rid).maybeSingle().then(({ data }) => setRestaurant(data as Restaurant | null))
    supabase.from('fg_foods').select('*').eq('restaurant_id', rid).order('is_popular', { ascending: false }).order('sold_count', { ascending: false })
      .then(({ data }) => setFoods((data ?? []) as Food[]))
    supabase.from('fg_categories').select('*').order('sort').then(({ data }) => setCategories((data ?? []) as Category[]))
  }, [id])

  const groups = useMemo(() => {
    const byCat = new Map<string, Food[]>()
    const popular = foods.filter(f => f.is_popular)
    if (popular.length) byCat.set('Nổi bật', popular)
    for (const f of foods) {
      const name = categories.find(c => c.id === f.category_id)?.name ?? 'Khác'
      byCat.set(name, [...(byCat.get(name) ?? []), f])
    }
    return [...byCat.entries()]
  }, [foods, categories])

  if (restaurant === undefined) return <Spinner />
  if (restaurant === null) {
    return <main className="mx-auto max-w-3xl px-5 py-16"><EmptyState icon={<Store />} title="Không tìm thấy nhà hàng"><Link href="/search?tab=restaurants" className="font-bold text-[#ff5b35]">Xem các nhà hàng khác</Link></EmptyState></main>
  }

  const cartHere = cart.filter(x => x.restaurant_id === restaurant.id)
  const cartHereCount = cartHere.reduce((s, x) => s + x.qty, 0)

  return (
    <main className="mx-auto max-w-[1100px] pb-28">
      <div className="relative h-48 sm:h-64 lg:mt-6 lg:overflow-hidden lg:rounded-3xl">
        {restaurant.image && <img src={restaurant.image} alt={restaurant.name} className="h-full w-full object-cover" />}
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
      </div>
      <div className="relative mx-5 -mt-12 rounded-2xl bg-white p-5 shadow-sm lg:mx-10">
        <div className="flex gap-4">
          <span className="size-20 shrink-0 overflow-hidden rounded-2xl border-4 border-white bg-[#fff0eb] shadow-sm">
            {restaurant.logo && <img src={restaurant.logo} alt="" className="h-full w-full object-cover" />}
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-extrabold">{restaurant.name}</h1>
              {restaurant.tag && <span className="rounded-md bg-[#fff7df] px-2 py-1 text-xs font-bold text-[#bd8300]">{restaurant.tag}</span>}
            </div>
            <p className="mt-1 text-sm text-[#746b67]">{restaurant.cuisine}</p>
            <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[#746b67]">
              <span><Star className="mr-1 inline size-3 fill-[#ffb21c] text-[#ffb21c]" />{Number(restaurant.rating).toFixed(1)} ({restaurant.review_count} đánh giá)</span>
              <span><Clock3 className="mr-1 inline size-3" />{restaurant.delivery_time}</span>
              <span><MapPin className="mr-1 inline size-3" />{restaurant.distance_km} km{restaurant.address ? ` • ${restaurant.address}` : ''}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="px-5 lg:px-10">
        {groups.length > 1 && (
          <nav className="sticky top-[133px] z-10 -mx-5 mt-6 flex gap-2 overflow-x-auto bg-[#fffaf7]/95 px-5 py-3 backdrop-blur sm:top-20">
            {groups.map(([name]) => <a key={name} href={`#cat-${name}`} className="whitespace-nowrap rounded-full bg-white px-4 py-2 text-sm font-semibold text-[#746b67] shadow-sm hover:text-[#ff5b35]">{name}</a>)}
          </nav>
        )}
        {foods.length === 0 && <div className="mt-8"><EmptyState icon={<Store />} title="Nhà hàng chưa có món nào" /></div>}
        {groups.map(([name, items]) => (
          <section key={name} id={`cat-${name}`} className="mt-8 scroll-mt-48">
            <h2 className="text-xl font-extrabold">{name}</h2>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {items.map(f => {
                const inCart = cart.find(x => x.food_id === f.id)
                const liked = favoriteIds.includes(f.id)
                return (
                  <article key={`${name}-${f.id}`} className={`flex gap-4 rounded-2xl bg-white p-3 shadow-sm ${f.is_available ? '' : 'opacity-60'}`}>
                    {f.image && <img src={f.image} alt={f.name} loading="lazy" className="size-28 shrink-0 rounded-xl object-cover" />}
                    <div className="flex min-w-0 flex-1 flex-col">
                      <div className="flex items-start justify-between gap-2">
                        <h3 className="font-bold">{f.name}</h3>
                        <button aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'} onClick={() => toggleFavorite(f.id)} className="-m-1 p-1 text-[#ff5b35]"><Heart className={`size-5 ${liked ? 'fill-current' : ''}`} /></button>
                      </div>
                      {f.description && <p className="mt-1 line-clamp-2 text-xs text-[#9c918c]">{f.description}</p>}
                      <div className="mt-auto flex items-center justify-between pt-3">
                        <div>
                          <b className="text-[#ff5b35]">{money(f.price)}</b>
                          {f.old_price && f.old_price > f.price && <del className="ml-2 text-xs text-[#aaa09b]">{money(f.old_price)}</del>}
                        </div>
                        {!f.is_available ? <span className="text-xs font-bold text-[#9c918c]">Tạm hết</span>
                          : inCart ? (
                            <div className="flex items-center gap-2">
                              <button aria-label="Giảm" onClick={() => setQty(f.id, inCart.qty - 1)} className="grid size-8 place-items-center rounded-lg bg-[#f8f3f0]"><Minus className="size-3" /></button>
                              <span className="w-5 text-center text-sm font-bold">{inCart.qty}</span>
                              <button aria-label="Tăng" onClick={() => setQty(f.id, inCart.qty + 1)} className="grid size-8 place-items-center rounded-lg bg-[#ff5b35] text-white"><Plus className="size-3" /></button>
                            </div>
                          ) : (
                            <button aria-label={`Thêm ${f.name}`} onClick={() => addToCart(f, restaurant.name)} className="grid size-9 place-items-center rounded-xl bg-[#fff0eb] text-[#ff5b35] hover:bg-[#ff5b35] hover:text-white"><Plus /></button>
                          )}
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>
          </section>
        ))}
      </div>

      {cartHereCount > 0 && (
        <div className="fixed bottom-[76px] left-0 right-0 z-20 px-5 sm:bottom-6">
          <button onClick={() => setCartOpen(true)} className="mx-auto flex h-14 w-full max-w-md items-center justify-between rounded-2xl bg-[#ff5b35] px-5 font-bold text-white shadow-xl hover:bg-[#e94c29]">
            <span>{cartHereCount} món • Xem giỏ hàng</span><span>{money(cartSubtotal)}</span>
          </button>
        </div>
      )}
    </main>
  )
}
