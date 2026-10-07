'use client'

import Link from 'next/link'
import { Suspense, useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { Heart, KeyRound, LayoutDashboard, Loader2, LogOut, MapPin, Package, Pencil, Plus, ShieldCheck, Store, Trash2, User } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { RequireAuth } from '@/components/require-auth'
import { EmptyState, FoodCard, FOOD_SELECT, Panel, Spinner } from '@/components/cards'
import { Field } from '@/components/field'
import { useApp } from '@/components/app-provider'
import { validateNewPassword } from '@/lib/password'
import { MfaSettings } from '@/components/mfa'
import { errorMessage, supabase } from '@/lib/supabase'
import type { Address, Food } from '@/lib/types'

const TABS = [
  { id: 'profile', label: 'Thông tin cá nhân', icon: User },
  { id: 'addresses', label: 'Địa chỉ', icon: MapPin },
  { id: 'favorites', label: 'Món yêu thích', icon: Heart },
  { id: 'password', label: 'Đổi mật khẩu', icon: KeyRound },
  { id: 'security', label: 'Bảo mật (2FA)', icon: ShieldCheck },
] as const
type Tab = (typeof TABS)[number]['id']

export default function AccountPage() {
  return <RequireAuth><Suspense fallback={<Spinner />}><Account /></Suspense></RequireAuth>
}

function Account() {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const { profile, signOut } = useApp()
  const tab = (TABS.some(t => t.id === params.get('tab')) ? params.get('tab') : 'profile') as Tab

  return (
    <main className="mx-auto max-w-[1100px] px-5 pb-24 pt-8 lg:px-10">
      <h1 className="text-3xl font-extrabold">Tài khoản</h1>
      <div className="mt-8 grid grid-cols-[minmax(0,1fr)] gap-6 md:grid-cols-[240px_minmax(0,1fr)]">
        <aside className="flex gap-1 overflow-x-auto rounded-2xl bg-white p-3 shadow-sm md:flex-col md:self-start">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button key={id} onClick={() => router.replace(`${pathname}?tab=${id}`, { scroll: false })}
              className={`flex shrink-0 items-center gap-3 rounded-xl px-3 py-3 text-left text-sm ${tab === id ? 'bg-[#fff0eb] font-bold text-[#ff5b35]' : 'text-[#746b67] hover:bg-[#fffaf7]'}`}>
              <Icon className="size-4" />{label}
            </button>
          ))}
          <Link href="/orders" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-3 text-sm text-[#746b67] hover:bg-[#fffaf7]"><Package className="size-4" />Đơn hàng</Link>
          <Link href="/shop" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-3 text-sm text-[#746b67] hover:bg-[#fffaf7]"><Store className="size-4" />Kênh nhà hàng</Link>
          {profile?.role === 'admin' && <Link href="/admin" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-3 text-sm text-[#746b67] hover:bg-[#fffaf7]"><LayoutDashboard className="size-4" />Trang quản trị</Link>}
          <button onClick={signOut} className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-3 text-left text-sm text-red-500 hover:bg-red-50"><LogOut className="size-4" />Đăng xuất</button>
        </aside>
        {tab === 'profile' && <ProfileTab />}
        {tab === 'addresses' && <AddressesTab />}
        {tab === 'favorites' && <FavoritesTab />}
        {tab === 'password' && <PasswordTab />}
        {tab === 'security' && <Panel title="Xác thực 2 lớp (2FA)"><MfaSettings /></Panel>}
      </div>
    </main>
  )
}

function ProfileTab() {
  const { user, profile, refreshProfile, toast } = useApp()
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [birthday, setBirthday] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setFullName(profile?.full_name ?? '')
    setPhone(profile?.phone ?? '')
    setBirthday(profile?.birthday ?? '')
  }, [profile])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!user) return
    setBusy(true)
    const { error } = await supabase.from('fg_profiles').update({ full_name: fullName.trim(), phone: phone.trim() || null, birthday: birthday || null }).eq('id', user.id)
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    await refreshProfile()
    toast('Đã lưu thông tin')
  }

  const initials = (fullName || user?.email || '?').split(' ').map(w => w[0]).slice(-2).join('').toUpperCase()
  return (
    <Panel title="Thông tin cá nhân">
      <form onSubmit={save} className="flex flex-col gap-5">
        <div className="flex items-center gap-4">
          {profile?.avatar_url ? <img src={profile.avatar_url} alt="" className="size-20 rounded-full object-cover" />
            : <span className="grid size-20 place-items-center rounded-full bg-[#ffe0d5] text-xl font-bold text-[#ff5b35]">{initials}</span>}
          <div className="text-sm"><b>{fullName || 'Chưa đặt tên'}</b><p className="text-[#746b67]">{user?.email}</p></div>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Họ và tên" value={fullName} onChange={setFullName} required />
          <Field label="Email" value={user?.email ?? ''} onChange={() => {}} disabled />
          <Field label="Số điện thoại" type="tel" value={phone} onChange={setPhone} />
          <Field label="Ngày sinh" type="date" value={birthday} onChange={setBirthday} />
        </div>
        <Button type="submit" disabled={busy} className="h-11 w-fit rounded-xl bg-[#ff5b35] px-5 hover:bg-[#e94c29]">{busy && <Loader2 className="animate-spin" />}Lưu thay đổi</Button>
      </form>
    </Panel>
  )
}

