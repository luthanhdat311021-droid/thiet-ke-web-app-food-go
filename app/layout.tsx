import { Analytics } from '@vercel/analytics/next'
import type { Metadata, Viewport } from 'next'
import './globals.css'
import { AppProvider } from '@/components/app-provider'
import { SiteShell } from '@/components/site-shell'
import { NativeBridge } from '@/components/native-bridge'

export const metadata: Metadata = {
  title: 'FoodGo – Món ngon giao tận cửa',
  description: 'Đặt món ăn từ các nhà hàng yêu thích quanh bạn, giao hàng nhanh.',
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
          <NativeBridge />
          <SiteShell>{children}</SiteShell>
        </AppProvider>
        {process.env.NODE_ENV === 'production' && <Analytics />}
      </body>
    </html>
  )
}
