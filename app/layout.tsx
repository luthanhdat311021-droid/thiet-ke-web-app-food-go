import { Analytics } from '@vercel/analytics/next'
import type { Metadata, Viewport } from 'next'
import './globals.css'
import { AppProvider } from '@/components/app-provider'
import { SiteShell } from '@/components/site-shell'
import { NativeBridge } from '@/components/native-bridge'
import { LocationProvider } from '@/components/location-provider'

export const metadata: Metadata = {
  title: 'FoodGo – Món ngon giao tận cửa',
  description: 'Đặt món từ các nhà hàng quanh bạn – cơm, gà rán, pizza, trà sữa giao tận cửa.',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
}

export const viewport: Viewport = {
  // the design is light-only; declaring it stops Android WebView from force-darkening the UI
  colorScheme: 'light',
  themeColor: '#ffffff',
  viewportFit: 'cover',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="vi">
      <body className="antialiased">
        <AppProvider>
          <LocationProvider>
            <NativeBridge />
            <SiteShell>{children}</SiteShell>
          </LocationProvider>
        </AppProvider>
        {process.env.NODE_ENV === 'production' && <Analytics />}
      </body>
    </html>
  )
}
