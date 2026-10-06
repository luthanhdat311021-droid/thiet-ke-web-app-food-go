'use client'

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { useApp } from '@/components/app-provider'
import { getCurrentPosition, reverseGeocode, type Place } from '@/lib/geo'

type Status = 'idle' | 'locating' | 'ready' | 'error'
type LocationValue = { place: Place | null; status: Status; error: string; locate: () => Promise<Place | null> }

const LocationContext = createContext<LocationValue | null>(null)
const KEY = 'foodgo-location'
const MAX_AGE_MS = 10 * 60 * 1000

export function useLocation() {
  const ctx = useContext(LocationContext)
  if (!ctx) throw new Error('useLocation must be used inside <LocationProvider>')
  return ctx
}

/** Detects the customer's current address once they are signed in (cached for the session). */
export function LocationProvider({ children }: { children: React.ReactNode }) {
  const { user } = useApp()
  const [place, setPlace] = useState<Place | null>(null)
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState('')
  const asked = useRef(false)

  const locate = useCallback(async () => {
    setStatus('locating'); setError('')
    try {
      const coords = await getCurrentPosition()
      const address = await reverseGeocode(coords).catch(() => `${coords.lat.toFixed(5)}, ${coords.lng.toFixed(5)}`)
      const next: Place = { ...coords, address }
      setPlace(next); setStatus('ready')
      try { sessionStorage.setItem(KEY, JSON.stringify({ ...next, savedAt: Date.now() })) } catch {}
      return next
    } catch (e) {
      setStatus('error'); setError(e instanceof Error ? e.message : 'Không xác định được vị trí')
      return null
    }
  }, [])

  useEffect(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as (Place & { savedAt?: number }) | null
      // only reuse a recent fix – the customer may have moved since
      if (saved && Date.now() - (saved.savedAt ?? 0) < MAX_AGE_MS) { setPlace(saved); setStatus('ready'); asked.current = true }
    } catch {}
  }, [])

  // ask for the location right after sign-in (once per session)
  useEffect(() => {
    if (user && !asked.current) { asked.current = true; locate() }
  }, [user, locate])

  return <LocationContext.Provider value={{ place, status, error, locate }}>{children}</LocationContext.Provider>
}
