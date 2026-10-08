'use client'

import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import { Circle, MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import type { LatLng } from '@/lib/geo'
import { ATTRIBUTION, DEFAULT_CENTER, TILES } from './leaflet-map'

/** Flies to `target` whenever it changes; reports the centre when the customer drags / zooms the map. */
function Controller({ target, onDragStart, onSettle }: { target: LatLng | null; onDragStart: () => void; onSettle: (c: LatLng) => void }) {
  const map = useMap()
  const userMove = useRef(false)
  const key = target ? `${target.lat},${target.lng}` : ''
  // the bottom sheet changes height (locating → address): keep the same point under the centre pin
  useEffect(() => {
    const ro = new ResizeObserver(() => map.invalidateSize({ pan: true, animate: false }))
    ro.observe(map.getContainer())
    return () => ro.disconnect()
  }, [map])
  useEffect(() => {
    if (!target) return
    userMove.current = false
    map.flyTo([target.lat, target.lng], 17, { duration: 1.8, easeLinearity: 0.2 })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  useMapEvents({
    dragstart: () => { userMove.current = true; onDragStart() },
    zoomstart: () => { if (userMove.current) onDragStart() },
    moveend: () => {
      if (!userMove.current) return
      const c = map.getCenter()
      onSettle({ lat: c.lat, lng: c.lng })
    },
  })
  return null
}

/** Full-bleed map behind the centre pin of the "định vị" screen (the pin itself is drawn by the caller). */
export default function LocateMap({ target, accuracy, onDragStart, onSettle }: {
  target: LatLng | null
  /** metres; drawn as a soft circle while the pin is still the device's own fix */
  accuracy?: number | null
  onDragStart: () => void
  onSettle: (c: LatLng) => void
}) {
  return (
    <MapContainer center={DEFAULT_CENTER} zoom={13} zoomControl={false} attributionControl className="z-0 h-full w-full">
      <TileLayer url={TILES} attribution={ATTRIBUTION} maxZoom={19} />
      {target && accuracy != null && accuracy > 20 && (
        <Circle center={[target.lat, target.lng]} radius={Math.min(accuracy, 2000)} pathOptions={{ color: '#ff5b35', weight: 1, fillColor: '#ff5b35', fillOpacity: 0.08 }} />
      )}
      <Controller target={target} onDragStart={onDragStart} onSettle={onSettle} />
    </MapContainer>
  )
}
