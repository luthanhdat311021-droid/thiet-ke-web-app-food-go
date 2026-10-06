'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { App } from '@capacitor/app'
import { Browser } from '@capacitor/browser'
import { StatusBar, Style } from '@capacitor/status-bar'
import { useApp } from '@/components/app-provider'
import { supabase } from '@/lib/supabase'
import { isNativeApp, NATIVE_AUTH_CALLBACK } from '@/lib/native'

/** Wires Android-only behaviour (back button, status bar, OAuth deep link). Renders nothing on the web. */
export function NativeBridge() {
  const router = useRouter()
  const { cartOpen, setCartOpen, toast } = useApp()

  useEffect(() => {
    if (!isNativeApp()) return
    document.documentElement.classList.add('native-app')
    StatusBar.setStyle({ style: Style.Light }).catch(() => {})
    StatusBar.setBackgroundColor({ color: '#ffffff' }).catch(() => {})
  }, [])

  // Android back button: close the cart first, then go back, then leave the app
  useEffect(() => {
    if (!isNativeApp()) return
    const handle = App.addListener('backButton', ({ canGoBack }) => {
      if (cartOpen) return setCartOpen(false)
      if (canGoBack && location.pathname !== '/') return window.history.back()
      App.minimizeApp()
    })
    return () => { handle.then(h => h.remove()) }
  }, [cartOpen, setCartOpen])

  // Google sign-in returns to com.foodgo.app://auth-callback#access_token=...&refresh_token=...
  useEffect(() => {
    if (!isNativeApp()) return
    const handle = App.addListener('appUrlOpen', async ({ url }) => {
      if (!url.startsWith(NATIVE_AUTH_CALLBACK)) return
      Browser.close().catch(() => {})
      const parsed = new URL(url.replace('#', '?'))
      const error = parsed.searchParams.get('error_description')
      if (error) return toast(error, 'error')
      const access_token = parsed.searchParams.get('access_token')
      const refresh_token = parsed.searchParams.get('refresh_token')
      if (!access_token || !refresh_token) return toast('Đăng nhập Google không thành công', 'error')
      const { error: sessionError } = await supabase.auth.setSession({ access_token, refresh_token })
      if (sessionError) return toast(sessionError.message, 'error')
      toast('Đăng nhập thành công')
      let next = '/'
      try { next = sessionStorage.getItem('foodgo-oauth-next') || '/' } catch {}
      router.replace(next)
    })
    return () => { handle.then(h => h.remove()) }
  }, [router, toast])

  return null
}
