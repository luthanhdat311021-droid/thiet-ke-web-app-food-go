import { NextResponse } from 'next/server'
import { rateLimit } from '@/lib/rate-limit'

const UA = 'FoodGo/1.0 (+https://thiet-ke-web-app-food-go.vercel.app)'

type Hit = { lat: string; lon: string; display_name: string }

// Thủ Dầu Một area (around Thu Dau Mot University), where the restaurants deliver
const CITY_BOX = '106.58,11.06,106.76,10.90'

async function nominatim(q: string, bounded: boolean): Promise<Hit | null> {
  const params = new URLSearchParams({ q, format: 'jsonv2', countrycodes: 'vn', limit: '1', 'accept-language': 'vi', viewbox: CITY_BOX, bounded: bounded ? '1' : '0' })
  const res = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, {
    headers: { 'User-Agent': UA },
    next: { revalidate: 60 * 60 * 24 * 7 },
    signal: AbortSignal.timeout(8000),
  }).catch(() => null)
  if (!res?.ok) return null
  const [hit] = await res.json() as Hit[]
  return hit ?? null
}

/**
 * Candidate queries from most to least specific. Vietnam merged districts/wards in 2025, so older
 * addresses ("Quận 1", "Phường Bến Nghé") often no longer match OpenStreetMap – drop those parts.
 */
function candidates(text: string) {
  const parts = text.split(',').map(s => s.trim()).filter(Boolean)
  const isAdmin = (s: string) => /^(quận|huyện|q\.?|p\.?|phường|xã|thị xã)\s*\S+/i.test(s)
  const withoutAdmin = parts.filter(p => !isAdmin(p))
  const city = parts.length > 1 ? parts[parts.length - 1] : 'Hồ Chí Minh'
  return [...new Set([
    text,
    withoutAdmin.join(', '),
    [parts[0], city].join(', '),
  ])].filter(q => q.length >= 4)
}

/** Address text → coordinates (Vietnam only), used when an order/address has no saved coordinates. */
export async function GET(req: Request) {
  const limited = rateLimit(req, 'geo-search', 30)
  if (limited) return limited
  const text = new URL(req.url).searchParams.get('q')?.trim()
  if (!text || text.length < 4 || text.length > 200) return NextResponse.json({ error: 'Địa chỉ không hợp lệ' }, { status: 400 })
  // inner city first (street names repeat across the merged city), then anywhere in Vietnam
  const attempts = candidates(text).flatMap(q => [[q, true], [q, false]] as const)
  for (const [q, bounded] of attempts) {
    const hit = await nominatim(q, bounded)
    if (hit) {
      return NextResponse.json({ lat: Number(hit.lat), lng: Number(hit.lon), address: hit.display_name }, { headers: { 'Cache-Control': 'public, s-maxage=604800' } })
    }
  }
  return NextResponse.json({ error: 'Không tìm thấy địa chỉ' }, { status: 404 })
}
