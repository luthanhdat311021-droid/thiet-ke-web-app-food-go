'use client'

import { useCallback, useEffect, useState } from 'react'
import { Loader2, ShieldCheck, Smartphone, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'

// Two-factor authentication with authenticator apps (TOTP) via Supabase Auth MFA — free, no SMS.
// Once a user has a verified factor, the DB (fg_mfa_ok) only grants admin / restaurant-owner rights
// to sessions that also passed the 6-digit code (assurance level "aal2").

type Factor = { id: string; friendly_name?: string; status: string; created_at: string }

/** null while loading; `needsCode` = has 2FA but this session hasn't entered a code yet. */
export function useAal() {
  const [state, setState] = useState<{ current: string; next: string } | null>(null)
  const refresh = useCallback(async () => {
    const { data } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel()
    setState({ current: data?.currentLevel ?? 'aal1', next: data?.nextLevel ?? 'aal1' })
  }, [])
  useEffect(() => {
    refresh()
    const { data: sub } = supabase.auth.onAuthStateChange(() => { refresh() })
    return () => sub.subscription.unsubscribe()
  }, [refresh])
  return { aal: state, needsCode: state?.current === 'aal1' && state.next === 'aal2', refresh }
}

const codeInput = 'h-12 w-full rounded-xl border border-[#eaded8] px-4 text-center font-mono text-2xl tracking-[0.5em] outline-none focus:border-[#ff5b35]'
const onlyDigits = (v: string) => v.replace(/\D/g, '').slice(0, 6)

/** Asks for the 6-digit code to lift this session to aal2. */
export function MfaChallenge({ onVerified, title = 'Xác thực 2 lớp' }: { onVerified: () => void; title?: string }) {
  const { signOut } = useApp()
  const [factor, setFactor] = useState<Factor | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    supabase.auth.mfa.listFactors().then(({ data }) => setFactor((data?.totp?.[0] as Factor | undefined) ?? null))
  }, [])

  const verify = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!factor || code.length !== 6) return
    setBusy(true); setError('')
    const { error } = await supabase.auth.mfa.challengeAndVerify({ factorId: factor.id, code })
    setBusy(false)
    if (error) { setCode(''); return setError(/invalid|expired/i.test(error.message) ? 'Mã không đúng hoặc đã hết hạn' : errorMessage(error)) }
    onVerified()
  }

  return (
    <main className="mx-auto max-w-sm px-5 py-16">
      <form onSubmit={verify} className="rounded-3xl bg-white p-6 text-center shadow-sm">
        <span className="mx-auto grid size-14 place-items-center rounded-full bg-[#fff0eb] text-[#ff5b35]"><ShieldCheck className="size-7" /></span>
        <h1 className="mt-4 text-xl font-extrabold">{title}</h1>
        <p className="mt-2 text-sm text-[#746b67]">Mở ứng dụng xác thực (Google Authenticator…) và nhập mã 6 số cho FoodGo.</p>
        <input value={code} onChange={e => setCode(onlyDigits(e.target.value))} inputMode="numeric" autoComplete="one-time-code" autoFocus
          aria-label="Mã xác thực 6 số" placeholder="••••••" className={`mt-5 ${codeInput}`} />
        {error && <p className="mt-3 rounded-xl bg-red-50 px-4 py-2 text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={busy || code.length !== 6 || !factor} className="mt-4 h-12 w-full rounded-xl bg-[#ff5b35] hover:bg-[#e94c29]">
          {busy && <Loader2 className="animate-spin" />}Xác nhận
        </Button>
        <button type="button" onClick={signOut} className="mt-3 py-2 text-sm font-semibold text-[#746b67]">Đăng xuất</button>
      </form>
    </main>
  )
}

/** Account → Bảo mật: turn 2FA on (scan QR, confirm a code) or off. */
export function MfaSettings() {
  const { toast } = useApp()
  const { needsCode, refresh } = useAal()
  const [factors, setFactors] = useState<Factor[] | null>(null)
  const [enroll, setEnroll] = useState<{ id: string; qr: string; secret: string } | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const { data } = await supabase.auth.mfa.listFactors()
    setFactors(((data?.totp ?? []) as Factor[]).filter(f => f.status === 'verified'))
  }, [])
  useEffect(() => { load() }, [load])

  const start = async () => {
    setBusy(true); setError('')
    // drop half-finished setups from earlier attempts
    const { data: all } = await supabase.auth.mfa.listFactors()
    for (const f of (all?.all ?? []) as Factor[]) if (f.status !== 'verified') await supabase.auth.mfa.unenroll({ factorId: f.id })
    const { data, error } = await supabase.auth.mfa.enroll({ factorType: 'totp', friendlyName: `FoodGo ${new Date().toLocaleString('vi-VN')}` })
    setBusy(false)
    if (error || !data) return setError(error ? errorMessage(error) : 'Không bật được 2FA')
    setEnroll({ id: data.id, qr: data.totp.qr_code, secret: data.totp.secret })
  }

  const confirm = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!enroll || code.length !== 6) return
    setBusy(true); setError('')
    const { error } = await supabase.auth.mfa.challengeAndVerify({ factorId: enroll.id, code })
    setBusy(false)
    if (error) { setCode(''); return setError('Mã không đúng, hãy nhập mã đang hiển thị trong ứng dụng') }
    setEnroll(null); setCode('')
    toast('Đã bật xác thực 2 lớp')
    load(); refresh()
  }

  const cancel = async () => {
    if (enroll) await supabase.auth.mfa.unenroll({ factorId: enroll.id })
    setEnroll(null); setCode(''); setError('')
  }

  const remove = async (f: Factor) => {
    if (needsCode) return setError('Hãy đăng xuất rồi đăng nhập lại kèm mã 2FA trước khi tắt')
    if (!window.confirm('Tắt xác thực 2 lớp? Tài khoản sẽ chỉ còn được bảo vệ bằng mật khẩu.')) return
    const { error } = await supabase.auth.mfa.unenroll({ factorId: f.id })
    if (error) return setError(errorMessage(error))
    toast('Đã tắt xác thực 2 lớp')
    load(); refresh()
  }

  if (factors === null) return <p className="py-6 text-center text-sm text-[#9c918c]"><Loader2 className="mx-auto size-5 animate-spin" /></p>

  return (
    <div className="flex max-w-xl flex-col gap-4 text-sm">
      <p className="text-[#746b67]">
        Ngoài mật khẩu, mỗi lần đăng nhập cần thêm mã 6 số từ ứng dụng trên điện thoại. Kẻ gian có mật khẩu của bạn cũng không vào được.
        <b className="text-[#241c19]"> Rất nên bật nếu bạn là quản trị viên hoặc chủ nhà hàng.</b>
      </p>

      {factors.length > 0 && !enroll && (
        <div className="flex items-center gap-3 rounded-xl bg-[#e4f8eb] px-4 py-3 text-[#2f7d4f]">
          <ShieldCheck className="size-5 shrink-0" />
          <span className="flex-1"><b>Đã bật xác thực 2 lớp</b><br /><span className="text-xs">{factors[0].friendly_name}</span></span>
          <button onClick={() => remove(factors[0])} className="flex items-center gap-1 rounded-lg px-2 py-2 text-xs font-bold text-red-500 hover:bg-white"><Trash2 className="size-4" />Tắt</button>
        </div>
      )}

      {factors.length === 0 && !enroll && (
        <Button onClick={start} disabled={busy} className="h-11 w-fit rounded-xl bg-[#ff5b35] px-5 hover:bg-[#e94c29]">
          {busy ? <Loader2 className="animate-spin" /> : <Smartphone />}Bật xác thực 2 lớp
        </Button>
      )}

      {enroll && (
        <form onSubmit={confirm} className="flex flex-col gap-4 rounded-2xl border border-[#f1e7e2] p-4 sm:flex-row sm:items-start">
          <img src={enroll.qr} alt="Mã QR thiết lập 2FA" className="size-44 shrink-0 self-center rounded-xl border border-[#f1e7e2] bg-white p-2" />
          <div className="flex-1">
            <ol className="list-decimal space-y-1 pl-4 text-[#746b67]">
              <li>Cài <b>Google Authenticator</b> hoặc <b>Microsoft Authenticator</b>.</li>
              <li>Quét mã QR (hoặc nhập khóa bên dưới).</li>
              <li>Nhập mã 6 số ứng dụng hiển thị.</li>
            </ol>
            <p className="mt-2 break-all rounded-lg bg-[#f8f3f0] px-3 py-2 font-mono text-xs">{enroll.secret}</p>
            <input value={code} onChange={e => setCode(onlyDigits(e.target.value))} inputMode="numeric" autoComplete="one-time-code"
              aria-label="Mã xác thực 6 số" placeholder="••••••" className={`mt-3 ${codeInput}`} />
            <div className="mt-3 flex gap-2">
              <Button type="submit" disabled={busy || code.length !== 6} className="h-11 rounded-xl bg-[#ff5b35] px-5 hover:bg-[#e94c29]">{busy && <Loader2 className="animate-spin" />}Xác nhận & bật</Button>
              <Button type="button" variant="outline" onClick={cancel} className="h-11 rounded-xl px-4">Hủy</Button>
            </div>
          </div>
        </form>
      )}
      {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-red-600">{error}</p>}
    </div>
  )
}
