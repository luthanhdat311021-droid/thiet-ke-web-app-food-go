'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Banknote, Bike, Loader2, LocateFixed, QrCode, ShoppingBag, TicketPercent } from 'lucide-react'
import { useLocation } from '@/components/location-provider'
import { MomoIcon } from '@/components/momo-icon'
import { startMomoPayment } from '@/lib/momo'
import { fetchRestaurant, useStoreHours } from '@/lib/store'
import { FoodMap } from '@/components/map'
import { distanceKm, formatKm, hasCoords, reverseGeocode, ROUGH_ACCURACY_M, type LatLng } from '@/lib/geo'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { RequireAuth } from '@/components/require-auth'
import { ClosedNotice, EmptyState, Panel } from '@/components/cards'
import { Field, TextArea } from '@/components/field'
import { errorMessage, supabase } from '@/lib/supabase'
import { money, shippingFee } from '@/lib/format'
import type { Address, Order, Restaurant, VoucherQuote } from '@/lib/types'

export default function CheckoutPage() {
  return <RequireAuth><Checkout /></RequireAuth>
}

function Checkout() {
  const router = useRouter()
  const { user, profile, cart, cartSubtotal, clearCart, toast } = useApp()
  const { place, status: locStatus, error: locError, locate } = useLocation()
  const [addresses, setAddresses] = useState<Address[] | null>(null)
  // 'here' = deliver to a point picked on the map (prefilled with the detected location)
  const [selected, setSelected] = useState<number | 'here'>('here')
  const [userPicked, setUserPicked] = useState(false)
  const [recipient, setRecipient] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [pin, setPin] = useState<LatLng | null>(null)
  const [resolving, setResolving] = useState(false)
  const [store, setStore] = useState<Restaurant | null>(null)
  const restaurantPos: LatLng | null = hasCoords(store) ? { lat: store.lat, lng: store.lng } : null
  const hours = useStoreHours(store)
  const [voucherInput, setVoucherInput] = useState('')
  const [voucher, setVoucher] = useState<VoucherQuote | null>(null)
  const [voucherError, setVoucherError] = useState('')
  const [voucherBusy, setVoucherBusy] = useState(false)
  const [saveAddress, setSaveAddress] = useState(true)
  const [payment, setPayment] = useState<'cod' | 'qr' | 'momo'>('qr')
  const [note, setNote] = useState('')
  const [agree, setAgree] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    supabase.from('fg_addresses').select('*').order('is_default', { ascending: false }).order('created_at', { ascending: false })
      .then(({ data }) => {
        const list = (data ?? []) as Address[]
        setAddresses(list)
        // without a detected location, fall back to the default saved address
        if (list.length && !place) setSelected(list[0].id)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // the detected location becomes the delivery point unless the customer chose something else
  useEffect(() => {
    if (!place || userPicked) return
    setSelected('here')
    setPin({ lat: place.lat, lng: place.lng })
    setAddress(place.address)
  }, [place, userPicked])

  // the cart's restaurant, fresh: the admin may have just opened/closed it
  const cartRestaurantId = cart[0]?.restaurant_id
  useEffect(() => {
    if (cartRestaurantId) fetchRestaurant(cartRestaurantId).then(setStore)
  }, [cartRestaurantId])

  const applyVoucher = async (code: string) => {
    if (!code.trim()) return
    setVoucherBusy(true); setVoucherError('')
    const { data, error } = await supabase.rpc('fg_check_voucher', { p_code: code, p_subtotal: cartSubtotal, p_restaurant_id: cartRestaurantId ?? null })
    setVoucherBusy(false)
    if (error) { setVoucher(null); setVoucherError(errorMessage(error)); return }
    setVoucher(data as VoucherQuote)
    setVoucherInput((data as VoucherQuote).code)
  }

  // the discount depends on the cart: re-check the applied code whenever the subtotal changes
  useEffect(() => {
    if (voucher) applyVoucher(voucher.code)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cartSubtotal])

  const movePin = async (p: LatLng) => {
    setPin(p); setResolving(true)
    try { setAddress(await reverseGeocode(p)) } catch {} finally { setResolving(false) }
  }

  const useCurrentLocation = async () => {
    setUserPicked(true); setSelected('here')
    const found = await locate()
    if (found) { setPin({ lat: found.lat, lng: found.lng }); setAddress(found.address) }
  }

  useEffect(() => {
    if (profile && !recipient) setRecipient(profile.full_name ?? '')
    if (profile && !phone) setPhone(profile.phone ?? '')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile])

  if (!cart.length) {
    return (
      <main className="mx-auto max-w-2xl px-5 pb-24 pt-10">
        <EmptyState icon={<ShoppingBag />} title="Giỏ hàng đang trống"><Link href="/search" className="font-bold text-[#ff5b35]">Khám phá món ăn</Link></EmptyState>
      </main>
    )
  }

  const fee = shippingFee(cartSubtotal)
  const discount = voucher?.discount ?? 0
  const closed = hours !== null && !hours.open
  const chosen = addresses?.find(a => a.id === selected)
  const deliveryPos = chosen ? (hasCoords(chosen) ? { lat: chosen.lat, lng: chosen.lng } : null) : pin
  const tripKm = restaurantPos && deliveryPos ? distanceKm(restaurantPos, deliveryPos) : null
  // pin still sits on a coarse IP/Wi-Fi guess the customer hasn't corrected
  const roughPin = !userPicked && (place?.accuracy ?? 0) > ROUGH_ACCURACY_M

  const placeOrder = async () => {
    setError('')
    const info = chosen ? { recipient: chosen.recipient, phone: chosen.phone, address: chosen.address } : { recipient, phone, address }
    if (tripKm !== null && tripKm > 30) return setError(`Vị trí giao hàng cách nhà hàng ${formatKm(tripKm)}, vượt quá phạm vi giao hàng (30 km)`)
    if (!info.recipient.trim() || !info.address.trim()) return setError('Vui lòng nhập đầy đủ người nhận và địa chỉ')
    if (!/^(0|\+84)\d{9,10}$/.test(info.phone.replace(/[\s.]/g, ''))) return setError('Số điện thoại không hợp lệ')
    if (!agree) return setError('Bạn cần đồng ý với điều khoản đặt hàng')

    setBusy(true)
    try {
      if (!chosen && saveAddress && user) {
        await supabase.from('fg_addresses').insert({ user_id: user.id, label: 'Nhà riêng', ...info, lat: pin?.lat ?? null, lng: pin?.lng ?? null, is_default: !addresses?.length })
      }
      const { data, error } = await supabase.rpc('fg_place_order', {
        p_items: cart.map(x => ({ food_id: x.food_id, qty: x.qty })),
        p_recipient: info.recipient,
        p_phone: info.phone,
        p_address: info.address,
        p_payment_method: payment,
        p_note: note,
        p_lat: deliveryPos?.lat ?? null,
        p_lng: deliveryPos?.lng ?? null,
        p_voucher_code: voucher?.code ?? null,
      })
      if (error) throw error
      const order = data as Order
      clearCart()
      if (payment === 'momo') {
        try {
          await startMomoPayment(order.id)
          return // leaving for MoMo's payment page
        } catch (e) {
          // the order exists; the customer can retry MoMo from the order page
          toast(errorMessage(e), 'error')
        }
      } else {
        toast('Đặt hàng thành công')
      }
      router.replace(`/orders/${order.id}?new=1`)
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <main className="mx-auto max-w-[1200px] px-5 pb-24 pt-8 lg:px-10">
      <Link href="/menu" className="-my-3 inline-block py-3 text-sm font-bold text-[#ff5b35]">← Tiếp tục chọn món</Link>
      <h1 className="mt-5 text-3xl font-extrabold">Thanh toán</h1>
      <div className="mt-8 grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <section className="flex flex-col gap-5">
          <Panel title="Địa chỉ giao hàng" action={<Link href="/account?tab=addresses" className="py-2 text-sm font-bold text-[#ff5b35]">Quản lý</Link>}>
            <div className="flex flex-col gap-3">
              <label className={`flex cursor-pointer gap-3 rounded-xl border p-4 ${selected === 'here' ? 'border-2 border-[#ff5b35] bg-[#fff5f1]' : 'border-[#eaded8]'}`}>
                <input type="radio" name="address" checked={selected === 'here'} onChange={() => { setUserPicked(true); setSelected('here') }} className="mt-1 size-4 accent-[#ff5b35]" />
                <span className="min-w-0 text-sm">
                  <b className="flex items-center gap-1.5"><LocateFixed className="size-4 text-[#ff5b35]" />Vị trí hiện tại / chọn trên bản đồ</b>
                  {locStatus === 'error' && !address ? (
                    <span className="mt-1 block text-[#c2410c]">{locError}</span>
                  ) : (
                    <span className="mt-1 block truncate text-[#746b67]">
                      {locStatus === 'locating' ? 'Đang xác định vị trí...' : address || 'Bấm “Lấy vị trí của tôi” hoặc chạm vào bản đồ'}
                    </span>
                  )}
                </span>
              </label>
              {selected === 'here' && (
                <div className="flex flex-col gap-4">
                  <div className="relative overflow-hidden rounded-2xl border border-[#eaded8]">
                    <FoodMap
                      className="h-64"
                      picker={pin ?? (place ? { lat: place.lat, lng: place.lng } : null)}
                      onPick={p => { setUserPicked(true); movePin(p) }}
                      markers={restaurantPos ? [{ id: 'r', kind: 'restaurant', pos: [restaurantPos.lat, restaurantPos.lng], label: store?.name }] : []}
                      fitPoints={pin ? [[pin.lat, pin.lng]] : restaurantPos ? [[restaurantPos.lat, restaurantPos.lng]] : undefined}
                    />
                    <button type="button" onClick={useCurrentLocation} className="absolute right-3 top-3 z-[400] flex h-10 items-center gap-2 rounded-xl bg-white px-3 text-sm font-bold text-[#ff5b35] shadow-md">
                      {locStatus === 'locating' ? <Loader2 className="size-4 animate-spin" /> : <LocateFixed className="size-4" />}Lấy vị trí của tôi
                    </button>
                  </div>
                  {roughPin ? (
                    <p className="-mt-2 rounded-xl bg-[#fff7df] px-3 py-2 text-xs text-[#8a6100]">
                      Thiết bị chỉ ước tính được vị trí (sai số khoảng {formatKm((place?.accuracy ?? 0) / 1000)}). Hãy <b>kéo ghim tới đúng nhà bạn</b> hoặc sửa địa chỉ bên dưới.
                    </p>
                  ) : (
                    <p className="-mt-2 text-xs text-[#9c918c]">Kéo ghim hoặc chạm vào bản đồ để chọn đúng điểm giao hàng.</p>
                  )}
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Người nhận" value={recipient} onChange={setRecipient} autoComplete="name" />
                    <Field label="Số điện thoại" type="tel" value={phone} onChange={setPhone} autoComplete="tel" />
                    <div className="sm:col-span-2">
                      <Field label={resolving ? 'Địa chỉ (đang cập nhật...)' : 'Địa chỉ'} value={address} onChange={setAddress} placeholder="Số nhà, đường, phường, quận, thành phố" autoComplete="street-address" />
                      <p className="mt-1 text-xs text-[#9c918c]">Bạn có thể sửa lại số nhà, hẻm, tòa nhà cho tài xế dễ tìm.</p>
                    </div>
                    <label className="flex cursor-pointer items-center gap-3 py-1 text-sm text-[#746b67] sm:col-span-2">
                      <input type="checkbox" checked={saveAddress} onChange={e => setSaveAddress(e.target.checked)} className="size-5 accent-[#ff5b35]" /> Lưu địa chỉ cho lần sau
                    </label>
                  </div>
                </div>
              )}
              {addresses?.map(a => (
                <label key={a.id} className={`flex cursor-pointer gap-3 rounded-xl border p-4 ${selected === a.id ? 'border-2 border-[#ff5b35] bg-[#fff5f1]' : 'border-[#eaded8]'}`}>
                  <input type="radio" name="address" checked={selected === a.id} onChange={() => { setUserPicked(true); setSelected(a.id) }} className="mt-1 size-4 accent-[#ff5b35]" />
                  <span className="text-sm">
                    <b>{a.recipient}</b> <span className="text-[#746b67]">• {a.phone}</span>
                    {a.is_default && <span className="ml-2 rounded bg-[#fff0eb] px-2 py-0.5 text-xs font-bold text-[#ff5b35]">Mặc định</span>}
                    <span className="mt-1 block text-[#746b67]">{a.address}</span>
                  </span>
                </label>
              ))}
              {tripKm !== null && (
                <p className="flex items-center gap-2 rounded-xl bg-[#f8f3f0] px-4 py-3 text-sm text-[#746b67]">
                  <Bike className="size-4 text-[#ff5b35]" />Cách nhà hàng khoảng <b className="text-[#241c19]">{formatKm(tripKm * 1.3)}</b> đường đi • dự kiến {Math.max(10, Math.round(tripKm * 1.3 * 3 + 12))} phút
                </p>
              )}
            </div>
          </Panel>

          <Panel title="Phương thức thanh toán">
            <div className="grid gap-3 sm:grid-cols-3">
              <PayOption active={payment === 'momo'} onClick={() => setPayment('momo')} icon={<MomoIcon />} title="Ví MoMo" desc="Thanh toán qua ví MoMo, xác nhận tức thì" />
              <PayOption active={payment === 'qr'} onClick={() => setPayment('qr')} icon={<QrCode />} title="Chuyển khoản QR" desc="Quét mã VietQR bằng app ngân hàng, xác nhận tự động" />
              <PayOption active={payment === 'cod'} onClick={() => setPayment('cod')} icon={<Banknote />} title="Thanh toán khi nhận hàng" desc="Trả tiền mặt cho tài xế" />
            </div>
          </Panel>

          <Panel title="Ghi chú cho quán">
            <TextArea label="" aria-label="Ghi chú cho quán" value={note} onChange={setNote} placeholder="Ví dụ: ít cay, không hành..." maxLength={300} />
          </Panel>
        </section>

        <div className="lg:sticky lg:top-24 lg:self-start">
          <Panel title={store ? `Đơn từ ${store.name}` : 'Đơn hàng của bạn'}>
            <div className="flex flex-col gap-4">
              {cart.map(x => (
                <div key={x.food_id} className="flex items-center gap-3">
                  {x.image && <img src={x.image} alt="" className="size-14 rounded-lg object-cover" />}
                  <div className="min-w-0 flex-1 text-sm"><b className="block truncate">{x.name}</b><p className="text-[#9c918c]">x{x.qty}</p></div>
                  <span className="font-bold">{money(x.price * x.qty)}</span>
                </div>
              ))}
              <div className="border-t border-[#f1e7e2] pt-4 text-sm">
                <div className="flex justify-between text-[#746b67]"><span>Tạm tính</span><span>{money(cartSubtotal)}</span></div>
                <div className="mt-3 flex justify-between text-[#746b67]"><span>Phí giao hàng</span>{fee ? <span>{money(fee)}</span> : <span className="text-[#72a77f]">Miễn phí</span>}</div>
                {discount > 0 && <div className="mt-3 flex justify-between text-[#2f7d4f]"><span>Giảm giá ({voucher?.code})</span><span>-{money(discount)}</span></div>}
                <div className="mt-4 flex justify-between text-lg font-extrabold"><span>Tổng cộng</span><span className="text-[#ff5b35]">{money(cartSubtotal + fee - discount)}</span></div>
              </div>
              <div className="border-t border-[#f1e7e2] pt-4">
                {voucher ? (
                  <div className="flex items-center gap-3 rounded-xl border border-dashed border-[#72a77f] bg-[#f0faf3] px-3 py-2.5">
                    <TicketPercent className="size-5 shrink-0 text-[#2f7d4f]" />
                    <div className="min-w-0 flex-1 text-sm">
                      <b className="text-[#2f7d4f]">{voucher.code}</b>
                      <p className="truncate text-xs text-[#746b67]">{voucher.description || `Giảm ${money(voucher.discount)}`}</p>
                    </div>
                    <button type="button" onClick={() => { setVoucher(null); setVoucherInput('') }} className="shrink-0 py-1 text-xs font-bold text-[#746b67] hover:text-red-500">Bỏ mã</button>
                  </div>
                ) : (
                  <form onSubmit={e => { e.preventDefault(); applyVoucher(voucherInput) }} className="flex gap-2">
                    <input value={voucherInput} onChange={e => { setVoucherInput(e.target.value.toUpperCase()); setVoucherError('') }}
                      placeholder="Nhập mã giảm giá" aria-label="Mã giảm giá" maxLength={30}
                      className="h-11 min-w-0 flex-1 rounded-xl border border-[#eaded8] px-3 text-sm uppercase outline-none placeholder:normal-case focus:border-[#ff5b35]" />
                    <Button type="submit" variant="outline" disabled={voucherBusy || !voucherInput.trim()} className="h-11 rounded-xl px-4 font-bold text-[#ff5b35]">
                      {voucherBusy && <Loader2 className="animate-spin" />}Áp dụng
                    </Button>
                  </form>
                )}
                {voucherError && <p className="mt-2 text-xs text-red-600">{voucherError}</p>}
              </div>
              <label className="flex cursor-pointer items-center gap-3 py-2 text-sm text-[#746b67]">
                <input type="checkbox" checked={agree} onChange={e => setAgree(e.target.checked)} className="size-5 shrink-0 accent-[#ff5b35]" /> Tôi đồng ý với điều khoản đặt hàng
              </label>
              {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
              {closed && hours && <ClosedNotice hours={hours} />}
              <Button disabled={busy || closed} onClick={placeOrder} className="h-12 rounded-xl bg-[#ff5b35] text-base hover:bg-[#e94c29]">
                {busy && <Loader2 className="animate-spin" />}{payment === 'qr' ? 'Đặt hàng & lấy mã QR' : payment === 'momo' ? 'Đặt hàng & thanh toán MoMo' : 'Đặt hàng'}
              </Button>
            </div>
          </Panel>
        </div>
      </div>
    </main>
  )
}

function PayOption({ active, onClick, icon, title, desc }: { active: boolean; onClick: () => void; icon: React.ReactNode; title: string; desc: string }) {
  return (
    <button type="button" onClick={onClick} className={`rounded-xl p-4 text-left ${active ? 'border-2 border-[#ff5b35] bg-[#fff5f1]' : 'border border-[#eaded8]'}`}>
      <span className={`mb-3 block ${active ? 'text-[#ff5b35]' : 'text-[#746b67]'}`}>{icon}</span>
      <b>{title}</b>
      <p className="mt-1 text-xs text-[#746b67]">{desc}</p>
    </button>
  )
}
