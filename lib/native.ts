import { useEffect, useState } from 'react'
import { Capacitor } from '@capacitor/core'

/** True when running inside the FoodGo Android app (Capacitor WebView). */
export const isNativeApp = () => Capacitor.isNativePlatform()

/** Same as isNativeApp(), but safe to use in render (false on the server/first paint, then the real value). */
export function useIsNativeApp() {
  const [native, setNative] = useState(false)
  useEffect(() => setNative(isNativeApp()), [])
  return native
}

/** Deep link Supabase redirects to after Google sign-in in the system browser. */
export const NATIVE_AUTH_CALLBACK = 'com.foodgo.app://auth-callback'
