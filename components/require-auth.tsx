'use client'

import Link from 'next/link'
import { useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { useApp } from '@/components/app-provider'
import { Spinner } from '@/components/cards'
import { MfaChallenge, useAal } from '@/components/mfa'

/**
 * Redirects to /login when signed out; with `admin`, also requires profiles.role = 'admin'.
 * `admin` / `secure` pages (admin panel, restaurant owner panel) also ask for the 2FA code when the
 * account has 2FA on — the database refuses those rights to sessions without it anyway.
 */
export function RequireAuth({ children, admin = false, secure = false }: { children: React.ReactNode; admin?: boolean; secure?: boolean }) {
  const { user, profile, authLoading, signingOut, signOut } = useApp()
  const router = useRouter()
  const pathname = usePathname()
  const { aal, needsCode, refresh } = useAal()

  useEffect(() => {
    // after "Đăng xuất" the app is already heading home; only unexpected sign-outs come back here after login
    if (!authLoading && !user && !signingOut) router.replace(`/login?next=${encodeURIComponent(pathname)}`)
  }, [authLoading, user, signingOut, router, pathname])

  if (authLoading || !user) return <Spinner />
  if (admin || secure) {
    if (!aal) return <Spinner />
    if (needsCode) return <MfaChallenge onVerified={refresh} />
  }
  if (admin) {
    if (!profile) return <Spinner />
    if (profile.role !== 'admin') {
      return (
        <main className="mx-auto max-w-lg px-5 pb-24 pt-[max(6rem,env(safe-area-inset-top))] text-center">
          <h1 className="text-2xl font-extrabold">Không có quyền truy cập</h1>
          <p className="mt-3 text-sm text-[#746b67]">
            Tài khoản <b className="text-[#241c19]">{profile.full_name || user.email}</b> không phải quản trị viên.
          </p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <Link href="/" className="flex h-12 items-center justify-center rounded-xl bg-[#ff5b35] px-6 text-sm font-bold text-white hover:bg-[#e94c29]">Về trang chủ</Link>
            <button onClick={signOut} className="h-12 rounded-xl border border-[#eaded8] px-6 text-sm font-bold text-[#746b67] hover:bg-white">Đăng nhập tài khoản khác</button>
          </div>
        </main>
      )
    }
  }
  return <>{children}</>
}
