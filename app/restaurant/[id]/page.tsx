'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'next/navigation'
import { Heart, Minus, Plus, Store } from 'lucide-react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Category, Food } from '@/lib/types'
import { money } from '@/lib/format'
import { useRestaurant } from '@/lib/store'
import { useApp } from '@/components/app-provider'
import { EmptyState, FOOD_SELECT, Spinner, StoreCard } from '@/components/cards'
import { FoodDetailSheet, RatingBadge } from '@/components/reviews'

export default function RestaurantPage() {
  const { id } = useParams<{ id: string }>()
  const restaurantId = Number(id) || null
  const store = useRestaurant(restaurantId)
  const [foods, setFoods] = useState<Food[] | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const { cart, setQty, addToCart, favoriteIds, toggleFavorite, setCartOpen, cartSubtotal, cartCount } = useApp()
  const [viewing, setViewing] = useState<Food | null>(null)
  // the cart bar only counts this restaurant's dishes (the cart may hold another restaurant's)
  const cartHere = cart[0]?.restaurant_id === restaurantId

  useEffect(() => {
    if (!isSupabaseConfigured || !restaurantId) { setFoods([]); return }
    supabase.from('fg_foods').select(FOOD_SELECT).eq('restaurant_id', restaurantId)
      .order('is_popular', { ascending: false }).order('sold_count', { ascending: false })
      .then(({ data }) => setFoods((data ?? []) as Food[]))
    supabase.from('fg_categories').select('*').order('sort').then(({ data }) => setCategories((data ?? []) as Category[]))
  }, [restaurantId])

  // "Nổi bật" first, then one section per category in the admin-defined order
  const groups = useMemo(() => {
    const list = foods ?? []
    const out: [string, Food[]][] = []
    const popular = list.filter(f => f.is_popular)
    if (popular.length) out.push(['Nổi bật', popular])
    for (const c of categories) {
      const items = list.filter(f => f.category_id === c.id)
      if (items.length) out.push([c.name, items])
    }
    const other = list.filter(f => !categories.some(c => c.id === f.category_id))
    if (other.length) out.push(['Khác', other])
    return out
  }, [foods, categories])

  if (store === undefined || foods === null) return <Spinner />
  if (store === null) {
    return (
      <main className="mx-auto max-w-2xl px-5 py-16">
        <EmptyState icon={<Store />} title="Không tìm thấy nhà hàng"><Link href="/menu" className="font-bold text-[#ff5b35]">Xem các nhà hàng khác</Link></EmptyState>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-[1100px] pb-28">
      {viewing && <FoodDetailSheet food={viewing} onClose={() => setViewing(null)} />}
      <div className="px-5 pt-4 lg:px-10"><Link href="/menu" className="-my-3 inline-block py-3 text-sm font-bold text-[#ff5b35]">← Tất cả nhà hàng</Link></div>
      <StoreCard store={store} />

      <div className="px-5 lg:px-10">
        {groups.length > 1 && (
          <nav className="sticky top-[133px] z-10 -mx-5 mt-4 flex gap-2 overflow-x-auto bg-[#fffaf7]/95 px-5 py-3 backdrop-blur [scrollbar-width:none] sm:top-20 [&::-webkit-scrollbar]:hidden">
            {groups.map(([name]) => <a key={name} href={`#cat-${name}`} className="whitespace-nowrap rounded-full bg-white px-4 py-2 text-sm font-semibold text-[#746b67] shadow-sm hover:text-[#ff5b35]">{name}</a>)}
          </nav>
        )}
        {foods.length === 0 && <div className="mt-8"><EmptyState icon={<Store />} title="Thực đơn đang được cập nhật" /></div>}
        {groups.map(([name, items]) => (
          <section key={name} id={`cat-${name}`} className="mt-8 scroll-mt-48">
            <h2 className="text-xl font-extrabold">{name}</h2>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {items.map(f => {
                const inCart = cart.find(x => x.food_id === f.id)
                const liked = favoriteIds.includes(f.id)
                return (
                  <article key={`${name}-${f.id}`} className={`flex gap-4 rounded-2xl bg-white p-3 shadow-sm ${f.is_available ? '' : 'opacity-60'}`}>
                    {f.image && (
                      <button type="button" aria-label={`Xem chi tiết ${f.name}`} onClick={() => setViewing(f)} className="shrink-0">
                        <img src={f.image} alt={f.name} loading="lazy" className="size-28 rounded-xl object-cover" />
                      </button>
                    )}
                    <div className="flex min-w-0 flex-1 flex-col">
                      <div className="flex items-start justify-between gap-2">
                        <button type="button" onClick={() => setViewing(f)} className="text-left font-bold hover:text-[#ff5b35]"><h3>{f.name}</h3></button>
                        <button aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'} onClick={() => toggleFavorite(f.id)} className="-m-1 p-1 text-[#ff5b35]"><Heart className={`size-5 ${liked ? 'fill-current' : ''}`} /></button>
                      </div>
                      <div className="mt-0.5"><RatingBadge rating={f.rating} count={f.review_count ?? 0} onClick={() => setViewing(f)} /></div>
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
                            <button aria-label={`Thêm ${f.name}`} onClick={() => addToCart(f)} className="grid size-9 place-items-center rounded-xl bg-[#fff0eb] text-[#ff5b35] hover:bg-[#ff5b35] hover:text-white"><Plus /></button>
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

      {cartCount > 0 && cartHere && (
        <div className="fixed bottom-[76px] left-0 right-0 z-20 px-5 sm:bottom-6">
          <button onClick={() => setCartOpen(true)} className="mx-auto flex h-14 w-full max-w-md items-center justify-between rounded-2xl bg-[#ff5b35] px-5 font-bold text-white shadow-xl hover:bg-[#e94c29]">
            <span>{cartCount} món • Xem giỏ hàng</span><span>{money(cartSubtotal)}</span>
          </button>
        </div>
      )}
    </main>
  )
}
