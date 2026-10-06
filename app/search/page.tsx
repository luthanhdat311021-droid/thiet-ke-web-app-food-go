'use client'

import { Suspense, useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { SearchX } from 'lucide-react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Category, Food, Restaurant } from '@/lib/types'
import { CardSkeleton, EmptyState, FoodCard, FOOD_SELECT, RestaurantCard, Spinner } from '@/components/cards'

const SORTS = [
  { value: 'popular', label: 'Phổ biến nhất' },
  { value: 'rating', label: 'Đánh giá cao' },
  { value: 'price_asc', label: 'Giá thấp → cao' },
  { value: 'price_desc', label: 'Giá cao → thấp' },
]

export default function SearchPage() {
  return <Suspense fallback={<Spinner />}><SearchContent /></Suspense>
}

function SearchContent() {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const q = params.get('q') ?? ''
  const categoryId = params.get('category')
  const sort = params.get('sort') ?? 'popular'
  const tab = params.get('tab') === 'restaurants' ? 'restaurants' : 'foods'

  const [categories, setCategories] = useState<Category[]>([])
  const [foods, setFoods] = useState<Food[] | null>(null)
  const [restaurants, setRestaurants] = useState<Restaurant[] | null>(null)

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    router.replace(`${pathname}?${next}`, { scroll: false })
  }

  useEffect(() => {
    if (!isSupabaseConfigured) return
    supabase.from('fg_categories').select('*').order('sort').then(({ data }) => setCategories((data ?? []) as Category[]))
  }, [])

  useEffect(() => {
    if (!isSupabaseConfigured) { setFoods([]); setRestaurants([]); return }
    setFoods(null); setRestaurants(null)
    // strip characters that would break PostgREST's or() filter syntax
    const term = q.replace(/[,()%*\\]/g, ' ').trim()

    let fq = supabase.from('fg_foods').select(FOOD_SELECT)
    if (term) fq = fq.or(`name.ilike.%${term}%,description.ilike.%${term}%`)
    if (categoryId) fq = fq.eq('category_id', Number(categoryId))
    fq = sort === 'rating' ? fq.order('rating', { ascending: false })
      : sort === 'price_asc' ? fq.order('price')
      : sort === 'price_desc' ? fq.order('price', { ascending: false })
      : fq.order('sold_count', { ascending: false })
    fq.limit(60).then(({ data }) => setFoods((data ?? []) as Food[]))

    let rq = supabase.from('fg_restaurants').select('*')
    if (term) rq = rq.or(`name.ilike.%${term}%,cuisine.ilike.%${term}%`)
    rq.order(sort === 'rating' ? 'rating' : 'review_count', { ascending: false }).limit(30)
      .then(({ data }) => setRestaurants((data ?? []) as Restaurant[]))
  }, [q, categoryId, sort])

  const list = tab === 'foods' ? foods : restaurants

  return (
    <main className="mx-auto max-w-[1400px] px-5 pb-24 pt-8 lg:px-10">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-[#ff5b35]">KHÁM PHÁ</p>
          <h1 className="mt-2 text-3xl font-extrabold">{q ? <>Kết quả cho “{q}”</> : 'Nhà hàng & món ăn'}</h1>
          <p className="mt-2 text-sm text-[#746b67]">
            {list === null ? 'Đang tìm...' : `Tìm thấy ${list.length} ${tab === 'foods' ? 'món ăn' : 'nhà hàng'} phù hợp`}
          </p>
        </div>
        <select aria-label="Sắp xếp" value={sort} onChange={e => update({ sort: e.target.value })} className="h-11 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
          {SORTS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>

      <div className="mt-6 flex gap-2 border-b border-[#eaded8]">
        {(['foods', 'restaurants'] as const).map(t => (
          <button key={t} onClick={() => update({ tab: t === 'foods' ? null : t })} className={`border-b-2 px-4 pb-3 text-sm font-bold ${tab === t ? 'border-[#ff5b35] text-[#ff5b35]' : 'border-transparent text-[#746b67]'}`}>
            {t === 'foods' ? `Món ăn${foods ? ` (${foods.length})` : ''}` : `Nhà hàng${restaurants ? ` (${restaurants.length})` : ''}`}
          </button>
        ))}
      </div>

      {tab === 'foods' && (
        <div className="mt-6 flex gap-2 overflow-x-auto pb-2">
          <Chip active={!categoryId} onClick={() => update({ category: null })}>Tất cả</Chip>
          {categories.map(c => <Chip key={c.id} active={categoryId === String(c.id)} onClick={() => update({ category: String(c.id) })}>{c.name}</Chip>)}
        </div>
      )}

      <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {list === null && <CardSkeleton count={4} />}
        {tab === 'foods' && foods?.map(f => <FoodCard key={f.id} food={f} />)}
        {tab === 'restaurants' && restaurants?.map(r => <RestaurantCard key={r.id} restaurant={r} />)}
      </div>
      {list?.length === 0 && (
        <EmptyState icon={<SearchX />} title="Không tìm thấy kết quả">Thử từ khóa khác hoặc bỏ bớt bộ lọc.</EmptyState>
      )}
    </main>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} className={`whitespace-nowrap rounded-full px-5 py-2.5 text-sm font-semibold ${active ? 'bg-[#ff5b35] text-white' : 'bg-white text-[#746b67] shadow-sm'}`}>{children}</button>
  )
}
