'use client'

import { Suspense, useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { SearchX } from 'lucide-react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Category, Food, Restaurant } from '@/lib/types'
import { CardSkeleton, EmptyState, FoodCard, FOOD_SELECT, Spinner } from '@/components/cards'

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

  const [categories, setCategories] = useState<Category[]>([])
  const [foods, setFoods] = useState<Food[] | null>(null)

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
    if (!isSupabaseConfigured) { setFoods([]); return }
    setFoods(null)
    // strip characters that would break PostgREST's or() filter syntax
    const term = q.replace(/[,()%*\\]/g, ' ').trim()

    let fq = supabase.from('fg_foods').select(FOOD_SELECT)
    if (term) fq = fq.or(`name.ilike.%${term}%,description.ilike.%${term}%`)
    if (categoryId) fq = fq.eq('category_id', Number(categoryId))
    fq = sort === 'rating' ? fq.order('rating', { ascending: false })
      : sort === 'price_asc' ? fq.order('price')
      : sort === 'price_desc' ? fq.order('price', { ascending: false })
      : fq.order('sold_count', { ascending: false })
    fq.limit(100).then(({ data }) => setFoods((data ?? []) as Food[]))
  }, [q, categoryId, sort])

  const list = foods

  return (
    <main className="mx-auto max-w-[1400px] px-5 pb-24 pt-8 lg:px-10">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-[#ff5b35]">TÌM MÓN</p>
          <h1 className="mt-2 text-3xl font-extrabold">{q ? <>Kết quả cho “{q}”</> : 'Tất cả món ăn'}</h1>
          <p className="mt-2 text-sm text-[#746b67]">
            {list === null ? 'Đang tìm...' : `Tìm thấy ${list.length} món ăn phù hợp`}
          </p>
        </div>
        <select aria-label="Sắp xếp" value={sort} onChange={e => update({ sort: e.target.value })} className="h-11 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
          {SORTS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>

      <div className="mt-6 flex gap-2 overflow-x-auto pb-2">
        <Chip active={!categoryId} onClick={() => update({ category: null })}>Tất cả</Chip>
        {categories.map(c => <Chip key={c.id} active={categoryId === String(c.id)} onClick={() => update({ category: String(c.id) })}>{c.name}</Chip>)}
      </div>

      <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {list === null && <CardSkeleton count={4} />}
        {foods?.map(f => <FoodCard key={f.id} food={f} />)}
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
