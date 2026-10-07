import { NextResponse } from 'next/server'

// Best-effort per-IP limiter for API routes (sliding window, in memory). Each serverless instance keeps
// its own counts, so this stops floods from one client rather than being an exact global quota —
// exact limits for money-related actions live in the database (fg_throttle).

const buckets = new Map<string, number[]>()

export function clientIp(req: Request) {
  // Vercel sets x-forwarded-for; the first entry is the original client
  return req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || req.headers.get('x-real-ip') || 'unknown'
}

/** Returns a 429 response when `req`'s IP made more than `max` calls to `name` in the last `windowMs`. */
export function rateLimit(req: Request, name: string, max: number, windowMs = 60_000): NextResponse | null {
  const key = `${name}:${clientIp(req)}`
  const now = Date.now()
  const hits = (buckets.get(key) ?? []).filter(t => now - t < windowMs)
  if (hits.length >= max) {
    const retry = Math.ceil((windowMs - (now - hits[0])) / 1000)
    return NextResponse.json({ error: 'Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút' }, { status: 429, headers: { 'Retry-After': String(retry) } })
  }
  hits.push(now)
  buckets.set(key, hits)
  // keep memory bounded on long-lived instances
  if (buckets.size > 5000) for (const [k, v] of buckets) if (!v.length || now - v[v.length - 1] > windowMs) buckets.delete(k)
  return null
}
