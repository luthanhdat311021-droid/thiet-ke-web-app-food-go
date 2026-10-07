'use client'

import { useMemo, useState } from 'react'
import { Search, Store } from 'lucide-react'
import { storeHours, useClock, useRestaurants } from '@/lib/store'
import { useLocation } from '@/components/location-provider'
import { distanceKm, hasCoords } from '@/lib/geo'
import { CardSkeleton, EmptyState, RestaurantCard } from '@/components/cards'

const SORTS = [
  { value: 'near', label: 'Gần tôi nhất' },
  { value: 'rating', label: 'Đánh giá cao' },
] as const

/** All restaurants: open ones first, then nearest (or best rated). */
export default function RestaurantsPage() {
  const restaurants = useRestaurants()
  const { place } = useLocation()
  const [q, setQ] = useState('')
  const [openOnly, setOpenOnly] = useState(false)
  const [sort, setSort] = useState<(typeof SORTS)[number]['value']>('near')
  useClock()

  const shown = useMemo(() => {
    if (!restaurants) return undefined
    const term = q.trim().toLowerCase()
    const dist = (r: (typeof restaurants)[number]) => place && hasCoords(r) ? distanceKm(place, r) : r.distance_km
    return restaurants
      .map(r => ({ r, open: storeHours(r).open, km: dist(r) }))
      .filter(x => (!openOnly || x.open) && (!term || `${x.r.name} ${x.r.cuisine ?? ''} ${x.r.address ?? ''}`.toLowerCase().includes(term)))
      .sort((a, b) => Number(b.open) - Number(a.open)
        || (sort === 'rating' ? b.r.rating - a.r.rating || b.r.review_count - a.r.review_count : a.km - b.km))
      .map(x => x.r)
  }, [restaurants, place, q, openOnly, sort])

  return (
    <main className="mx-auto max-w-[1400px] px-5 pb-24 pt-8 lg:px-10">
      <p className="text-sm font-semibold text-[#ff5b35]">NHÀ HÀNG</p>
      <h1 className="mt-2 text-3xl font-extrabold">Chọn nhà hàng</h1>
      <p className="mt-2 text-sm text-[#746b67]">
        {shown === undefined ? 'Đang tải...' : `${shown.length} nhà hàng${place ? ' • sắp xếp theo khoảng cách tới bạn' : ''}`}
      </p>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative min-w-0 flex-1 sm:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#9c918c]" />
          <input value={q} onChange={e => setQ(e.target.value)} placeholder="Tìm tên nhà hàng, món chính..." aria-label="Tìm nhà hàng"
            className="h-11 w-full rounded-xl border border-[#eaded8] bg-white pl-9 pr-3 text-sm outline-none focus:border-[#ff5b35]" />
        </div>
        <select aria-label="Sắp xếp" value={sort} onChange={e => setSort(e.target.value as typeof sort)} className="h-11 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
          {SORTS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
        <label className="flex h-11 cursor-pointer items-center gap-2 rounded-xl bg-white px-3 text-sm font-semibold shadow-sm">
          <input type="checkbox" checked={openOnly} onChange={e => setOpenOnly(e.target.checked)} className="size-4 accent-[#ff5b35]" />Đang mở cửa
        </label>
      </div>

      <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {shown === undefined && <CardSkeleton count={4} className="h-60" />}
        {shown?.map(r => <RestaurantCard key={r.id} r={r} />)}
      </div>
      {shown?.length === 0 && (
        <EmptyState icon={<Store />} title={restaurants?.length ? 'Không có nhà hàng phù hợp' : 'Chưa có nhà hàng nào'}>
          {restaurants?.length ? 'Thử từ khóa khác hoặc bỏ lọc "Đang mở cửa".' : null}
        </EmptyState>
      )}
    </main>
  )
}
