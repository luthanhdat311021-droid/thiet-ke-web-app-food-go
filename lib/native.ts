import { Capacitor } from '@capacitor/core'

/** True when running inside the FoodGo Android app (Capacitor WebView). */
export const isNativeApp = () => Capacitor.isNativePlatform()

/** Deep link Supabase redirects to after Google sign-in in the system browser. */
export const NATIVE_AUTH_CALLBACK = 'com.foodgo.app://auth-callback'
