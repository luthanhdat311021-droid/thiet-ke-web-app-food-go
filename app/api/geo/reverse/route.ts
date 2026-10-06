import { NextResponse } from 'next/server'

// Nominatim (OpenStreetMap) requires an identifying User-Agent; calls go through this
// route so the browser/app never hits it directly and results are cached at the edge.
const UA = 'FoodGo/1.0 (+https://thiet-ke-web-app-food-go.vercel.app)'

type NominatimAddress = Record<string, string | undefined>

/** Builds a short Vietnamese street address: "12 Lê Lợi, Bến Nghé, Quận 1, TP. Hồ Chí Minh". */
function formatAddress(a: NominatimAddress, fallback: string) {
  const street = [a.house_number, a.road ?? a.pedestrian ?? a.footway].filter(Boolean).join(' ')
  const ward = a.quarter ?? a.suburb ?? a.neighbourhood ?? a.village
  const district = a.city_district ?? a.county ?? a.district ?? a.town
  const city = a.city ?? a.state ?? a.province
  const parts = [street || a.amenity || a.building, ward, district, city].filter(Boolean)
  return parts.length >= 2 ? [...new Set(parts)].join(', ') : fallback
}

export async function GET(req: Request) {
  const { searchParams } = new URL(req.url)
  const lat = Number(searchParams.get('lat'))
  const lng = Number(searchParams.get('lng'))
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return NextResponse.json({ error: 'Tọa độ không hợp lệ' }, { status: 400 })
  }
  // ~11 m precision is plenty for an address and makes the cache far more effective
  const q = new URLSearchParams({ lat: lat.toFixed(4), lon: lng.toFixed(4), format: 'jsonv2', addressdetails: '1', 'accept-language': 'vi', zoom: '18' })
  const res = await fetch(`https://nominatim.openstreetmap.org/reverse?${q}`, {
    headers: { 'User-Agent': UA },
    next: { revalidate: 60 * 60 * 24 * 7 },
    signal: AbortSignal.timeout(8000),
  }).catch(() => null)
  if (!res?.ok) return NextResponse.json({ error: 'Không lấy được địa chỉ' }, { status: 502 })
  const data = await res.json() as { display_name?: string; address?: NominatimAddress }
  const address = formatAddress(data.address ?? {}, data.display_name ?? `${lat.toFixed(5)}, ${lng.toFixed(5)}`)
  return NextResponse.json({ address, lat, lng }, { headers: { 'Cache-Control': 'public, s-maxage=604800' } })
}
