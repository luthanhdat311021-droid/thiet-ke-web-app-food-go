import { NextResponse } from 'next/server'

const valid = (lat: number, lng: number) => Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180

/**
 * Motorbike-ish road route between two points via the public OSRM server.
 * Returns [lat, lng][] plus distance (m) and duration (s); falls back to a straight line.
 */
export async function GET(req: Request) {
  const p = new URL(req.url).searchParams
  const from = [Number(p.get('fromLat')), Number(p.get('fromLng'))] as const
  const to = [Number(p.get('toLat')), Number(p.get('toLng'))] as const
  if (!valid(...from) || !valid(...to)) return NextResponse.json({ error: 'Tọa độ không hợp lệ' }, { status: 400 })

  const coords = `${from[1].toFixed(5)},${from[0].toFixed(5)};${to[1].toFixed(5)},${to[0].toFixed(5)}`
  try {
    const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`, {
      headers: { 'User-Agent': 'FoodGo/1.0 (+https://thiet-ke-web-app-food-go.vercel.app)' },
      next: { revalidate: 60 * 60 * 24 },
      signal: AbortSignal.timeout(6000),
    })
    const data = await res.json() as { routes?: { distance: number; duration: number; geometry: { coordinates: [number, number][] } }[] }
    const route = data.routes?.[0]
    if (!route) throw new Error('no route')
    return NextResponse.json({
      points: route.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
      distance: route.distance,
      // OSRM assumes a car; city motorbikes in HCMC average ~20 km/h
      duration: Math.max(route.duration, route.distance / (20 / 3.6)),
    }, { headers: { 'Cache-Control': 'public, s-maxage=86400' } })
  } catch {
    const R = 6371000, rad = Math.PI / 180
    const a = Math.sin(((to[0] - from[0]) * rad) / 2) ** 2 + Math.cos(from[0] * rad) * Math.cos(to[0] * rad) * Math.sin(((to[1] - from[1]) * rad) / 2) ** 2
    const distance = 2 * R * Math.asin(Math.sqrt(a)) * 1.3
    return NextResponse.json({ points: [from, to], distance, duration: distance / (20 / 3.6), approximate: true })
  }
}
