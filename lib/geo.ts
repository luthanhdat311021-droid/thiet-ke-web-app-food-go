export type LatLng = { lat: number; lng: number }
/** accuracy: radius in metres reported by the device (large = IP/Wi-Fi guess, not GPS) */
export type Place = LatLng & { address: string; accuracy?: number }

// window.Capacitor is injected by the Android shell; avoids importing @capacitor/core here
const isAndroidApp = () => typeof window !== 'undefined' && Boolean((window as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor?.isNativePlatform?.())

/** Beyond this the position is a rough guess and the customer should confirm the pin. */
export const ROUGH_ACCURACY_M = 150

/** Great-circle distance in km. */
export function distanceKm(a: LatLng, b: LatLng) {
  const R = 6371, rad = Math.PI / 180
  const h = Math.sin(((b.lat - a.lat) * rad) / 2) ** 2
    + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(((b.lng - a.lng) * rad) / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

export const formatKm = (km: number) => (km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1)} km`)

export const hasCoords = (o: { lat?: number | null; lng?: number | null } | null | undefined): o is LatLng =>
  o != null && typeof o.lat === 'number' && typeof o.lng === 'number'

/**
 * Best device position within a few seconds (browser or Android WebView). The first fix is often a
 * coarse network/IP guess, so keep watching until GPS is precise enough or time runs out.
 */
export function getCurrentPosition(maxWaitMs = 8000): Promise<LatLng & { accuracy: number }> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) return reject(new Error('Thiết bị không hỗ trợ định vị'))
    let best: (LatLng & { accuracy: number }) | null = null
    let done = false
    const finish = () => {
      if (done) return
      done = true
      navigator.geolocation.clearWatch(id)
      clearTimeout(timer)
      if (best) resolve(best)
      else reject(new Error('Lấy vị trí quá lâu, hãy bật GPS rồi thử lại'))
    }
    const id = navigator.geolocation.watchPosition(
      pos => {
        const fix = { lat: pos.coords.latitude, lng: pos.coords.longitude, accuracy: pos.coords.accuracy }
        if (!best || fix.accuracy < best.accuracy) best = fix
        if (fix.accuracy <= 30) finish()
      },
      err => {
        const fail = (msg: string) => { done = true; navigator.geolocation.clearWatch(id); clearTimeout(timer); reject(new Error(msg)) }
        if (err.code === err.PERMISSION_DENIED) {
          return fail(isAndroidApp()
            ? 'Hãy cấp quyền Vị trí cho FoodGo: Cài đặt → Ứng dụng → FoodGo → Quyền → Vị trí'
            : 'Bạn chưa cho phép trang web truy cập vị trí')
        }
        // device location switched off: no fix will ever come, say so right away
        if (err.code === err.POSITION_UNAVAILABLE && !best) return fail('Hãy bật Vị trí (GPS) trên điện thoại rồi thử lại')
        // timeouts: keep waiting, a later fix may still arrive
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: maxWaitMs },
    )
    const timer = setTimeout(finish, maxWaitMs)
  })
}

export async function reverseGeocode({ lat, lng }: LatLng): Promise<string> {
  const res = await fetch(`/api/geo/reverse?lat=${lat}&lng=${lng}`)
  if (!res.ok) throw new Error('Không lấy được địa chỉ')
  return (await res.json()).address as string
}

export async function geocode(address: string): Promise<LatLng | null> {
  const res = await fetch(`/api/geo/search?q=${encodeURIComponent(address)}`)
  if (!res.ok) return null
  const { lat, lng } = await res.json()
  return { lat, lng }
}

export type Route = { points: [number, number][]; distance: number; duration: number }

export async function fetchRoute(from: LatLng, to: LatLng): Promise<Route | null> {
  const q = new URLSearchParams({ fromLat: String(from.lat), fromLng: String(from.lng), toLat: String(to.lat), toLng: String(to.lng) })
  const res = await fetch(`/api/geo/route?${q}`)
  return res.ok ? res.json() : null
}

/** Point at `t` (0..1) of the way along a polyline, by length. */
export function pointAlong(points: [number, number][], t: number): [number, number] {
  if (points.length < 2) return points[0]
  const seg = points.slice(1).map((p, i) => distanceKm({ lat: points[i][0], lng: points[i][1] }, { lat: p[0], lng: p[1] }))
  let target = seg.reduce((s, d) => s + d, 0) * Math.min(1, Math.max(0, t))
  for (let i = 0; i < seg.length; i++) {
    if (target <= seg[i] || i === seg.length - 1) {
      const f = seg[i] ? Math.min(1, target / seg[i]) : 0
      const [a, b] = [points[i], points[i + 1]]
      return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f]
    }
    target -= seg[i]
  }
  return points[points.length - 1]
}
