'use client'

import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { useEffect, useMemo } from 'react'
import { MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import type { LatLng } from '@/lib/geo'

export type MapMarker = { id: string; pos: [number, number]; kind: 'restaurant' | 'home' | 'driver'; label?: string }

const pin = (bg: string, glyph: string, size = 40) => L.divIcon({
  className: '',
  iconSize: [size, size],
  iconAnchor: [size / 2, size / 2],
  html: `<div style="width:${size}px;height:${size}px;border-radius:9999px;background:${bg};border:3px solid #fff;box-shadow:0 4px 12px rgba(0,0,0,.25);display:grid;place-items:center;font-size:${size * 0.45}px">${glyph}</div>`,
})
const ICONS = {
  restaurant: pin('#241c19', '🍽️'),
  home: pin('#ff5b35', '🏠'),
  driver: pin('#3eaa68', '🛵', 44),
}
// classic drop-pin for picking a delivery spot
const PICK_ICON = L.divIcon({
  className: '',
  iconSize: [36, 48],
  iconAnchor: [18, 46],
  html: `<svg width="36" height="48" viewBox="0 0 36 48"><path d="M18 0C8 0 0 8 0 18c0 13 18 30 18 30s18-17 18-30C36 8 28 0 18 0z" fill="#ff5b35"/><circle cx="18" cy="18" r="7" fill="#fff"/></svg>`,
})

// Esri World Street Map: no API key, Vietnamese street labels; attribution is required.
// (CARTO basemaps now demand an API key, and tile.openstreetmap.org rejects many app/WebView requests.)
const TILES = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}'
const ATTRIBUTION = 'Tiles &copy; <a href="https://www.esri.com">Esri</a> — Esri, HERE, Garmin, &copy; OpenStreetMap contributors'

function FitBounds({ points, padding }: { points: [number, number][]; padding: number }) {
  const map = useMap()
  const key = points.map(p => p.join(',')).join('|')
  useEffect(() => {
    if (points.length === 1) map.setView(points[0], Math.max(map.getZoom(), 16))
    else if (points.length > 1) map.fitBounds(L.latLngBounds(points), { padding: [padding, padding], maxZoom: 17 })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return null
}

function PickOnClick({ onPick }: { onPick: (p: LatLng) => void }) {
  useMapEvents({ click: e => onPick({ lat: e.latlng.lat, lng: e.latlng.lng }) })
  return null
}

export default function LeafletMap({ markers = [], route, picker, onPick, fitPoints, className = 'h-72', padding = 48 }: {
  markers?: MapMarker[]
  route?: [number, number][]
  /** draggable pin for choosing a location */
  picker?: LatLng | null
  onPick?: (p: LatLng) => void
  /** points the view should frame (defaults to all markers) */
  fitPoints?: [number, number][]
  className?: string
  padding?: number
}) {
  const fit = useMemo(
    () => fitPoints ?? [...markers.map(m => m.pos), ...(picker ? [[picker.lat, picker.lng] as [number, number]] : [])],
    [fitPoints, markers, picker],
  )
  const center = fit[0] ?? [10.9806, 106.6744] // Trường ĐH Thủ Dầu Một

  return (
    <MapContainer center={center} zoom={15} scrollWheelZoom={false} className={`z-0 w-full ${className}`} attributionControl>
      <TileLayer url={TILES} attribution={ATTRIBUTION} maxZoom={19} />
      <FitBounds points={fit} padding={padding} />
      {route && route.length > 1 && <Polyline positions={route} pathOptions={{ color: '#ff5b35', weight: 5, opacity: 0.85 }} />}
      {markers.map(m => <Marker key={m.id} position={m.pos} icon={ICONS[m.kind]} title={m.label} zIndexOffset={m.kind === 'driver' ? 1000 : 0} />)}
      {picker && onPick && (
        <>
          <Marker
            position={[picker.lat, picker.lng]}
            icon={PICK_ICON}
            draggable
            eventHandlers={{ dragend: e => { const p = (e.target as L.Marker).getLatLng(); onPick({ lat: p.lat, lng: p.lng }) } }}
          />
          <PickOnClick onPick={onPick} />
        </>
      )}
    </MapContainer>
  )
}
