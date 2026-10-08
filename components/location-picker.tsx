'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Loader2, LocateFixed, MapPin, X } from 'lucide-react'
import { useApp } from '@/components/app-provider'
import { useLocation } from '@/components/location-provider'
import { LocateMap } from '@/components/map'
import { reverseGeocode, ROUGH_ACCURACY_M, type LatLng, type Place } from '@/lib/geo'

/**
 * Full-screen "định vị" map, ShopeeFood style: opens right after sign-in (and from the "Giao đến" chip).
 * The pin bobs over radar rings while locating, the map flies to the customer and the pin drops in;
 * dragging the map lifts the pin and the address follows wherever it is dropped.
 */
export function LocationPicker() {
  const { pickerOpen, setPickerOpen, place, status, error, locate, choose } = useLocation()
  const { profile } = useApp()
  // what the sheet shows: the device fix, or wherever the customer dropped the pin
  const [draft, setDraft] = useState<Place | null>(place)
  const [dragging, setDragging] = useState(false)
  const [resolving, setResolving] = useState(false)
  const [drop, setDrop] = useState(0)   // bump to replay the pin's drop-in
  const request = useRef(0)

  // a new device fix replaces any hand-placed pin
  useEffect(() => {
    if (!place) return
    setDraft(place)
    setDrop(n => n + 1)
  }, [place])

  // each opening starts from the saved position (a pin dropped but not confirmed last time is forgotten);
  // without one yet, start locating
  useEffect(() => {
    if (!pickerOpen) return
    setDraft(place); setDragging(false); setResolving(false); request.current++
    if (!place && status !== 'locating') locate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pickerOpen])

  const close = useCallback(() => setPickerOpen(false), [setPickerOpen])

  useEffect(() => {
    if (!pickerOpen) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    window.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = overflow }
  }, [pickerOpen, close])

  const onSettle = useCallback(async (c: LatLng) => {
    setDragging(false)
    setResolving(true)
    setDrop(n => n + 1)
    const id = ++request.current
    const address = await reverseGeocode(c).catch(() => `${c.lat.toFixed(5)}, ${c.lng.toFixed(5)}`)
    if (id !== request.current) return   // the pin moved again meanwhile
    setDraft({ ...c, address, accuracy: 0 })
    setResolving(false)
  }, [])

  const onDragStart = useCallback(() => { request.current++; setDragging(true); setResolving(false) }, [])

  const confirm = () => {
    if (draft && draft !== place) choose(draft)
    close()
  }

  if (!pickerOpen) return null

  const locating = status === 'locating'
  const phase = locating && !draft ? 'locating' : dragging ? 'lifted' : 'rest'
  const rough = !dragging && !resolving && (draft?.accuracy ?? 0) > ROUGH_ACCURACY_M
  const name = profile?.full_name?.trim().split(/\s+/).pop()

  return createPortal(
    <div role="dialog" aria-modal="true" aria-label="Xác định vị trí giao hàng" className="fg-fade-in fixed inset-0 z-[70] flex flex-col bg-[#fffaf7]">
      <div className="fg-locate relative flex-1">
        <LocateMap
          target={place}
          accuracy={draft === place ? place?.accuracy : null}
          onDragStart={onDragStart}
          onSettle={onSettle}
        />

        {/* top bar */}
        <div className="pointer-events-none absolute inset-x-0 top-0 z-[500] flex items-start justify-between gap-3 bg-gradient-to-b from-white/90 to-transparent px-4 pb-10 pt-[max(1rem,env(safe-area-inset-top))]">
          <div className="pointer-events-auto rounded-2xl bg-white px-4 py-2.5 shadow-md">
            <p className="text-xs text-[#9c918c]">{name ? `Chào ${name}!` : 'Chào bạn!'}</p>
            <p className="text-sm font-extrabold">Bạn muốn giao đồ ăn tới đâu?</p>
          </div>
          <button onClick={close} aria-label="Để sau" className="pointer-events-auto grid size-11 shrink-0 place-items-center rounded-full bg-white shadow-md"><X className="size-5" /></button>
        </div>

        {/* centre pin: its tip marks the map centre */}
        <div className="pointer-events-none absolute left-1/2 top-1/2 z-[500]">
          {phase === 'locating' && [0, 0.6, 1.2].map(d => (
            <span key={d} className="fg-radar absolute left-0 top-0 size-56 rounded-full border-2 border-[#ff5b35] bg-[#ff5b35]/15" style={{ animationDelay: `${d}s` }} />
          ))}
          {/* ground shadow: shrinks while the pin is in the air */}
          <span className={`absolute left-0 top-0 h-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-black/25 blur-[1px] transition-all duration-200 ${phase === 'rest' ? 'w-5' : 'w-3 opacity-60'}`} />
          <div className={`absolute bottom-0 left-0 -translate-x-1/2 transition-transform duration-200 ${phase === 'lifted' ? '-translate-y-4' : ''}`}>
            <div key={phase === 'rest' ? `drop-${drop}` : phase} className={phase === 'locating' ? 'fg-pin-bob' : phase === 'rest' ? 'fg-pin-drop' : ''}>
              <svg width="48" height="62" viewBox="0 0 36 48" className="drop-shadow-lg">
                <path d="M18 0C8 0 0 8 0 18c0 13 18 30 18 30s18-17 18-30C36 8 28 0 18 0z" fill="#ff5b35" />
                <circle cx="18" cy="18" r="8" fill="#fff" />
                <circle cx="18" cy="18" r="3.5" fill="#ff5b35" />
              </svg>
            </div>
          </div>
        </div>

        <button
          onClick={() => locate()}
          disabled={locating}
          aria-label="Định vị lại"
          className="absolute bottom-14 right-4 z-[500] grid size-12 place-items-center rounded-full bg-white text-[#ff5b35] shadow-lg disabled:opacity-70"
        >
          <LocateFixed className={`size-5 ${locating ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* bottom sheet */}
      <div className="fg-sheet-up relative z-10 -mt-6 rounded-t-3xl bg-white px-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-5 shadow-[0_-8px_30px_rgba(0,0,0,0.12)]">
        <div className="mx-auto mb-4 h-1.5 w-10 rounded-full bg-[#eaded8]" />
        {phase === 'locating' ? (
          <div className="flex items-center gap-3 py-2">
            <Loader2 className="size-6 shrink-0 animate-spin text-[#ff5b35]" />
            <div>
              <p className="font-extrabold">Đang xác định vị trí của bạn...</p>
              <p className="mt-0.5 text-sm text-[#746b67]">Bật GPS để tài xế giao đúng chỗ hơn</p>
            </div>
          </div>
        ) : !draft && status === 'error' ? (
          <div className="py-1">
            <p className="font-extrabold text-[#c2410c]">Không lấy được vị trí</p>
            <p className="mt-1 text-sm text-[#746b67]">{error}</p>
            <p className="mt-1 text-sm text-[#746b67]">Hoặc kéo bản đồ để đặt ghim tại nơi bạn nhận hàng.</p>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <button onClick={close} className="h-12 rounded-xl bg-[#f8f3f0] text-sm font-bold">Để sau</button>
              <button onClick={() => locate()} className="h-12 rounded-xl bg-[#ff5b35] text-sm font-bold text-white hover:bg-[#e94c29]">Thử lại</button>
            </div>
          </div>
        ) : (
          <div>
            <div className="flex items-start gap-3">
              <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[#fff0eb] text-[#ff5b35]"><MapPin className="size-5" /></span>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold text-[#9c918c]">Giao đến</p>
                {dragging ? <p className="font-extrabold text-[#746b67]">Thả ghim tại nơi bạn nhận hàng</p>
                  : resolving ? <p className="flex items-center gap-2 font-extrabold text-[#746b67]"><Loader2 className="size-4 animate-spin" />Đang lấy địa chỉ...</p>
                  : <p className="line-clamp-2 font-extrabold leading-snug">{draft?.address}</p>}
                {rough && <p className="mt-1 text-xs text-[#c2410c]">Vị trí chỉ là ước tính. Kéo bản đồ để đặt ghim đúng chỗ nhé.</p>}
              </div>
            </div>
            <button
              onClick={confirm}
              disabled={!draft || dragging || resolving}
              className="mt-5 h-12 w-full rounded-xl bg-[#ff5b35] text-sm font-bold text-white transition hover:bg-[#e94c29] disabled:opacity-50"
            >Xác nhận vị trí</button>
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}