function AddressesTab() {
  const { user, toast } = useApp()
  const [list, setList] = useState<Address[] | null>(null)
  const [editing, setEditing] = useState<Partial<Address> | null>(null)
  const [busy, setBusy] = useState(false)

  const load = () => supabase.from('fg_addresses').select('*').order('is_default', { ascending: false }).order('created_at')
    .then(({ data }) => setList((data ?? []) as Address[]))
  useEffect(() => { load() }, [])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!user || !editing) return
    setBusy(true)
    const row = { label: editing.label || 'Nhà riêng', recipient: editing.recipient ?? '', phone: editing.phone ?? '', address: editing.address ?? '', is_default: Boolean(editing.is_default) || !list?.length }
    const { error } = editing.id
      ? await supabase.from('fg_addresses').update(row).eq('id', editing.id)
      : await supabase.from('fg_addresses').insert({ ...row, user_id: user.id })
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    setEditing(null)
    toast('Đã lưu địa chỉ')
    load()
  }

  const remove = async (a: Address) => {
    if (!window.confirm(`Xóa địa chỉ "${a.label}"?`)) return
    const { error } = await supabase.from('fg_addresses').delete().eq('id', a.id)
    if (error) return toast(errorMessage(error), 'error')
    load()
  }

  const makeDefault = async (a: Address) => {
    await supabase.from('fg_addresses').update({ is_default: true }).eq('id', a.id)
    load()
  }

  return (
    <Panel title="Sổ địa chỉ" action={!editing && <Button onClick={() => setEditing({})} className="h-10 rounded-xl bg-[#ff5b35] px-4 hover:bg-[#e94c29]"><Plus />Thêm</Button>}>
      {editing && (
        <form onSubmit={save} className="mb-6 grid gap-4 rounded-2xl bg-[#fffaf7] p-4 sm:grid-cols-2">
          <Field label="Tên gợi nhớ" value={editing.label ?? ''} onChange={v => setEditing({ ...editing, label: v })} placeholder="Nhà riêng, Công ty..." />
          <Field label="Người nhận" value={editing.recipient ?? ''} onChange={v => setEditing({ ...editing, recipient: v })} required />
          <Field label="Số điện thoại" type="tel" value={editing.phone ?? ''} onChange={v => setEditing({ ...editing, phone: v })} required />
          <Field label="Địa chỉ" value={editing.address ?? ''} onChange={v => setEditing({ ...editing, address: v })} required />
          <label className="flex cursor-pointer items-center gap-3 text-sm text-[#746b67] sm:col-span-2">
            <input type="checkbox" checked={Boolean(editing.is_default)} onChange={e => setEditing({ ...editing, is_default: e.target.checked })} className="size-5 accent-[#ff5b35]" /> Đặt làm địa chỉ mặc định
          </label>
          <div className="flex gap-3 sm:col-span-2">
            <Button type="submit" disabled={busy} className="h-11 rounded-xl bg-[#ff5b35] px-5 hover:bg-[#e94c29]">{busy && <Loader2 className="animate-spin" />}Lưu</Button>
            <Button type="button" variant="outline" onClick={() => setEditing(null)} className="h-11 rounded-xl px-5">Hủy</Button>
          </div>
        </form>
      )}
      {list === null && <Spinner />}
      {list?.length === 0 && !editing && <p className="py-10 text-center text-sm text-[#9c918c]">Bạn chưa lưu địa chỉ nào</p>}
      <div className="flex flex-col gap-3">
        {list?.map(a => (
          <div key={a.id} className="flex items-start gap-3 rounded-xl border border-[#eaded8] p-4">
            <MapPin className="mt-0.5 size-5 shrink-0 text-[#ff5b35]" />
            <div className="min-w-0 flex-1 text-sm">
              <b>{a.label}</b>{a.is_default && <span className="ml-2 rounded bg-[#fff0eb] px-2 py-0.5 text-xs font-bold text-[#ff5b35]">Mặc định</span>}
              <p className="mt-1">{a.recipient} • {a.phone}</p>
              <p className="text-[#746b67]">{a.address}</p>
              {!a.is_default && <button onClick={() => makeDefault(a)} className="mt-1 py-1 text-xs font-bold text-[#ff5b35]">Đặt làm mặc định</button>}
            </div>
            <button aria-label="Sửa" onClick={() => setEditing(a)} className="grid size-9 place-items-center rounded-lg text-[#746b67] hover:bg-[#f8f3f0]"><Pencil className="size-4" /></button>
            <button aria-label="Xóa" onClick={() => remove(a)} className="grid size-9 place-items-center rounded-lg text-[#746b67] hover:bg-red-50 hover:text-red-500"><Trash2 className="size-4" /></button>
          </div>
        ))}
      </div>
    </Panel>
  )
}

