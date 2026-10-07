'use client'

import { useEffect, useState } from 'react'
import { Spinner } from '@/components/cards'
import { supabase } from '@/lib/supabase'
import { formatDateTime } from '@/lib/format'

type Entry = { id: number; at: string; actor: string | null; action: string; table_name: string; row_id: string | null; changes: Record<string, unknown> | null }

const TABLES: Record<string, string> = {
  fg_profiles: 'Tài khoản', fg_restaurants: 'Nhà hàng', fg_vouchers: 'Mã giảm giá',
  fg_subscription_payments: 'Phí duy trì', fg_orders: 'Đơn hàng', fg_foods: 'Món ăn',
}
const ACTIONS: Record<string, string> = { insert: 'Thêm', update: 'Sửa', delete: 'Xóa' }
const ACTION_STYLE: Record<string, string> = { insert: 'bg-[#e4f8eb] text-[#2f7d4f]', update: 'bg-[#fff7df] text-[#8a6100]', delete: 'bg-red-50 text-red-600' }

const show = (v: unknown) => v === null || v === undefined ? '∅' : typeof v === 'object' ? JSON.stringify(v) : String(v)

/** Read-only trail written by DB triggers (fg_audit): who changed what, and when. Nobody can edit it from the app. */
export function AuditLog() {
  const [entries, setEntries] = useState<Entry[] | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})
  const [table, setTable] = useState('')

  useEffect(() => {
    let q = supabase.from('fg_audit_log').select('*').order('at', { ascending: false }).limit(300)
    if (table) q = q.eq('table_name', table)
    q.then(async ({ data }) => {
      const list = (data ?? []) as Entry[]
      setEntries(list)
      const ids = [...new Set(list.map(e => e.actor).filter((x): x is string => !!x))]
      if (ids.length) {
        const { data: people } = await supabase.from('fg_profiles').select('id, full_name').in('id', ids)
        setNames(Object.fromEntries((people ?? []).map(p => [p.id, p.full_name ?? 'Người dùng'])))
      }
    })
  }, [table])

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Nhật ký bảo mật</h1>
          <p className="mt-1 text-sm text-[#746b67]">Ghi tự động trong database, không ai sửa hay xóa được từ ứng dụng.</p>
        </div>
        <select aria-label="Lọc theo loại" value={table} onChange={e => setTable(e.target.value)} className="h-10 rounded-xl border border-[#eaded8] bg-white px-3 text-sm">
          <option value="">Tất cả</option>
          {Object.entries(TABLES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>
      <div className="mt-5 flex flex-col gap-2">
        {entries === null && <Spinner />}
        {entries?.length === 0 && <p className="rounded-2xl bg-white py-12 text-center text-sm text-[#9c918c] shadow-sm">Chưa có hoạt động nào</p>}
        {entries?.map(e => (
          <div key={e.id} className="rounded-2xl bg-white p-4 text-sm shadow-sm">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className={`rounded-full px-2 py-0.5 text-xs font-bold ${ACTION_STYLE[e.action] ?? ''}`}>{ACTIONS[e.action] ?? e.action}</span>
              <b>{TABLES[e.table_name] ?? e.table_name}{e.row_id && ` #${e.row_id}`}</b>
              <span className="text-[#746b67]">bởi {e.actor ? (names[e.actor] ?? 'người dùng') : <i>hệ thống</i>}</span>
              <span className="ml-auto text-xs text-[#9c918c]">{formatDateTime(e.at)}</span>
            </div>
            {e.changes && (
              <dl className="mt-2 grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-0.5 text-xs">
                {Object.entries(e.changes).slice(0, 8).map(([k, v]) => {
                  const diff = e.action === 'update' && v && typeof v === 'object' && 'from' in (v as object)
                  return (
                    <div key={k} className="contents">
                      <dt className="font-mono text-[#9c918c]">{k}</dt>
                      <dd className="truncate">{diff ? <><del className="text-[#9c918c]">{show((v as { from: unknown }).from)}</del> → <b>{show((v as { to: unknown }).to)}</b></> : show(v)}</dd>
                    </div>
                  )
                })}
              </dl>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
