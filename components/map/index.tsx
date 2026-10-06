'use client'

import dynamic from 'next/dynamic'

// Leaflet touches `window` on import, so the map only loads in the browser
export const FoodMap = dynamic(() => import('./leaflet-map'), {
  ssr: false,
  loading: () => <div className="h-72 w-full animate-pulse bg-[#e8efe9]" />,
})

export type { MapMarker } from './leaflet-map'
