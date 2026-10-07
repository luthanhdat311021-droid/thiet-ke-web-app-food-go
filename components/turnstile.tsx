'use client'

import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

/** Cloudflare Turnstile site key; without it the widget is skipped (Supabase captcha must then stay off too). */
export const TURNSTILE_SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY?.trim() || ''

type TurnstileApi = {
  render: (el: HTMLElement, opts: Record<string, unknown>) => string
  reset: (id: string) => void
  remove: (id: string) => void
}
declare global { interface Window { turnstile?: TurnstileApi } }

let scriptPromise: Promise<void> | null = null
function loadScript() {
  scriptPromise ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => { scriptPromise = null; reject(new Error('turnstile')) }
    document.head.appendChild(s)
  })
  return scriptPromise
}

export type TurnstileHandle = { reset: () => void }

/**
 * Invisible-for-most-people bot check (Cloudflare Turnstile, free). The token is passed to Supabase Auth
 * (`captchaToken`), which verifies it server-side when "Bot and Abuse Protection" is enabled there.
 * Tokens are single-use: call `reset()` after every submit.
 */
export const Turnstile = forwardRef<TurnstileHandle, { onToken: (token: string | null) => void }>(function Turnstile({ onToken }, ref) {
  const box = useRef<HTMLDivElement>(null)
  const widget = useRef<string | null>(null)
  const cb = useRef(onToken)
  cb.current = onToken

  useImperativeHandle(ref, () => ({
    reset: () => { if (widget.current && window.turnstile) { window.turnstile.reset(widget.current); cb.current(null) } },
  }), [])

  useEffect(() => {
    if (!TURNSTILE_SITE_KEY) return
    let cancelled = false
    loadScript().then(() => {
      if (cancelled || !box.current || !window.turnstile) return
      widget.current = window.turnstile.render(box.current, {
        sitekey: TURNSTILE_SITE_KEY,
        language: 'vi',
        appearance: 'interaction-only', // only shows itself when Cloudflare needs the visitor to click
        callback: (t: string) => cb.current(t),
        'expired-callback': () => cb.current(null),
        'error-callback': () => cb.current(null),
      })
    }).catch(() => cb.current(null))
    return () => {
      cancelled = true
      if (widget.current && window.turnstile) window.turnstile.remove(widget.current)
      widget.current = null
    }
  }, [])

  if (!TURNSTILE_SITE_KEY) return null
  return <div ref={box} className="flex justify-center empty:hidden" />
})
