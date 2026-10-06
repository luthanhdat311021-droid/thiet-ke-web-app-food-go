'use client'

import Link from 'next/link'
import { ArrowRight, Clock3, Heart, Loader2, Plus, Star } from 'lucide-react'
import { useApp } from '@/components/app-provider'
import { money } from '@/lib/format'
import type { Food, Restaurant } from '@/lib/types'

export function SectionTitle({ title, href, action = 'Xem tất cả' }: { title: string; href?: string; action?: string }) {
  return (
    <div className="flex items-center justify-between">
      <h2 className="text-2xl font-extrabold tracking-tight">{title}</h2>
      {href && <Link href={href} className="-my-2 py-3 text-sm font-bold text-[#ff5b35]">{action} <ArrowRight className="ml-1 inline size-4" /></Link>}
    </div>
  )
}

export function FoodCard({ food }: { food: Food }) {
  const { addToCart, favoriteIds, toggleFavorite } = useApp()
  const liked = favoriteIds.includes(food.id)
  const restaurant = food.restaurants
  return (
    <article className="group overflow-hidden rounded-2xl bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg">
      <div className="relative h-48 overflow-hidden">
        <Link href={`/restaurants/${food.restaurant_id}`}>
          {food.image && <img src={food.image} alt={food.name} loading="lazy" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
        </Link>
        {!food.is_available && <span className="absolute left-3 top-3 rounded-full bg-[#241c19]/80 px-3 py-1 text-xs font-bold text-white">Tạm hết</span>}
        <button aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'} onClick={() => toggleFavorite(food.id)} className="absolute right-3 top-3 grid size-9 place-items-center rounded-full bg-white/90 text-[#ff5b35] shadow-sm">
          <Heart className={liked ? 'fill-current' : ''} />
        </button>
      </div>
      <div className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="truncate font-bold">{food.name}</h3>
            {restaurant && <Link href={`/restaurants/${restaurant.id}`} className="mt-1 block truncate text-xs text-[#9c918c] hover:text-[#ff5b35]">{restaurant.name}</Link>}
          </div>
          <span className="flex shrink-0 items-center gap-1 text-xs font-bold"><Star className="size-3 fill-[#ffb21c] text-[#ffb21c]" />{Number(food.rating).toFixed(1)}</span>
        </div>
        {restaurant && (
          <div className="mt-4 flex items-center gap-3 text-xs text-[#746b67]">
            <span><Clock3 className="mr-1 inline size-3" />{restaurant.delivery_time}</span><span>• {restaurant.distance_km} km</span>
          </div>
        )}
        <div className="mt-4 flex items-center justify-between">
          <div>
            <b className="text-lg text-[#ff5b35]">{money(food.price)}</b>
            {food.old_price && food.old_price > food.price && <del className="ml-2 text-xs text-[#aaa09b]">{money(food.old_price)}</del>}
          </div>
          <button
            aria-label={`Thêm ${food.name} vào giỏ`}
            disabled={!food.is_available}
            onClick={() => addToCart(food, restaurant?.name ?? '')}
            className="grid size-10 place-items-center rounded-xl bg-[#fff0eb] text-[#ff5b35] transition hover:bg-[#ff5b35] hover:text-white disabled:opacity-40"
          ><Plus /></button>
        </div>
      </div>
    </article>
  )
}

export function RestaurantCard({ restaurant }: { restaurant: Restaurant }) {
  return (
    <Link href={`/restaurants/${restaurant.id}`} className="block overflow-hidden rounded-2xl bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg">
      {restaurant.image && <img src={restaurant.image} alt={restaurant.name} loading="lazy" className="h-36 w-full object-cover" />}
      <div className="relative p-4 pt-0">
        <span className="absolute -top-7 left-4 size-14 overflow-hidden rounded-2xl border-4 border-white bg-[#fff0eb] shadow-sm">
          {restaurant.logo && <img src={restaurant.logo} alt="" loading="lazy" className="h-full w-full object-cover" />}
        </span>
        <div className="pt-10">
          <div className="flex items-center justify-between gap-2">
            <h3 className="truncate font-bold">{restaurant.name}</h3>
            {restaurant.tag && <span className="shrink-0 rounded-md bg-[#fff7df] px-2 py-1 text-xs font-bold text-[#bd8300]">{restaurant.tag}</span>}
          </div>
          <p className="mt-1 text-xs text-[#9c918c]">{restaurant.cuisine}</p>
          <div className="mt-4 flex gap-3 text-xs text-[#746b67]">
            <span><Star className="mr-1 inline size-3 fill-[#ffb21c] text-[#ffb21c]" />{Number(restaurant.rating).toFixed(1)} ({formatCount(restaurant.review_count)})</span>
            <span>• {restaurant.distance_km} km</span>
            <span>• {restaurant.delivery_time}</span>
          </div>
        </div>
      </div>
    </Link>
  )
}

const formatCount = (n: number) => (n >= 1000 ? `${(n / 1000).toFixed(1).replace('.0', '')}k` : String(n))

export function CardSkeleton({ count = 3, className = 'h-72' }: { count?: number; className?: string }) {
  return <>{Array.from({ length: count }, (_, i) => <div key={i} className={`animate-pulse rounded-2xl bg-[#f4ece8] ${className}`} />)}</>
}

export function Spinner({ label = 'Đang tải...' }: { label?: string }) {
  return <div className="flex items-center justify-center gap-2 py-20 text-sm text-[#9c918c]"><Loader2 className="size-5 animate-spin text-[#ff5b35]" />{label}</div>
}

export function EmptyState({ icon, title, children }: { icon: React.ReactNode; title: string; children?: React.ReactNode }) {
  return (
    <div className="rounded-2xl bg-white px-6 py-16 text-center shadow-sm">
      <div className="mx-auto grid size-16 place-items-center rounded-full bg-[#fff0eb] text-[#ff5b35]">{icon}</div>
      <h3 className="mt-4 text-lg font-bold">{title}</h3>
      {children && <div className="mt-2 text-sm text-[#746b67]">{children}</div>}
    </div>
  )
}

export function Panel({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="rounded-2xl bg-white p-5 shadow-sm">
      <div className="mb-5 flex items-center justify-between gap-3"><h2 className="text-lg font-extrabold">{title}</h2>{action}</div>
      {children}
    </section>
  )
}

export const FOOD_SELECT = '*, restaurants:fg_restaurants(id, name, delivery_time, distance_km)'
