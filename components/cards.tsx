'use client'

import Link from 'next/link'
import { useState } from 'react'
import { ArrowRight, Bike, Clock3, Heart, Loader2, MapPin, Moon, Plus, Star, Store } from 'lucide-react'
import { type StoreHours, storeHours, useStoreHours } from '@/lib/store'
import { FoodMap } from '@/components/map'
import { FoodDetailSheet, RatingBadge } from '@/components/reviews'
import { useApp } from '@/components/app-provider'
import { money } from '@/lib/format'
import { useLocation } from '@/components/location-provider'
import { distanceKm, formatKm, hasCoords } from '@/lib/geo'
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
  const [showDetail, setShowDetail] = useState(false)
  return (
    <article className="group overflow-hidden rounded-2xl bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg">
      {showDetail && <FoodDetailSheet food={food} onClose={() => setShowDetail(false)} />}
      <div className="relative h-48 overflow-hidden">
        <button type="button" aria-label={`Xem chi tiết ${food.name}`} onClick={() => setShowDetail(true)} className="block h-full w-full">
          {food.image && <img src={food.image} alt={food.name} loading="lazy" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
        </button>
        {!food.is_available && <span className="absolute left-3 top-3 rounded-full bg-[#241c19]/80 px-3 py-1 text-xs font-bold text-white">Tạm hết</span>}
        <button aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'} onClick={() => toggleFavorite(food.id)} className="absolute right-3 top-3 grid size-9 place-items-center rounded-full bg-white/90 text-[#ff5b35] shadow-sm">
          <Heart className={liked ? 'fill-current' : ''} />
        </button>
      </div>
      <div className="p-4">
        <button type="button" onClick={() => setShowDetail(true)} className="block w-full text-left hover:text-[#ff5b35]"><h3 className="truncate font-bold">{food.name}</h3></button>
        {food.restaurants && (
          <Link href={`/restaurant/${food.restaurants.id}`} className="mt-0.5 flex items-center gap-1 truncate text-xs font-semibold text-[#746b67] hover:text-[#ff5b35]">
            <Store className="size-3 shrink-0" /><span className="truncate">{food.restaurants.name}</span>
          </Link>
        )}
        <div className="mt-1"><RatingBadge rating={food.rating} count={food.review_count ?? 0} onClick={() => setShowDetail(true)} /></div>
        {food.description && <p className="mt-1 line-clamp-2 text-xs text-[#9c918c]">{food.description}</p>}
        <div className="mt-4 flex items-center justify-between">
          <div>
            <b className="text-lg text-[#ff5b35]">{money(food.price)}</b>
            {food.old_price && food.old_price > food.price && <del className="ml-2 text-xs text-[#aaa09b]">{money(food.old_price)}</del>}
          </div>
          <button
            aria-label={`Thêm ${food.name} vào giỏ`}
            disabled={!food.is_available}
            onClick={() => addToCart(food)}
            className="grid size-10 place-items-center rounded-xl bg-[#fff0eb] text-[#ff5b35] transition hover:bg-[#ff5b35] hover:text-white disabled:opacity-40"
          ><Plus /></button>
        </div>
      </div>
    </article>
  )
}

/** Square logo, or the restaurant's initial when it has none. */
export function RestaurantLogo({ r, className = 'size-16 text-2xl' }: { r: Pick<Restaurant, 'name' | 'logo'>; className?: string }) {
  return r.logo
    // relative + z-10: the logo overlaps the cover photo (negative margin) and must paint above it
    ? <img src={r.logo} alt="" className={`relative z-10 shrink-0 rounded-2xl border-4 border-white bg-white object-cover shadow-md ${className}`} />
    : <span className={`relative z-10 grid shrink-0 place-items-center rounded-2xl border-4 border-white bg-[#ff5b35] font-extrabold text-white shadow-md ${className}`}>{r.name.trim()[0]?.toUpperCase()}</span>
}

