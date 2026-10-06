'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Field } from '@/components/field'
import { Spinner } from '@/components/cards'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'

/** Landing page for the password-recovery email link (Supabase signs the user in from the URL). */
export default function ResetPasswordPage() {
  const router = useRouter()
  const { toast } = useApp()
  const [status, setStatus] = useState<'checking' | 'ready' | 'invalid' | 'done'>('checking')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    // Supabase puts errors (e.g. expired link) in the URL hash
    const hash = new URLSearchParams(location.hash.slice(1))
    if (hash.get('error_description')) { setStatus('invalid'); return }

    const { data: sub } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'PASSWORD_RECOVERY' || session) setStatus('ready')
    })
    supabase.auth.getSession().then(({ data }) => { if (data.session) setStatus('ready') })
    // the session is parsed from the URL almost instantly; give it a moment before declaring the link invalid
    const timer = setTimeout(() => setStatus(s => (s === 'checking' ? 'invalid' : s)), 4000)
    return () => { sub.subscription.unsubscribe(); clearTimeout(timer) }
  }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (password.length < 6) return setError('Mật khẩu cần ít nhất 6 ký tự')
    if (password !== confirm) return setError('Mật khẩu nhập lại không khớp')
    setBusy(true)
    const { error } = await supabase.auth.updateUser({ password })
    setBusy(false)
    if (error) return setError(errorMessage(error))
    setStatus('done')
    toast('Đã đặt mật khẩu mới')
    setTimeout(() => router.replace('/'), 1500)
  }

  return (
    <main className="mx-auto max-w-md px-5 pb-24 pt-10">
      <div className="rounded-3xl bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-2xl font-extrabold">Đặt lại mật khẩu</h1>
        {status === 'checking' && <Spinner label="Đang xác thực liên kết..." />}
        {status === 'invalid' && (
          <div className="mt-4 text-sm text-[#746b67]">
            <p className="rounded-xl bg-red-50 px-4 py-3 text-red-600">Liên kết không hợp lệ hoặc đã hết hạn.</p>
            <Link href="/login?mode=forgot" className="mt-5 inline-block py-2 font-bold text-[#ff5b35]">Gửi lại email đặt lại mật khẩu</Link>
          </div>
        )}
        {status === 'done' && (
          <p className="mt-6 flex items-center gap-2 rounded-xl bg-[#e4f8eb] px-4 py-3 text-sm text-[#2f7d4f]"><CheckCircle2 className="size-5" />Đổi mật khẩu thành công. Đang chuyển về trang chủ...</p>
        )}
        {status === 'ready' && (
          <form onSubmit={submit} className="mt-6 flex flex-col gap-4">
            <p className="text-sm text-[#746b67]">Nhập mật khẩu mới cho tài khoản của bạn.</p>
            <Field label="Mật khẩu mới" type="password" value={password} onChange={setPassword} autoComplete="new-password" required />
            <Field label="Nhập lại mật khẩu" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" required />
            {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy} className="h-12 rounded-xl bg-[#ff5b35] text-base hover:bg-[#e94c29]">
              {busy && <Loader2 className="animate-spin" />}Lưu mật khẩu mới
            </Button>
          </form>
        )}
      </div>
    </main>
  )
}
