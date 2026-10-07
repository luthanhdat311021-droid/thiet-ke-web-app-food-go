'use client'

import { Suspense, useEffect, useRef, useState } from 'react'
import { Turnstile, TURNSTILE_SITE_KEY, type TurnstileHandle } from '@/components/turnstile'
import { validateNewPassword } from '@/lib/password'
import { useRouter, useSearchParams } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { Spinner } from '@/components/cards'
import { Field } from '@/components/field'
import { errorMessage, isSupabaseConfigured, supabase } from '@/lib/supabase'
import { isNativeApp, NATIVE_AUTH_CALLBACK } from '@/lib/native'
import { Browser } from '@capacitor/browser'

type Mode = 'signin' | 'signup' | 'forgot'

export default function LoginPage() {
  return <Suspense fallback={<Spinner />}><LoginForm /></Suspense>
}

function LoginForm() {
  const router = useRouter()
  const params = useSearchParams()
  // only allow same-site relative redirects
  const rawNext = params.get('next') ?? '/'
  const next = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/'
  const { user, profile, authLoading, toast } = useApp()
  const [mode, setMode] = useState<Mode>(params.get('mode') === 'forgot' ? 'forgot' : params.get('mode') === 'signup' ? 'signup' : 'signin')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [captcha, setCaptcha] = useState<string | null>(null)
  const turnstile = useRef<TurnstileHandle>(null)

  // /admin is only a sensible destination for admins; anyone else signing in goes home
  const adminOnly = next.startsWith('/admin')
  useEffect(() => {
    if (authLoading || !user) return
    if (!adminOnly) return router.replace(next)
    if (profile) router.replace(profile.role === 'admin' ? next : '/')
  }, [authLoading, user, profile, adminOnly, router, next])

  // OAuth failures come back as ?error_description=... or #error_description=...
  useEffect(() => {
    const desc = params.get('error_description') ?? new URLSearchParams(location.hash.slice(1)).get('error_description')
    if (desc) setError(translate(desc))
  }, [params])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!isSupabaseConfigured) return setError('Chưa cấu hình Supabase (.env.local).')
    if (TURNSTILE_SITE_KEY && !captcha) return setError('Vui lòng chờ xác minh chống robot hoàn tất')
    setBusy(true); setError(''); setInfo('')
    // undefined when Turnstile isn't configured, so Supabase works with captcha protection off
    const captchaToken = captcha ?? undefined
    try {
      if (mode === 'signin') {
        const { error } = await supabase.auth.signInWithPassword({ email, password, options: { captchaToken } })
        if (error) throw error
        toast('Đăng nhập thành công')
      } else if (mode === 'signup') {
        const problem = await validateNewPassword(password)
        if (problem) throw new Error(problem)
        const { data, error } = await supabase.auth.signUp({
          email, password,
          options: { data: { full_name: name.trim() }, emailRedirectTo: `${location.origin}${next}`, captchaToken },
        })
        if (error) throw error
        if (!data.session) setInfo('Đã gửi email xác nhận. Vui lòng mở hộp thư và bấm vào liên kết để kích hoạt tài khoản.')
      } else {
        const { error } = await supabase.auth.resetPasswordForEmail(email, { redirectTo: `${location.origin}/reset-password`, captchaToken })
        if (error) throw error
        setInfo('Đã gửi email đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.')
      }
    } catch (err) {
      setError(translate(errorMessage(err)))
    } finally {
      setBusy(false)
      turnstile.current?.reset() // tokens are single-use
    }
  }

  const google = async () => {
    if (!isSupabaseConfigured) return setError('Chưa cấu hình Supabase (.env.local).')
    // Google refuses sign-in inside embedded WebViews, so the Android app uses the system
    // browser and comes back through the com.foodgo.app:// deep link (see NativeBridge)
    const native = isNativeApp()
    if (native) { try { sessionStorage.setItem('foodgo-oauth-next', next) } catch {} }
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: native ? NATIVE_AUTH_CALLBACK : `${location.origin}${next}`,
        skipBrowserRedirect: native,
        queryParams: { prompt: 'select_account' },
      },
    })
    if (error) return setError(translate(error.message))
    if (native && data.url) await Browser.open({ url: data.url })
  }

  return (
    <main className="mx-auto max-w-md px-5 pb-24 pt-10">
      <div className="rounded-3xl bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-2xl font-extrabold">{mode === 'signin' ? 'Đăng nhập' : mode === 'signup' ? 'Tạo tài khoản' : 'Quên mật khẩu'}</h1>
        <p className="mt-2 text-sm text-[#746b67]">
          {mode === 'forgot' ? 'Nhập email, chúng tôi sẽ gửi liên kết đặt lại mật khẩu.' : 'Đặt món nhanh hơn, theo dõi đơn hàng và lưu món yêu thích.'}
        </p>

        {mode !== 'forgot' && (
          <>
            <button onClick={google} className="mt-6 flex h-12 w-full items-center justify-center gap-3 rounded-xl border border-[#eaded8] text-sm font-semibold hover:bg-[#fffaf7]">
              <GoogleIcon /> Tiếp tục với Google
            </button>
            <div className="my-6 flex items-center gap-3 text-xs text-[#9c918c]"><span className="h-px flex-1 bg-[#f1e7e2]" />hoặc<span className="h-px flex-1 bg-[#f1e7e2]" /></div>
          </>
        )}

        <form onSubmit={submit} className={`flex flex-col gap-4 ${mode === 'forgot' ? 'mt-6' : ''}`}>
          {mode === 'signup' && <Field label="Họ và tên" value={name} onChange={setName} required autoComplete="name" />}
          <Field label="Email" type="email" value={email} onChange={setEmail} required autoComplete="email" />
          {mode !== 'forgot' && <Field label="Mật khẩu" type="password" value={password} onChange={setPassword} required autoComplete={mode === 'signin' ? 'current-password' : 'new-password'} />}
          {mode === 'signin' && <button type="button" onClick={() => setMode('forgot')} className="-mt-2 self-end py-2 text-sm font-semibold text-[#ff5b35]">Quên mật khẩu?</button>}
          {mode === 'signup' && <p className="-mt-2 text-xs text-[#9c918c]">Ít nhất 8 ký tự, gồm cả chữ và số.</p>}
          <Turnstile ref={turnstile} onToken={setCaptcha} />
          {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
          {info && <p className="rounded-xl bg-[#e4f8eb] px-4 py-3 text-sm text-[#2f7d4f]">{info}</p>}
          <Button type="submit" disabled={busy} className="h-12 rounded-xl bg-[#ff5b35] text-base hover:bg-[#e94c29]">
            {busy && <Loader2 className="animate-spin" />}
            {mode === 'signin' ? 'Đăng nhập' : mode === 'signup' ? 'Đăng ký' : 'Gửi liên kết'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-[#746b67]">
          {mode === 'signin' ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}{' '}
          <button onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(''); setInfo('') }} className="py-2 font-bold text-[#ff5b35]">
            {mode === 'signin' ? 'Đăng ký ngay' : 'Đăng nhập'}
          </button>
        </p>
      </div>
    </main>
  )
}

function translate(msg: string) {
  if (/invalid login credentials/i.test(msg)) return 'Email hoặc mật khẩu không đúng'
  if (/email not confirmed/i.test(msg)) return 'Email chưa được xác nhận. Vui lòng kiểm tra hộp thư.'
  if (/already registered/i.test(msg)) return 'Email này đã được đăng ký'
  if (/provider is not enabled/i.test(msg)) return 'Đăng nhập Google chưa được bật trong Supabase (xem SUPABASE_SETUP.md)'
  if (/rate limit|only request this after/i.test(msg)) return 'Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút'
  if (/captcha/i.test(msg)) return 'Xác minh chống robot thất bại, vui lòng thử lại'
  if (/error sending (confirmation|recovery|magic link)/i.test(msg)) return 'Không gửi được email tới địa chỉ này. Vui lòng kiểm tra lại email hoặc thử email khác.'
  return msg
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 48 48" className="size-5" aria-hidden>
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  )
}
