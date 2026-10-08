'use client'

import { useState } from 'react'
import { BarChart3, Loader2, ReceiptText, TicketPercent, UtensilsCrossed } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { ImageInput } from '@/components/admin/entity-manager'
import { LocationPicker, type LocationValue } from '@/components/admin/location-picker'
import { errorMessage, supabase } from '@/lib/supabase'
import { money } from '@/lib/format'
import { normalizePhone, PHONE_ERROR, phoneInput } from '@/lib/validate'
import { SUBSCRIPTION_FEE } from '@/lib/shop'

const inputCls = 'mt-2 h-11 w-full rounded-xl border border-[#eaded8] bg-white px-3 font-normal outline-none focus:border-[#ff5b35]'

/** Self-service sign-up: creates the restaurant (hidden until paid) and the first month's invoice. */
export function RegisterRestaurantForm({ onRegistered }: { onRegistered: () => void }) {
  const { profile, toast } = useApp()
  const [form, setForm] = useState({
    name: '', cuisine: '', phone: profile?.phone ?? '', address: '', delivery_time: '20-30 phút',
    open_time: '07:00', close_time: '21:00', image: '', logo: '',
  })
  const [pos, setPos] = useState<{ lat: number | null; lng: number | null }>({ lat: null, lng: null })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (patch: Partial<typeof form>) => setForm(f => ({ ...f, ...patch }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    const phone = normalizePhone(form.phone)
    if (!phone) return setError(PHONE_ERROR)
    if (pos.lat == null || pos.lng == null) return setError('Vui lòng ghim vị trí nhà hàng trên bản đồ')
    setBusy(true)
    const { error } = await supabase.rpc('fg_register_restaurant', { p: { ...form, phone, lat: pos.lat, lng: pos.lng } })
    setBusy(false)
    if (error) return setError(errorMessage(error))
    toast('Đã tạo nhà hàng. Thanh toán phí tháng đầu để bắt đầu bán!')
    onRegistered()
  }

  return (
    <main className="mx-auto max-w-3xl px-5 pb-24 pt-8">
      <p className="text-sm font-semibold text-[#ff5b35]">KÊNH NHÀ HÀNG</p>
      <h1 className="mt-2 text-3xl font-extrabold">Đăng ký nhà hàng của bạn</h1>
      <p className="mt-2 text-sm text-[#746b67]">Mở gian hàng trên FoodGo, tự quản lý thực đơn, mã giảm giá và doanh thu.</p>

      <div className="mt-6 grid gap-3 sm:grid-cols-4">
        {[
          { icon: UtensilsCrossed, text: 'Thêm món, giá, ảnh' },
          { icon: TicketPercent, text: 'Tạo mã giảm giá riêng' },
          { icon: ReceiptText, text: 'Nhận & xử lý đơn' },
          { icon: BarChart3, text: 'Xem doanh thu' },
        ].map(({ icon: Icon, text }) => (
          <div key={text} className="flex items-center gap-2 rounded-xl bg-white p-3 text-sm font-semibold shadow-sm"><Icon className="size-4 shrink-0 text-[#ff5b35]" />{text}</div>
        ))}
      </div>
      <p className="mt-4 rounded-xl bg-[#fff7df] px-4 py-3 text-sm text-[#8a6100]">
        Phí duy trì <b>{money(SUBSCRIPTION_FEE)}/tháng</b>, thanh toán bằng chuyển khoản QR. Nhà hàng hiển thị với khách ngay khi nhận được tiền.
      </p>

      <form onSubmit={submit} className="mt-6 grid gap-4 rounded-2xl bg-white p-5 shadow-sm sm:grid-cols-2">
        <label className="block text-sm font-semibold sm:col-span-2">Tên nhà hàng <span className="text-[#ff5b35]">*</span>
          <input value={form.name} onChange={e => set({ name: e.target.value })} required maxLength={80} placeholder="VD: Bún bò Cô Ba" className={inputCls} />
        </label>
        <label className="block text-sm font-semibold">Món chính
          <input value={form.cuisine} onChange={e => set({ cuisine: e.target.value })} maxLength={120} placeholder="VD: Bún bò • Bánh canh" className={inputCls} />
        </label>
        <label className="block text-sm font-semibold">Số điện thoại <span className="text-[#ff5b35]">*</span>
          <input type="tel" inputMode="tel" value={form.phone} onChange={e => set({ phone: phoneInput(e.target.value) })} required placeholder="0912 345 678" autoComplete="tel" className={inputCls} />
        </label>

        <div className="text-sm font-semibold sm:col-span-2">Vị trí nhà hàng <span className="text-[#ff5b35]">*</span>
          <div className="mt-2">
            <LocationPicker
              value={{ ...pos, address: form.address }}
              onChange={({ address, ...p }: LocationValue) => { setPos(p); if (address !== undefined && address !== null) set({ address }) }}
            />
          </div>
        </div>
        <label className="block text-sm font-semibold sm:col-span-2">Địa chỉ hiển thị cho khách <span className="text-[#ff5b35]">*</span>
          <input value={form.address} onChange={e => set({ address: e.target.value })} required className={inputCls} />
          <span className="mt-1 block text-xs font-normal text-[#9c918c]">Tự điền khi ghim trên bản đồ; có thể sửa lại cho dễ đọc</span>
        </label>

        <label className="block text-sm font-semibold">Giờ mở cửa
          <input type="time" value={form.open_time} onChange={e => set({ open_time: e.target.value })} className={inputCls} />
        </label>
        <label className="block text-sm font-semibold">Giờ đóng cửa
          <input type="time" value={form.close_time} onChange={e => set({ close_time: e.target.value })} className={inputCls} />
        </label>
        <label className="block text-sm font-semibold">Thời gian giao dự kiến
          <input value={form.delivery_time} onChange={e => set({ delivery_time: e.target.value })} className={inputCls} />
        </label>
        <div />
        <div className="text-sm font-semibold sm:col-span-2">Ảnh bìa<ImageInput value={form.image} onChange={v => set({ image: v })} /></div>
        <div className="text-sm font-semibold sm:col-span-2">Logo<ImageInput value={form.logo} onChange={v => set({ logo: v })} /></div>

        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 sm:col-span-2">{error}</p>}
        <Button type="submit" disabled={busy} className="h-12 rounded-xl bg-[#ff5b35] text-base hover:bg-[#e94c29] sm:col-span-2">
          {busy && <Loader2 className="animate-spin" />}Đăng ký & thanh toán {money(SUBSCRIPTION_FEE)}
        </Button>
      </form>
    </main>
  )
}
