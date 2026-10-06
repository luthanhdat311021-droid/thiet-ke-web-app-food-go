'use client'

import { useEffect, useState } from 'react'
import { Navigation, Phone } from 'lucide-react'
import { FoodMap, type MapMarker } from '@/components/map'
import { fetchRoute, formatKm, geocode, hasCoords, pointAlong, type LatLng, type Route } from '@/lib/geo'
import type { Order } from '@/lib/types'

/**
 * Restaurant → customer map with the road route. There is no driver app yet, so while the order is
 * "delivering" the driver's position is estimated from the time since that status started.
 */
export function OrderTrackingMap({ order }: { order: Order }) {
  const [from, setFrom] = useState<LatLng | null>(null)
  const [to, setTo] = useState<LatLng | null>(null)
  const [route, setRoute] = useState<Route | null>(null)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const r = order.restaurant
    if (hasCoords(r)) setFrom({ lat: r.lat, lng: r.lng })
    else if (r?.address) geocode(r.address).then(setFrom)
    if (order.delivery_lat != null && order.delivery_lng != null) setTo({ lat: order.delivery_lat, lng: order.delivery_lng })
    else geocode(order.address).then(setTo)
  }, [order.restaurant, order.delivery_lat, order.delivery_lng, order.address])

  useEffect(() => {
    if (from && to) fetchRoute(from, to).then(setRoute)
  }, [from, to])

  const moving = order.status === 'delivering'
  useEffect(() => {
    if (!moving) return
    const t = setInterval(() => setNow(Date.now()), 3000)
    return () => clearInterval(t)
  }, [moving])

  if (order.status === 'cancelled') return null
  if (!from && !to) {
    return <div className="grid h-64 place-items-center rounded-2xl bg-[#f4ece8] text-sm text-[#9c918c]">Đang tải bản đồ...</div>
  }

  const points: [number, number][] = route?.points ?? (from && to ? [[from.lat, from.lng], [to.lat, to.lng]] : [])
  const duration = route?.duration ?? 15 * 60
  const progress = moving ? Math.min(1, (now - new Date(order.updated_at).getTime()) / (duration * 1000)) : 0
  const remainingMin = Math.max(1, Math.ceil((duration * (1 - progress)) / 60))

  let driver: [number, number] | null = null
  if (order.status === 'picking_up' && from) driver = [from.lat, from.lng]
  if (moving && points.length) driver = pointAlong(points, progress)
  if (order.status === 'delivered' && to) driver = [to.lat, to.lng]

  const markers: MapMarker[] = [
    ...(from ? [{ id: 'restaurant', kind: 'restaurant' as const, pos: [from.lat, from.lng] as [number, number], label: order.restaurant_name }] : []),
    ...(to ? [{ id: 'home', kind: 'home' as const, pos: [to.lat, to.lng] as [number, number], label: 'Điểm giao hàng' }] : []),
    ...(driver ? [{ id: 'driver', kind: 'driver' as const, pos: driver, label: 'Tài xế' }] : []),
  ]

  const headline =
    order.status === 'delivering' ? (progress >= 1 ? 'Tài xế sắp đến nơi' : `Tài xế sẽ đến trong khoảng ${remainingMin} phút`)
    : order.status === 'picking_up' ? 'Tài xế đang lấy món tại nhà hàng'
    : order.status === 'delivered' ? 'Đơn hàng đã được giao'
    : 'Nhà hàng đang chuẩn bị đơn của bạn'

  const directions = to ? `https://www.google.com/maps/dir/?api=1&destination=${to.lat},${to.lng}${from ? `&origin=${from.lat},${from.lng}` : ''}&travelmode=two-wheeler` : null

  return (
    <div className="overflow-hidden rounded-2xl bg-white shadow-sm">
      <FoodMap className="h-72" markers={markers} route={points} fitPoints={markers.map(m => m.pos)} padding={40} />
      <div className="flex flex-col gap-3 p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <b className="block">{headline}</b>
            <p className="mt-1 text-xs text-[#746b67]">
              {route ? `Quãng đường ${formatKm(route.distance / 1000)}` : 'Đang tính đường đi...'}
              {moving && ' • vị trí tài xế là ước tính'}
            </p>
          </div>
          {directions && (
            <a href={directions} target="_blank" rel="noreferrer" className="flex shrink-0 items-center gap-1 rounded-lg px-2 py-2 text-xs font-bold text-[#ff5b35] hover:bg-[#fff5f1]">
              <Navigation className="size-3.5" />Google Maps
            </a>
          )}
        </div>
        {moving && <div className="h-2 overflow-hidden rounded-full bg-[#f4ece8]"><div className="h-full rounded-full bg-[#ff5b35] transition-[width] duration-1000" style={{ width: `${Math.round(progress * 100)}%` }} /></div>}
        {(order.status === 'picking_up' || moving) && (
          <div className="flex items-center gap-3 border-t border-[#f1e7e2] pt-3">
            <span className="grid size-11 place-items-center rounded-full bg-[#ffe0d5] text-lg">🛵</span>
            <div className="flex-1"><b className="text-sm">Tài xế FoodGo</b><p className="text-xs text-[#746b67]">{moving ? 'Đang trên đường đến bạn' : 'Đang chờ lấy món'}</p></div>
            <a href="tel:19001234" aria-label="Gọi tài xế" className="grid size-10 place-items-center rounded-full bg-[#e4f8eb] text-[#3eaa68]"><Phone className="size-4" /></a>
          </div>
        )}
      </div>
    </div>
  )
}
