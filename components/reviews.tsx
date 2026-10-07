'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Heart, Loader2, MessageSquareText, Minus, Plus, Star, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { money, timeAgo } from '@/lib/format'
import type { Food, Order, Review } from '@/lib/types'

/** "★ 4.8 (12)" or "Chưa có đánh giá" – ratings come only from customer reviews. */
export function RatingBadge({ rating, count, onClick, className = '' }: { rating: number; count: number; onClick?: () => void; className?: string }) {
  const content = count > 0
    ? <><Star className="size-3 fill-[#ffb21c] text-[#ffb21c]" />{Number(rating).toFixed(1)}<span className="font-normal text-[#9c918c]">({count})</span></>
    : <span className="font-normal text-[#9c918c]">Chưa có đánh giá</span>
  const cls = `inline-flex shrink-0 items-center gap-1 text-xs font-bold ${className}`
  return onClick
    ? <button type="button" onClick={onClick} className={`${cls} -m-1 rounded p-1 hover:bg-[#fff5f1]`}>{content}</button>
    : <span className={cls}>{content}</span>
}

export function Stars({ value, onChange, size = 'size-7' }: { value: number; onChange?: (v: number) => void; size?: string }) {
  return (
    <div className="flex gap-1" role={onChange ? 'radiogroup' : undefined} aria-label="Số sao">
      {[1, 2, 3, 4, 5].map(n => {
        const star = <Star className={`${size} ${n <= value ? 'fill-[#ffb21c] text-[#ffb21c]' : 'text-[#e4dad5]'}`} />
        return onChange
          ? <button key={n} type="button" role="radio" aria-checked={n === value} aria-label={`${n} sao`} onClick={() => onChange(n)} className="p-0.5">{star}</button>
          : <span key={n}>{star}</span>
      })}
    </div>
  )
}

const LABELS = ['', 'Rất tệ', 'Chưa ngon', 'Bình thường', 'Ngon', 'Tuyệt vời']

