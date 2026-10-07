'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Category, Food } from '@/lib/types'
import { storeHours, useRestaurants } from '@/lib/store'
import { useLocation } from '@/components/location-provider'
import { distanceKm, hasCoords } from '@/lib/geo'
import { CardSkeleton, FOOD_SELECT, FoodCard, RestaurantCard, SectionTitle } from '@/components/cards'

const HERO_IMAGE = 'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&q=85'

export default function HomePage() {
  const restaurants = useRestaurants()
  const { place } = useLocation()
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [foods, setFoods] = useState<Food[] | null>(null)

  useEffect(() => {
    if (!isSupabaseConfigured) { setCategories([]); setFoods([]); return }
    supabase.from('fg_categories').select('*').order('sort').then(({ data }) => setCategories((data ?? []) as Category[]))
    supabase.from('fg_foods').select(FOOD_SELECT).eq('is_popular', true).order('sold_count', { ascending: false }).limit(6)
      .then(({ data }) => setFoods((data ?? []) as Food[]))
  }, [])

  // open ones first, then nearest to the customer
  const nearby = useMemo(() => restaurants && [...restaurants]
    .map(r => ({ r, open: storeHours(r).open, km: place && hasCoords(r) ? distanceKm(place, r) : r.distance_km }))
    .sort((a, b) => Number(b.open) - Number(a.open) || a.km - b.km)
    .slice(0, 8).map(x => x.r), [restaurants, place])

  return (
    <main className="mx-auto max-w-[1400px] px-5 pb-24 pt-8 lg:px-10">
      <section className="relative overflow-hidden rounded-3xl bg-[#fff0e8] px-7 py-10 md:px-14 md:py-14">
        <div className="relative z-10 max-w-xl">
          <span className="rounded-full bg-white px-4 py-2 text-xs font-bold text-[#ff5b35]">GIAO HÀNG NHANH • TẬN TÂM</span>
          <h1 className="mt-5 text-4xl font-extrabold leading-tight tracking-tight md:text-6xl">Món ngon<br /><span className="text-[#ff5b35]">giao tận cửa</span></h1>
          <p className="mt-4 max-w-md text-base leading-7 text-[#746b67]">Cơm, gà rán, pizza, trà sữa… từ các nhà hàng quanh bạn, giao nhanh tận cửa.</p>
          <Link href="/menu" className="mt-7 inline-flex h-12 items-center gap-2 rounded-xl bg-[#ff5b35] px-6 text-sm font-semibold text-white hover:bg-[#e94c29]">Xem nhà hàng <ArrowRight className="size-4" /></Link>
          <img className="mt-8 h-48 w-full rounded-2xl object-cover md:hidden" src={`${HERO_IMAGE}&w=800`} alt="Món ăn Việt hấp dẫn" />
        </div>
        <div className="absolute -right-10 -top-16 hidden h-[130%] w-1/2 overflow-hidden rounded-full md:block">
          <img className="h-full w-full object-cover" src={`${HERO_IMAGE}&w=1000`} alt="Món ăn Việt hấp dẫn" />
        </div>
      </section>

      <section className="mt-12">
        <SectionTitle title="Bạn muốn ăn gì?" href="/search" action="Tất cả món" />
        <div className="mt-5 flex gap-4 overflow-x-auto pb-3">
          {categories === null && <CardSkeleton count={8} className="h-[124px] min-w-[100px]" />}
          {categories?.map(c => (
            <Link key={c.id} href={`/search?category=${c.id}`} className="group flex min-w-[100px] flex-col items-center gap-3 rounded-2xl bg-white p-3 shadow-sm transition hover:-translate-y-1 hover:shadow-md">
              <span className="size-16 overflow-hidden rounded-2xl bg-[#fff4ef]">
                {c.image && <img src={c.image} alt={c.name} loading="lazy" className="h-full w-full object-cover transition duration-300 group-hover:scale-110" />}
              </span>
              <span className="whitespace-nowrap text-xs font-semibold">{c.name}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="mt-12">
        <SectionTitle title={place ? 'Nhà hàng gần bạn' : 'Nhà hàng'} href="/menu" />
        <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {nearby === undefined ? <CardSkeleton count={4} className="h-60" /> : nearby.map(r => <RestaurantCard key={r.id} r={r} />)}
        </div>
      </section>

      <section className="mt-12">
        <SectionTitle title="Món bán chạy" href="/search" action="Tất cả món" />
        <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {foods === null ? <CardSkeleton count={3} /> : foods.map(f => <FoodCard key={f.id} food={f} />)}
        </div>
      </section>
    </main>
  )
}