function FavoritesTab() {
  const { favoriteIds } = useApp()
  const [foods, setFoods] = useState<Food[] | null>(null)
  useEffect(() => {
    supabase.from('fg_favorites').select(`created_at, foods:fg_foods(${FOOD_SELECT})`).order('created_at', { ascending: false })
      .then(({ data }) => setFoods((data ?? []).map(r => r.foods as unknown as Food).filter(Boolean)))
  }, [])
  const shown = foods?.filter(f => favoriteIds.includes(f.id))
  return (
    <div>
      {shown === undefined && <Spinner />}
      {shown?.length === 0 && <EmptyState icon={<Heart />} title="Chưa có món yêu thích">Bấm biểu tượng trái tim trên món ăn để lưu lại.</EmptyState>}
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{shown?.map(f => <FoodCard key={f.id} food={f} />)}</div>
    </div>
  )
}

function PasswordTab() {
  const { toast } = useApp()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (password !== confirm) return setError('Mật khẩu nhập lại không khớp')
    setBusy(true)
    const problem = await validateNewPassword(password)
    if (problem) { setBusy(false); return setError(problem) }
    const { error } = await supabase.auth.updateUser({ password })
    setBusy(false)
    if (error) return setError(errorMessage(error))
    setPassword(''); setConfirm('')
    toast('Đã đổi mật khẩu')
  }
  return (
    <Panel title="Đổi mật khẩu">
      <form onSubmit={save} className="flex max-w-md flex-col gap-4">
        <p className="text-sm text-[#746b67]">Nếu bạn đăng nhập bằng Google, bạn có thể đặt mật khẩu để đăng nhập thêm bằng email.</p>
        <Field label="Mật khẩu mới" type="password" value={password} onChange={setPassword} autoComplete="new-password" required />
        <Field label="Nhập lại mật khẩu" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" required />
        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={busy} className="h-11 w-fit rounded-xl bg-[#ff5b35] px-5 hover:bg-[#e94c29]">{busy && <Loader2 className="animate-spin" />}Cập nhật mật khẩu</Button>
      </form>
    </Panel>
  )
}
