'use client'

import { useEffect, useState } from 'react'
import { Check, CheckCircle2, Copy, Loader2, QrCode } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { formatDateTime, money, vietQrUrl } from '@/lib/format'
import { SUBSCRIPTION_FEE, SUBSCRIPTION_MONTHS, subscriptionOf } from '@/lib/shop'
import type { Restaurant, SubscriptionPayment } from '@/lib/types'

/**
 * The owner's monthly fee: current status, renew (1/3/6/12 months) by VietQR transfer to the admin's
 * account. SePay sees the money and fg_sepay_confirm() extends paid_until; this panel polls until then.
 */
export function SubscriptionPanel({ restaurant, onPaid }: { restaurant: Restaurant; onPaid: () => void }) {
  const { toast } = useApp()
  const [history, setHistory] = useState<SubscriptionPayment[] | null>(null)
  const [invoice, setInvoice] = useState<SubscriptionPayment | null>(null)
  const [months, setMonths] = useState<number>(1)
  const [busy, setBusy] = useState(false)
  const sub = subscriptionOf(restaurant)

  const loadHistory = () => supabase.from('fg_subscription_payments').select('*').eq('restaurant_id', restaurant.id).order('created_at', { ascending: false }).limit(50)
    .then(({ data }) => {
      const list = (data ?? []) as SubscriptionPayment[]
      setHistory(list)
      // an unpaid invoice from registration (or an earlier visit) is shown right away
      setInvoice(cur => cur ?? list.find(p => p.status === 'pending' && p.method === 'qr') ?? null)
    })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { loadHistory() }, [restaurant.id])

  // wait for SePay: check every 5 s while a QR is on screen
  useEffect(() => {
    if (!invoice || invoice.status !== 'pending') return
    const t = setInterval(async () => {
      const { data } = await supabase.from('fg_subscription_payments').select('*').eq('id', invoice.id).maybeSingle()
      if ((data as SubscriptionPayment | null)?.status === 'paid') {
        setInvoice(null)
        toast('Đã nhận phí duy trì. Nhà hàng đã được gia hạn!')
        loadHistory()
        onPaid()
      }
    }, 5000)
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [invoice])

  const createInvoice = async () => {
    setBusy(true)
    const { data, error } = await supabase.rpc('fg_create_subscription_payment', { p_months: months })
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    setInvoice(data as SubscriptionPayment)
    loadHistory()
  }

  const qr = invoice ? vietQrUrl(invoice.amount, invoice.code) : null

  return (
    <section className="rounded-2xl bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-extrabold">Phí duy trì nhà hàng</h2>
          <p className="mt-0.5 text-sm text-[#746b67]">{money(SUBSCRIPTION_FEE)} / tháng • nhà hàng hiển thị cho khách khi còn hạn</p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-bold ${!sub.active ? 'bg-red-50 text-red-600' : sub.daysLeft <= 7 ? 'bg-[#fff7df] text-[#bd8300]' : 'bg-[#e4f8eb] text-[#2f7d4f]'}`}>
          {!sub.until ? 'Chưa kích hoạt' : !sub.active ? 'Đã hết hạn' : `Còn ${sub.daysLeft} ngày • đến ${sub.until.toLocaleDateString('vi-VN')}`}
        </span>
      </div>

      {!sub.active && (
        <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
          {sub.until ? 'Nhà hàng đã hết hạn nên đang bị ẩn với khách.' : 'Nhà hàng chưa hiển thị với khách.'} Thanh toán phí để {sub.until ? 'mở lại' : 'bắt đầu nhận đơn'}.
        </p>
      )}

      {invoice ? (
        <div className="mt-5 flex flex-col items-center gap-5 rounded-2xl border border-[#f1e7e2] p-4 sm:flex-row sm:items-start">
          {qr ? <img src={qr} alt={`Mã QR thanh toán ${money(invoice.amount)}`} className="w-56 rounded-xl border border-[#f1e7e2]" />
            : <p className="rounded-xl bg-[#fff7df] p-4 text-sm text-[#8a6100]">Chưa cấu hình tài khoản nhận tiền (NEXT_PUBLIC_VIETQR_*).</p>}
          <div className="w-full text-sm">
            <p className="font-bold">Gia hạn {invoice.months} tháng</p>
            <p className="mt-1 text-[#746b67]">Mở app ngân hàng, quét mã QR. Số tiền và nội dung đã điền sẵn — <b>giữ nguyên nội dung chuyển khoản</b>.</p>
            <dl className="mt-4 flex flex-col gap-2">
              <CopyRow label="Số tiền" value={String(invoice.amount)} display={money(invoice.amount)} />
              <CopyRow label="Nội dung" value={invoice.code} />
              {process.env.NEXT_PUBLIC_VIETQR_ACCOUNT_NO && <CopyRow label="Số tài khoản" value={process.env.NEXT_PUBLIC_VIETQR_ACCOUNT_NO} />}
            </dl>
            <p className="mt-4 flex items-center gap-2 font-semibold text-[#bd8300]"><Loader2 className="size-4 animate-spin" />Đang chờ tiền vào tài khoản...</p>
            <p className="mt-1 text-xs text-[#9c918c]">Trang tự cập nhật khi nhận được tiền (thường dưới 1 phút).</p>
            <button onClick={() => setInvoice(null)} className="mt-3 py-1 text-xs font-bold text-[#746b67] hover:text-[#ff5b35]">Chọn gói khác</button>
          </div>
        </div>
      ) : (
        <div className="mt-5">
          <p className="text-sm font-semibold">{sub.active ? 'Gia hạn thêm' : 'Chọn gói'}</p>
          <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {SUBSCRIPTION_MONTHS.map(m => (
              <button key={m} type="button" onClick={() => setMonths(m)}
                className={`rounded-xl p-3 text-left ${months === m ? 'border-2 border-[#ff5b35] bg-[#fff5f1]' : 'border border-[#eaded8]'}`}>
                <b>{m} tháng</b>
                <p className="mt-0.5 text-xs text-[#746b67]">{money(SUBSCRIPTION_FEE * m)}</p>
              </button>
            ))}
          </div>
          <Button onClick={createInvoice} disabled={busy} className="mt-4 h-12 w-full rounded-xl bg-[#ff5b35] hover:bg-[#e94c29] sm:w-auto sm:px-6">
            {busy ? <Loader2 className="animate-spin" /> : <QrCode />}Thanh toán {money(SUBSCRIPTION_FEE * months)} bằng QR
          </Button>
        </div>
      )}

      {!!history?.some(p => p.status === 'paid') && (
        <div className="mt-6 border-t border-[#f1e7e2] pt-4">
          <h3 className="text-sm font-bold">Lịch sử gia hạn</h3>
          <ul className="mt-2 divide-y divide-[#f8f3f0] text-sm">
            {history.filter(p => p.status === 'paid').map(p => (
              <li key={p.id} className="flex items-center justify-between gap-3 py-2">
                <span className="flex items-center gap-2"><CheckCircle2 className="size-4 text-[#3eaa68]" />{p.months} tháng <span className="text-xs text-[#9c918c]">• {formatDateTime(p.paid_at!)}</span></span>
                <b>{p.method === 'admin' ? <span className="font-normal text-[#9c918c]">Admin tặng</span> : money(p.amount)}</b>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

function CopyRow({ label, value, display }: { label: string; value: string; display?: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl bg-[#f8f3f0] px-4 py-2">
      <dt className="text-[#746b67]">{label}</dt>
      <dd className="flex items-center gap-2 font-bold">
        {display ?? value}
        <button type="button" aria-label={`Sao chép ${label}`} onClick={() => { navigator.clipboard?.writeText(value); setCopied(true); setTimeout(() => setCopied(false), 1500) }}
          className="grid size-8 place-items-center rounded-lg text-[#ff5b35] hover:bg-white">
          {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
        </button>
      </dd>
    </div>
  )
}