/** Dish detail sheet (opened from the menu / food cards): photo, info, add to cart and customer reviews. */
export function FoodDetailSheet({ food, onClose }: { food: Food; onClose: () => void }) {
  const { cart, setQty, addToCart, favoriteIds, toggleFavorite } = useApp()
  const [reviews, setReviews] = useState<Review[] | null>(null)
  const inCart = cart.find(x => x.food_id === food.id)
  const liked = favoriteIds.includes(food.id)

  useEffect(() => {
    supabase.from('fg_reviews').select('*').eq('food_id', food.id).order('created_at', { ascending: false }).limit(50)
      .then(({ data }) => setReviews((data ?? []) as Review[]))
  }, [food.id])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // portal: callers sit inside cards with hover transforms, which would trap a `fixed` overlay
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/30 sm:items-center" onClick={onClose}>
      <div role="dialog" aria-label={food.name} onClick={e => e.stopPropagation()}
        className="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-t-3xl bg-white sm:rounded-3xl">
        <button aria-label="Đóng" onClick={onClose} className="absolute right-4 top-4 z-10 grid size-10 place-items-center rounded-full bg-white/90 shadow-sm"><X /></button>
        <div className="flex-1 overflow-y-auto">
          {food.image
            ? <img src={food.image} alt={food.name} className={`h-60 w-full object-cover sm:h-72 ${food.is_available ? '' : 'grayscale'}`} />
            : <div className="h-16" />}
          <div className="p-5">
            <div className="flex items-start justify-between gap-3">
              <h2 className="text-2xl font-extrabold">{food.name}</h2>
              <button aria-label={liked ? 'Bỏ yêu thích' : 'Yêu thích'} onClick={() => toggleFavorite(food.id)} className="-m-1 p-1 text-[#ff5b35]"><Heart className={`size-6 ${liked ? 'fill-current' : ''}`} /></button>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
              <RatingBadge rating={food.rating} count={food.review_count ?? 0} className="text-sm" />
              {food.sold_count > 0 && <span className="text-xs text-[#9c918c]">Đã bán {food.sold_count}</span>}
              {!food.is_available && <span className="rounded-full bg-[#f4f0ee] px-2 py-0.5 text-xs font-bold text-[#746b67]">Tạm hết</span>}
            </div>
            {food.description && <p className="mt-3 whitespace-pre-line text-sm text-[#4a403c]">{food.description}</p>}
            <div className="mt-4">
              <b className="text-2xl text-[#ff5b35]">{money(food.price)}</b>
              {food.old_price && food.old_price > food.price && <del className="ml-2 text-sm text-[#aaa09b]">{money(food.old_price)}</del>}
            </div>

            <h3 className="mt-6 border-t border-[#f1e7e2] pt-5 font-extrabold">Đánh giá từ khách hàng</h3>
            <div className="mt-4">
          {reviews === null && <p className="py-8 text-center text-sm text-[#9c918c]"><Loader2 className="mx-auto size-5 animate-spin text-[#ff5b35]" /></p>}
          {reviews?.length === 0 && (
            <div className="py-6 text-center text-sm text-[#9c918c]">
              <MessageSquareText className="mx-auto mb-2 size-8 text-[#ffb9a5]" />
              Món này chưa có đánh giá. Đặt món và là người đầu tiên đánh giá nhé!
            </div>
          )}
          <div className="flex flex-col gap-4">
            {reviews?.map(r => (
              <div key={r.id} className="border-b border-[#f8f3f0] pb-4 last:border-0">
                <div className="flex items-center justify-between gap-2">
                  <b className="text-sm">{r.reviewer_name}</b>
                  <span className="text-xs text-[#9c918c]">{timeAgo(r.updated_at)}</span>
                </div>
                <div className="mt-1 flex items-center gap-2"><Stars value={r.rating} size="size-3.5" /><span className="text-xs text-[#746b67]">{LABELS[r.rating]}</span></div>
                {r.comment && <p className="mt-2 text-sm text-[#4a403c]">{r.comment}</p>}
              </div>
            ))}
          </div>
            </div>
          </div>
        </div>
        <div className="border-t border-[#f1e7e2] p-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          {!food.is_available ? (
            <div className="flex h-12 items-center justify-center rounded-xl bg-[#f4f0ee] text-sm font-bold text-[#9c918c]">Món tạm hết</div>
          ) : inCart ? (
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <button aria-label="Giảm" onClick={() => setQty(food.id, inCart.qty - 1)} className="grid size-11 place-items-center rounded-xl bg-[#f8f3f0]"><Minus className="size-4" /></button>
                <span className="w-6 text-center font-bold">{inCart.qty}</span>
                <button aria-label="Tăng" onClick={() => setQty(food.id, inCart.qty + 1)} className="grid size-11 place-items-center rounded-xl bg-[#ff5b35] text-white"><Plus className="size-4" /></button>
              </div>
              <b className="text-[#ff5b35]">{money(food.price * inCart.qty)}</b>
            </div>
          ) : (
            <Button onClick={() => addToCart(food)} className="h-12 w-full rounded-xl bg-[#ff5b35] hover:bg-[#e94c29]"><Plus />Thêm vào giỏ • {money(food.price)}</Button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}

