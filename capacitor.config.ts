import type { CapacitorConfig } from '@capacitor/cli'

// The Android app is a native shell around the live site, so web deploys
// (vercel deploy --prod) reach the app without rebuilding the APK.
const config: CapacitorConfig = {
  appId: 'com.foodgo.app',
  appName: 'FoodGo',
  webDir: 'mobile-shell',
  server: {
    url: 'https://thiet-ke-web-app-food-go.vercel.app',
    androidScheme: 'https',
    // shown by the WebView when the site can't be reached (offline)
    errorPath: 'offline.html',
    // MoMo's payment page stays inside the app so its redirect lands back in the app
    allowNavigation: ['thiet-ke-web-app-food-go.vercel.app', '*.supabase.co', 'img.vietqr.io', '*.momo.vn'],
  },
  android: {
    backgroundColor: '#fffaf7',
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      launchAutoHide: true,
      backgroundColor: '#ff5b35',
      showSpinner: false,
      androidScaleType: 'CENTER_CROP',
    },
    StatusBar: {
      backgroundColor: '#ffffff',
      style: 'LIGHT',
      overlaysWebView: false,
    },
  },
}

export default config
