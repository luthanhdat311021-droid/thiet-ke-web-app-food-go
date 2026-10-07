const supabase = (process.env.NEXT_PUBLIC_SUPABASE_URL ?? '').replace(/^﻿/, '').trim().replace(/\/$/, '')
const dev = process.env.NODE_ENV !== 'production'

// Content-Security-Policy: only the origins the app really talks to.
// Next.js injects inline bootstrap scripts, so scripts need 'unsafe-inline' (nonces would force every
// page to render dynamically); everything else is locked down.
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline' https://challenges.cloudflare.com${dev ? " 'unsafe-eval'" : ''}`,
  "style-src 'self' 'unsafe-inline'",
  // dish / restaurant photos can be any https URL an admin pastes; map tiles; VietQR codes
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  `connect-src 'self' ${supabase} ${supabase.replace(/^https/, 'wss')} https://api.pwnedpasswords.com https://challenges.cloudflare.com`,
  // Cloudflare Turnstile renders inside an iframe
  'frame-src https://challenges.cloudflare.com',
  "frame-ancestors 'none'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  ...(dev ? [] : ['upgrade-insecure-requests']),
].join('; ')

const securityHeaders = [
  { key: 'Content-Security-Policy', value: csp },
  // HTTPS only for 2 years, including subdomains
  { key: 'Strict-Transport-Security', value: 'max-age=63072000; includeSubDomains; preload' },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  // the app only needs the customer's location; everything else is off
  { key: 'Permissions-Policy', value: 'geolocation=(self), camera=(), microphone=(), payment=(), usb=(), interest-cohort=()' },
  { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
  { key: 'X-DNS-Prefetch-Control', value: 'on' },
]

/** @type {import('next').NextConfig} */
const nextConfig = {
  devIndicators: false,
  poweredByHeader: false,
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  async headers() {
    return [
      { source: '/:path*', headers: securityHeaders },
      // keep API endpoints out of search engines
      { source: '/api/:path*', headers: [{ key: 'X-Robots-Tag', value: 'noindex' }] },
    ]
  },
}

export default nextConfig
