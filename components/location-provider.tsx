'use client'

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { useApp } from '@/components/app-provider'
import { getCurrentPosition, reverseGeocode, type Place } from '@/lib/geo'

type Status = 'idle' | 'locating' | 'ready' | 'error'
type LocationValue = {
  place: Place | null
  status: Status
  error: string
  locate: () => Promise<Place | null>
  /** the customer moved the pin by hand */
  choose: (p: Place) => void
  /** full-screen "định vị" map (opens by itself right after sign-in) */
  pickerOpen: boolean
  setPickerOpen: (open: boolean) => void
}

const LocationContext = createContext<LocationValue | null>(null)
const KEY = 'foodgo-location'
const MAX_AGE_MS = 10 * 60 * 1000
const INTRO_KEY = 'foodgo-location-intro'

export function useLocation() {
  const ctx = useContext(LocationContext)
  if (!ctx) throw new Error('useLocation must be used inside <LocationProvider>')
  return ctx
}

/** Detects the customer's current address once they are signed in (cached for the session). */
export function LocationProvider({ children }: { children: React.ReactNode }) {
  const { user, authLoading } = useApp()
  const [place, setPlace] = useState<Place | null>(null)
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState('')
  const [pickerOpen, setPickerOpen] = useState(false)
  const asked = useRef(false)

  const save = useCallback((next: Place) => {
    setPlace(next); setStatus('ready')
    try { sessionStorage.setItem(KEY, JSON.stringify({ ...next, savedAt: Date.now() })) } catch {}
  }, [])

  const locate = useCallback(async () => {
    setStatus('locating'); setError('')
    try {
      const coords = await getCurrentPosition()
      const address = await reverseGeocode(coords).catch(() => `${coords.lat.toFixed(5)}, ${coords.lng.toFixed(5)}`)
      const next: Place = { ...coords, address }
      save(next)
      return next
    } catch (e) {
      setStatus('error'); setError(e instanceof Error ? e.message : 'Không xác định được vị trí')
      return null
    }
  }, [save])

  // a pin placed by hand is exact
  const choose = useCallback((p: Place) => save({ ...p, accuracy: 0 }), [save])

  useEffect(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as (Place & { savedAt?: number }) | null
      // only reuse a recent fix – the customer may have moved since
      if (saved && Date.now() - (saved.savedAt ?? 0) < MAX_AGE_MS) { setPlace(saved); setStatus('ready'); asked.current = true }
    } catch {}
  }, [])

  // right after sign-in (once per session): open the map and locate the customer
  useEffect(() => {
    // signed out: the next sign-in greets with the map again
    if (!user && !authLoading) {
      asked.current = false
      try { sessionStorage.removeItem(INTRO_KEY) } catch {}
    }
    if (!user || asked.current) return
    asked.current = true
    locate()
    // the map greets the customer once per app session, not on every reload
    try {
      if (!sessionStorage.getItem(INTRO_KEY)) { sessionStorage.setItem(INTRO_KEY, '1'); setPickerOpen(true) }
    } catch { setPickerOpen(true) }
  }, [user, authLoading, locate])

  return <LocationContext.Provider value={{ place, status, error, locate, choose, pickerOpen, setPickerOpen }}>{children}</LocationContext.Provider>
}
