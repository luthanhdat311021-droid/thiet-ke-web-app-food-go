'use client'

import { useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { useApp } from '@/components/app-provider'
import { Spinner } from '@/components/cards'

/** Redirects to /login when signed out; with `admin`, also requires profiles.role = 'admin'. */
export function RequireAuth({ children, admin = false }: { children: React.ReactNode; admin?: boolean }) {
  const { user, profile, authLoading } = useApp()
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (!authLoading && !user) router.replace(`/login?next=${encodeURIComponent(pathname)}`)
  }, [authLoading, user, router, pathname])

  if (authLoading || !user) return <Spinner />
  if (admin) {
    if (!profile) return <Spinner />
    if (profile.role !== 'admin') {
      return (
        <main className="mx-auto max-w-lg px-5 py-24 text-center">
          <h1 className="text-2xl font-extrabold">Không có quyền truy cập</h1>
          <p className="mt-3 text-sm text-[#746b67]">Tài khoản của bạn không phải quản trị viên. Xem mục “Tạo tài khoản admin” trong SUPABASE_SETUP.md.</p>
        </main>
      )
    }
  }
  return <>{children}</>
}