/** On a delivered order: rate each dish (1–5 stars + optional comment); can be edited later. */
export function OrderReviewPanel({ order }: { order: Order }) {
  const { toast } = useApp()
  const [mine, setMine] = useState<Record<number, Review>>({})
  const [draft, setDraft] = useState<Record<number, { rating: number; comment: string }>>({})
  const [editing, setEditing] = useState<number | null>(null)
  const [busy, setBusy] = useState<number | null>(null)

  // one row per dish (an order can list the same dish once)
  const items = (order.order_items ?? []).filter(i => i.food_id !== null)

  useEffect(() => {
    supabase.from('fg_reviews').select('*').eq('order_id', order.id).then(({ data }) => {
      const map: Record<number, Review> = {}
      for (const r of (data ?? []) as Review[]) map[r.food_id] = r
      setMine(map)
    })
  }, [order.id])

  if (order.status !== 'delivered' || items.length === 0) return null

  const submit = async (foodId: number) => {
    const d = draft[foodId]
    if (!d?.rating) return toast('Vui lòng chọn số sao', 'error')
    setBusy(foodId)
    const { data, error } = await supabase.rpc('fg_submit_review', { p_order_id: order.id, p_food_id: foodId, p_rating: d.rating, p_comment: d.comment })
    setBusy(null)
    if (error) return toast(errorMessage(error), 'error')
    setMine(m => ({ ...m, [foodId]: data as Review }))
    setEditing(null)
    toast('Cảm ơn bạn đã đánh giá!')
  }

  const pending = items.filter(i => !mine[i.food_id!]).length
  return (
    <section id="danh-gia" className="scroll-mt-24 rounded-2xl bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-lg font-extrabold">Đánh giá món ăn</h2>
        {pending > 0 && <span className="rounded-full bg-[#fff0eb] px-3 py-1 text-xs font-bold text-[#ff5b35]">{pending} món chờ đánh giá</span>}
      </div>
      <div className="flex flex-col gap-4">
        {items.map(i => {
          const foodId = i.food_id!
          const done = mine[foodId]
          const open = !done || editing === foodId
          const d = draft[foodId] ?? { rating: done?.rating ?? 0, comment: done?.comment ?? '' }
          const set = (patch: Partial<typeof d>) => setDraft(x => ({ ...x, [foodId]: { ...d, ...patch } }))
          return (
            <div key={i.id} className="rounded-xl border border-[#f1e7e2] p-4">
              <div className="flex items-center gap-3">
                {i.image && <img src={i.image} alt="" className="size-12 rounded-lg object-cover" />}
                <b className="min-w-0 flex-1 truncate text-sm">{i.name}</b>
                {done && !open && (
                  <button onClick={() => { setDraft(x => ({ ...x, [foodId]: { rating: done.rating, comment: done.comment ?? '' } })); setEditing(foodId) }}
                    className="shrink-0 py-1 text-xs font-bold text-[#ff5b35]">Sửa</button>
                )}
              </div>
              {done && !open ? (
                <div className="mt-3">
                  <div className="flex items-center gap-2"><Stars value={done.rating} size="size-4" /><span className="text-xs text-[#746b67]">{LABELS[done.rating]}</span></div>
                  {done.comment && <p className="mt-1 text-sm text-[#4a403c]">{done.comment}</p>}
                </div>
              ) : (
                <div className="mt-3 flex flex-col gap-3">
                  <div className="flex flex-wrap items-center gap-3">
                    <Stars value={d.rating} onChange={v => set({ rating: v })} />
                    {d.rating > 0 && <span className="text-sm font-semibold text-[#ff5b35]">{LABELS[d.rating]}</span>}
                  </div>
                  <textarea value={d.comment} onChange={e => set({ comment: e.target.value })} maxLength={500} rows={2}
                    placeholder="Món ăn thế nào? (không bắt buộc)" aria-label={`Nhận xét về ${i.name}`}
                    className="w-full rounded-xl border border-[#eaded8] px-3 py-2 text-sm outline-none focus:border-[#ff5b35]" />
                  <div className="flex gap-2">
                    <Button onClick={() => submit(foodId)} disabled={busy === foodId || !d.rating} className="h-10 rounded-xl bg-[#ff5b35] px-5 hover:bg-[#e94c29]">
                      {busy === foodId && <Loader2 className="animate-spin" />}{done ? 'Lưu đánh giá' : 'Gửi đánh giá'}
                    </Button>
                    {done && <Button variant="outline" onClick={() => setEditing(null)} className="h-10 rounded-xl px-4">Hủy</Button>}
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}
