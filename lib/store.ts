'use client'

import { useEffect, useState } from 'react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Restaurant } from '@/lib/types'

// Restaurants change rarely: one shared list per page load (RLS hides inactive ones from customers).
let cache: Promise<Restaurant[]> | null = null

export function fetchRestaurants(fresh = false) {
  if (!isSupabaseConfigured) return Promise.resolve([])
  if (!cache || fresh) {
    cache = Promise.resolve(supabase.from('fg_restaurants').select('*').order('id'))
      .then(({ data }) => (data ?? []) as Restaurant[])
  }
  return cache
}

/** Always fresh: hours / "tạm đóng cửa" may have just changed. */
export async function fetchRestaurant(id: number) {
  if (!isSupabaseConfigured) return null
  const { data } = await supabase.from('fg_restaurants').select('*').eq('id', id).maybeSingle()
  return data as Restaurant | null
}

/** undefined while loading. */
export function useRestaurants() {
  const [list, setList] = useState<Restaurant[] | undefined>(undefined)
  useEffect(() => { fetchRestaurants().then(setList) }, [])
  return list
}

/** undefined while loading, null if it doesn't exist (or is hidden). */
export function useRestaurant(id: number | null | undefined) {
  const [r, setR] = useState<Restaurant | null | undefined>(undefined)
  useEffect(() => {
    if (!id) { setR(null); return }
    setR(undefined)
    fetchRestaurant(id).then(setR)
  }, [id])
  return r
}

const hhmm = (t: string) => t.slice(0, 5)

/** Current time-of-day in Vietnam as "HH:MM", whatever the device's timezone. */
function vnNow() {
  return new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date())
}

export type StoreHours = { open: boolean; paused: boolean; hours: string | null; reopens: string | null }

/** Same rule as fg_store_open() in the DB (which has the final say when ordering). */
export function storeHours(s: Pick<Restaurant, 'is_open' | 'open_time' | 'close_time'>): StoreHours {
  const paused = s.is_open === false
  if (!s.open_time || !s.close_time || hhmm(s.open_time) === hhmm(s.close_time)) {
    return { open: !paused, paused, hours: null, reopens: null }
  }
  const [o, c, t] = [hhmm(s.open_time), hhmm(s.close_time), vnNow()]
  const inHours = o < c ? t >= o && t < c : t >= o || t < c
  return { open: !paused && inHours, paused, hours: `${o} - ${c}`, reopens: paused ? null : o }
}

/** Re-evaluates every minute so the "đang mở / đã đóng" badge flips on time. */
export function useStoreHours(s: Restaurant | null | undefined) {
  const [, tick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => tick(n => n + 1), 60000)
    return () => clearInterval(id)
  }, [])
  return s ? storeHours(s) : null
}

/** Same as useStoreHours for a whole list (one timer). */
export function useClock() {
  const [, tick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => tick(n => n + 1), 60000)
    return () => clearInterval(id)
  }, [])
}
