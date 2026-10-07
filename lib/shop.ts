'use client'

import { useCallback, useEffect, useState } from 'react'
import { supabase } from '@/lib/supabase'
import type { Restaurant } from '@/lib/types'

/** Monthly fee for a restaurant run by its owner (same as fg_subscription_fee() in the DB). */
export const SUBSCRIPTION_FEE = 200000
export const SUBSCRIPTION_MONTHS = [1, 3, 6, 12] as const

/** The signed-in user's own restaurant: undefined while loading, null if they have none. */
export function useMyRestaurant(userId: string | null | undefined) {
  const [restaurant, setRestaurant] = useState<Restaurant | null | undefined>(undefined)
  const reload = useCallback(async () => {
    if (!userId) { setRestaurant(null); return }
    const { data } = await supabase.from('fg_restaurants').select('*').eq('owner_id', userId).maybeSingle()
    setRestaurant(data as Restaurant | null)
  }, [userId])
  useEffect(() => { reload() }, [reload])
  return { restaurant, reload }
}

export type Subscription = { active: boolean; daysLeft: number; until: Date | null }

/** Admin-run restaurants (no owner) never expire. */
export function subscriptionOf(r: Pick<Restaurant, 'owner_id' | 'paid_until'>): Subscription {
  if (!r.owner_id) return { active: true, daysLeft: Infinity, until: null }
  const until = r.paid_until ? new Date(r.paid_until) : null
  const ms = until ? until.getTime() - Date.now() : -1
  return { active: ms > 0, daysLeft: Math.max(0, Math.ceil(ms / 86400000)), until }
}
