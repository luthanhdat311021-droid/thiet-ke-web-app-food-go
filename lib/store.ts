'use client'

import { useEffect, useState } from 'react'
import { isSupabaseConfigured, supabase } from '@/lib/supabase'
import type { Restaurant } from '@/lib/types'

// FoodGo serves a single shop: the one row of fg_restaurants holds its name, address and location.
let cache: Promise<Restaurant | null> | null = null

export function fetchStore(fresh = false) {
  if (!isSupabaseConfigured) return Promise.resolve(null)
  if (!cache || fresh) {
    cache = Promise.resolve(supabase.from('fg_restaurants').select('*').order('id').limit(1).maybeSingle())
      .then(({ data }) => (data as Restaurant | null))
  }
  return cache
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

/** undefined while loading, null if the shop isn't set up yet. */
export function useStore() {
  const [store, setStore] = useState<Restaurant | null | undefined>(undefined)
  useEffect(() => { fetchStore().then(setStore) }, [])
  return store
}
