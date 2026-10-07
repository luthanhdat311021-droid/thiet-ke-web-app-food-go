'use client'

import { useState } from 'react'
import { Loader2, LocateFixed, MapPin, Search } from 'lucide-react'
import { FoodMap } from '@/components/map'
import { getCurrentPosition, reverseGeocode, type LatLng } from '@/lib/geo'

export type LocationValue = { lat: number | null; lng: number | null; address?: string | null }

/**
 * Pin a place on the map: search an address, use the device location, click the map or drag the pin.
 * The address found for the pin is offered (and filled in automatically when the address is still empty).
 */
export function LocationPicker({ value, onChange }: { value: LocationValue; onChange: (patch: LocationValue) => void }) {
  const pin: LatLng | null = value.lat != null && value.lng != null ? { lat: Number(value.lat), lng: Number(value.lng) } : null
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState<'search' | 'locate' | 'reverse' | null>(null)
  const [suggested, setSuggested] = useState<string | null>(null)
  const [error, setError] = useState('')

  const place = async (p: LatLng, knownAddress?: string) => {
    setError('')
    onChange({ lat: round(p.lat), lng: round(p.lng) })
    let address = knownAddress
    if (!address) {
      setBusy('reverse')
      address = await reverseGeocode(p).catch(() => undefined)
      setBusy(null)
    }
    if (!address) return setSuggested(null)
    // keep an address the admin typed; otherwise take the one for the pin
    if (!value.address?.trim()) { onChange({ lat: round(p.lat), lng: round(p.lng), address }); setSuggested(null) }
    else setSuggested(address === value.address ? null : address)
  }

  const search = async () => {
    const q = query.trim() || value.address?.trim()
    if (!q || q.length < 4) return setError('Nhập ít nhất 4 ký tự để tìm địa chỉ')
    setBusy('search'); setError('')
    const res = await fetch(`/api/geo/search?q=${encodeURIComponent(q)}`).catch(() => null)
    setBusy(null)
    if (!res?.ok) return setError('Không tìm thấy địa chỉ này. Thử ghi rõ hơn, hoặc bấm thẳng lên bản đồ.')
    const hit = await res.json() as { lat: number; lng: number; address: string }
    place(hit, hit.address)
  }

  const locate = async () => {
    setBusy('locate'); setError('')
    try { const p = await getCurrentPosition(); setBusy(null); place(p) }
    catch (e) { setBusy(null); setError(e instanceof Error ? e.message : 'Không lấy được vị trí') }
  }

  return (
    <div className="flex flex-col gap-2 text-sm font-normal">
      <div className="flex gap-2">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#9c918c]" />
          <input value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); search() } }}
            placeholder="Tìm địa chỉ, vd: 12 Trần Văn Ơn, Thủ Dầu Một" aria-label="Tìm địa chỉ trên bản đồ"
            className="h-11 w-full rounded-xl border border-[#eaded8] bg-white pl-9 pr-3 outline-none focus:border-[#ff5b35]" />
        </div>
        <button type="button" onClick={search} disabled={busy !== null} className="h-11 shrink-0 rounded-xl bg-[#241c19] px-4 font-bold text-white disabled:opacity-50">
          {busy === 'search' ? <Loader2 className="size-4 animate-spin" /> : 'Tìm'}
        </button>
      </div>
      <div className="relative overflow-hidden rounded-xl border border-[#eaded8]">
        <FoodMap className="h-64" picker={pin} onPick={p => place(p)} fitPoints={pin ? [[pin.lat, pin.lng]] : undefined} />
        <button type="button" onClick={locate} disabled={busy !== null}
          className="absolute right-3 top-3 z-[400] flex h-9 items-center gap-1.5 rounded-lg bg-white px-3 text-xs font-bold text-[#ff5b35] shadow-md">
          {busy === 'locate' ? <Loader2 className="size-4 animate-spin" /> : <LocateFixed className="size-4" />}Vị trí của tôi
        </button>
        {!pin && (
          <p className="pointer-events-none absolute inset-x-0 bottom-3 z-[400] mx-auto w-fit rounded-full bg-[#241c19]/85 px-3 py-1.5 text-xs font-semibold text-white">
            Bấm lên bản đồ để ghim vị trí nhà hàng
          </p>
        )}
      </div>
      {pin ? (
        <p className="flex items-center gap-1.5 text-xs text-[#746b67]">
          <MapPin className="size-3.5 text-[#ff5b35]" />
          {busy === 'reverse' ? 'Đang tìm địa chỉ của điểm ghim...' : <>Đã ghim: {pin.lat.toFixed(5)}, {pin.lng.toFixed(5)} • kéo ghim để chỉnh</>}
        </p>
      ) : <p className="text-xs text-[#c2410c]">Chưa ghim vị trí</p>}
      {suggested && (
        <div className="flex items-start gap-2 rounded-xl bg-[#fff7df] px-3 py-2 text-xs text-[#8a6100]">
          <span className="min-w-0 flex-1">Địa chỉ tại điểm ghim: <b>{suggested}</b></span>
          <button type="button" onClick={() => { onChange({ lat: pin?.lat ?? null, lng: pin?.lng ?? null, address: suggested }); setSuggested(null) }} className="shrink-0 font-bold text-[#ff5b35]">Dùng địa chỉ này</button>
        </div>
      )}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}

const round = (n: number) => Math.round(n * 1e6) / 1e6
