'use client'

import { useEffect, useState } from 'react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import { FOOD_SELECT } from '@/lib/store'
import type { Food, Recommendation } from '@/lib/types'

// Behaviour history feeding the recommender (see supabase/migrations/018_recommendations.sql).
// Signed in: stored server-side by fg_track(). Guests: kept on this device and sent along with each request.
const RECENT_KEY = 'foodgo-recent'
type Recent = { foods: number[]; queries: string[] }

function readRecent(): Recent {
  try {
    const r = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '{}')
    return { foods: Array.isArray(r.foods) ? r.foods : [], queries: Array.isArray(r.queries) ? r.queries : [] }
  } catch { return { foods: [], queries: [] } }
}

export type TrackKind = 'view' | 'search' | 'cart'

/** Fire-and-forget: a failed log must never get in the customer's way. */
export function track(kind: TrackKind, value: number | string, signedIn: boolean) {
  if (!isSupabaseConfigured) return
  if (signedIn) {
    supabase.rpc('fg_track', kind === 'search' ? { p_kind: kind, p_query: value } : { p_kind: kind, p_food_id: value }).then(() => {}, () => {})
    return
  }
  const r = readRecent()
  if (kind === 'search') {
    const q = String(value).trim().slice(0, 80)
    if (q) r.queries = [q, ...r.queries.filter(x => x.toLowerCase() !== q.toLowerCase())].slice(0, 10)
  } else {
    const id = Number(value)
    r.foods = [id, ...r.foods.filter(x => x !== id)].slice(0, 20)
  }
  try { localStorage.setItem(RECENT_KEY, JSON.stringify(r)) } catch {}
}

/** Loads the dishes behind an RPC's [{food_id, reason}] rows, keeping the RPC's order. */
async function withFoods(rows: { food_id: number; reason: string }[] | null): Promise<Recommendation[]> {
  if (!rows?.length) return []
  const { data } = await supabase.from('fg_foods').select(FOOD_SELECT).in('id', rows.map(r => r.food_id))
  const byId = new Map(((data ?? []) as Food[]).map(f => [f.id, f]))
  return rows.flatMap(r => {
    const food = byId.get(r.food_id)
    return food ? [{ food, reason: r.reason }] : []
  })
}

/** "Gợi ý cho bạn": null while loading. Pass the user id so the list follows sign-in / sign-out. */
export function useRecommendations(userId: string | null | undefined, ready: boolean, limit = 12) {
  const [list, setList] = useState<Recommendation[] | null>(null)
  useEffect(() => {
    if (!ready) return
    if (!isSupabaseConfigured) { setList([]); return }
    let alive = true
    const recent = userId ? { foods: [], queries: [] } : readRecent()
    supabase.rpc('fg_recommend_foods', { p_limit: limit, p_recent_food_ids: recent.foods, p_recent_queries: recent.queries })
      .then(({ data }) => withFoods(data))
      .then(r => { if (alive) setList(r) }, () => { if (alive) setList([]) })
    return () => { alive = false }
  }, [userId, ready, limit])
  return list
}

/** "Có thể bạn cũng thích" for one dish: null while loading. */
export function useSimilarFoods(foodId: number, limit = 8) {
  const [list, setList] = useState<Recommendation[] | null>(null)
  useEffect(() => {
    if (!isSupabaseConfigured) { setList([]); return }
    let alive = true
    setList(null)
    supabase.rpc('fg_similar_foods', { p_food_id: foodId, p_limit: limit })
      .then(({ data }) => withFoods(data))
      .then(r => { if (alive) setList(r) }, () => { if (alive) setList([]) })
    return () => { alive = false }
  }, [foodId, limit])
  return list
}