/** One restaurant in a list: photo, open/closed, rating, distance. */
export function RestaurantCard({ r }: { r: Restaurant }) {
  const distance = useDistanceLabel(r)
  const hours = storeHours(r)
  return (
    <Link href={`/restaurant/${r.id}`} className="group block overflow-hidden rounded-2xl bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg">
      <div className="relative h-36 overflow-hidden bg-[#f8f3f0]">
        {r.image && <img src={r.image} alt={r.name} loading="lazy" className={`h-full w-full object-cover transition duration-500 group-hover:scale-105 ${hours.open ? '' : 'grayscale-[60%]'}`} />}
        <span className={`absolute left-3 top-3 rounded-full px-3 py-1 text-xs font-bold ${hours.open ? 'bg-white/95 text-[#2f7d4f]' : 'bg-[#241c19]/80 text-white'}`}>
          {hours.open ? 'Đang mở cửa' : hours.paused ? 'Tạm đóng cửa' : hours.reopens ? `Mở lúc ${hours.reopens}` : 'Đã đóng cửa'}
        </span>
        {r.tag && <span className="absolute right-3 top-3 rounded-full bg-[#fff7df] px-3 py-1 text-xs font-bold text-[#bd8300]">{r.tag}</span>}
      </div>
      <div className="flex gap-3 p-4">
        <RestaurantLogo r={r} className="-mt-10 size-14 text-xl" />
        <div className="min-w-0 flex-1">
          <h3 className="truncate font-bold group-hover:text-[#ff5b35]">{r.name}</h3>
          {r.cuisine && <p className="truncate text-xs text-[#746b67]">{r.cuisine}</p>}
          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-[#746b67]">
            {r.review_count > 0
              ? <span><Star className="mr-1 inline size-3 fill-[#ffb21c] text-[#ffb21c]" />{Number(r.rating).toFixed(1)} ({formatCount(r.review_count)})</span>
              : <span className="text-[#9c918c]">Chưa có đánh giá</span>}
            <span><Bike className="mr-1 inline size-3" />{r.delivery_time}</span>
            <span><MapPin className="mr-1 inline size-3" />{distance}</span>
          </div>
        </div>
      </div>
    </Link>
  )
}

/** A restaurant's header card with an expandable location map. */
export function StoreCard({ store }: { store: Restaurant }) {
  const distance = useDistanceLabel(store)
  const { place } = useLocation()
  const [showMap, setShowMap] = useState(false)
  const hours = useStoreHours(store)
  return (
    <section className="lg:px-10 lg:pt-6">
      <div className="relative h-40 sm:h-56 lg:overflow-hidden lg:rounded-3xl">
        {store.image && <img src={store.image} alt={store.name} className="h-full w-full object-cover" />}
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
      </div>
      <div className="relative mx-5 -mt-12 rounded-2xl bg-white p-5 shadow-sm lg:mx-0">
        <div className="flex gap-4">
          <RestaurantLogo r={store} />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-extrabold">{store.name}</h1>
              {hours && (hours.open
                ? <span className="rounded-md bg-[#e4f8eb] px-2 py-1 text-xs font-bold text-[#2f7d4f]">Đang mở cửa</span>
                : <span className="rounded-md bg-[#f4f0ee] px-2 py-1 text-xs font-bold text-[#746b67]">Đã đóng cửa</span>)}
              {store.tag && <span className="rounded-md bg-[#fff7df] px-2 py-1 text-xs font-bold text-[#bd8300]">{store.tag}</span>}
            </div>
            {store.cuisine && <p className="mt-1 text-sm text-[#746b67]">{store.cuisine}</p>}
            <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[#746b67]">
              {store.review_count > 0
                ? <span><Star className="mr-1 inline size-3 fill-[#ffb21c] text-[#ffb21c]" />{Number(store.rating).toFixed(1)} ({formatCount(store.review_count)} đánh giá)</span>
                : <span>Chưa có đánh giá</span>}
              {hours?.hours && <span><Clock3 className="mr-1 inline size-3" />Mở cửa {hours.hours}</span>}
              <span><Bike className="mr-1 inline size-3" />Giao {store.delivery_time}</span>
              <span><MapPin className="mr-1 inline size-3" />Cách bạn {distance}</span>
            </div>
            {store.address && <p className="mt-2 text-xs text-[#9c918c]">{store.address}</p>}
          </div>
        </div>
        {hours && !hours.open && <ClosedNotice hours={hours} className="mt-4" />}
        {hasCoords(store) && (
          <>
            <button onClick={() => setShowMap(s => !s)} className="mt-4 flex w-full items-center justify-between rounded-xl bg-[#f8f3f0] px-4 py-3 text-sm font-bold">
              <span className="flex items-center gap-2"><MapPin className="size-4 text-[#ff5b35]" />Xem vị trí nhà hàng trên bản đồ</span>
              <span className="text-[#ff5b35]">{showMap ? 'Thu gọn' : 'Mở'}</span>
            </button>
            {showMap && (
              <div className="mt-3 overflow-hidden rounded-xl">
                <FoodMap
                  className="h-60"
                  markers={[
                    { id: 'store', kind: 'restaurant', pos: [store.lat, store.lng], label: store.name },
                    ...(place ? [{ id: 'me', kind: 'home' as const, pos: [place.lat, place.lng] as [number, number], label: 'Bạn' }] : []),
                  ]}
                />
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}

export function ClosedNotice({ hours, className = '' }: { hours: StoreHours; className?: string }) {
  return (
    <div className={`flex items-start gap-3 rounded-xl bg-[#fff7df] px-4 py-3 text-sm text-[#8a6100] ${className}`}>
      <Moon className="mt-0.5 size-4 shrink-0" />
      <p>
        <b>{hours.paused ? 'Nhà hàng đang tạm đóng cửa.' : 'Nhà hàng đã đóng cửa.'}</b>{' '}
        {hours.reopens ? `Mở lại lúc ${hours.reopens}. Bạn vẫn có thể xem thực đơn và chọn món trước.` : 'Vui lòng quay lại sau nhé.'}
      </p>
    </div>
  )
}

const formatCount =(n: number) => (n >= 1000 ? `${(n / 1000).toFixed(1).replace('.0', '')}k` : String(n))

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

// !inner: dishes of hidden (inactive) restaurants drop out, since RLS hides the restaurant row
export const FOOD_SELECT = '*, restaurants:fg_restaurants!inner(id, name, delivery_time, distance_km, lat, lng, is_open, open_time, close_time)'

/** Real distance from the customer's detected location, falling back to the stored estimate. */
export function useDistanceLabel(r: { distance_km: number; lat: number | null; lng: number | null } | null | undefined) {
  const { place } = useLocation()
  if (!r) return ''
  return place && hasCoords(r) ? formatKm(distanceKm(place, r)) : `${r.distance_km} km`
}
