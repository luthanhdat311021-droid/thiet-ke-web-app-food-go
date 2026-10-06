'use client'

import { useEffect, useMemo, useState } from 'react'
import { ImagePlus, Loader2, Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'

export type FieldDef = {
  key: string
  label: string
  type: 'text' | 'number' | 'textarea' | 'image' | 'select' | 'checkbox'
  required?: boolean
  options?: { value: number | string; label: string }[]
  wide?: boolean
}

type Row = Record<string, unknown> & { id: number }

/** Generic list + create/edit/delete for a simple Supabase table (admin RLS required). */
export function EntityManager({ table, title, fields, columns, select = '*', orderBy = 'id', searchKey = 'name' }: {
  table: string
  title: string
  fields: FieldDef[]
  columns: { label: string; render: (row: Row) => React.ReactNode }[]
  select?: string
  orderBy?: string
  searchKey?: string
}) {
  const { toast } = useApp()
  const [rows, setRows] = useState<Row[] | null>(null)
  const [editing, setEditing] = useState<Record<string, unknown> | null>(null)
  const [busy, setBusy] = useState(false)
  const [q, setQ] = useState('')

  const load = () => supabase.from(table).select(select).order(orderBy).then(({ data, error }) => {
    if (error) toast(errorMessage(error), 'error')
    setRows((data ?? []) as unknown as Row[])
  })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [table])

  const shown = useMemo(() => rows?.filter(r => !q || String(r[searchKey] ?? '').toLowerCase().includes(q.toLowerCase())), [rows, q, searchKey])

  const startCreate = () => {
    const blank: Record<string, unknown> = {}
    for (const f of fields) blank[f.key] = f.type === 'checkbox' ? true : f.type === 'select' ? f.options?.[0]?.value ?? null : ''
    setEditing(blank)
  }

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editing) return
    const payload: Record<string, unknown> = {}
    for (const f of fields) {
      const v = editing[f.key]
      payload[f.key] = f.type === 'number' ? (v === '' || v === null ? null : Number(v))
        : f.type === 'select' ? (v === '' || v === null ? null : Number.isNaN(Number(v)) ? v : Number(v))
        : f.type === 'checkbox' ? Boolean(v)
        : v === '' ? null : v
    }
    setBusy(true)
    const { error } = editing.id
      ? await supabase.from(table).update(payload).eq('id', editing.id as number)
      : await supabase.from(table).insert(payload)
    setBusy(false)
    if (error) return toast(errorMessage(error), 'error')
    toast(editing.id ? 'Đã cập nhật' : 'Đã thêm mới')
    setEditing(null)
    load()
  }

  const remove = async (row: Row) => {
    if (!window.confirm(`Xóa "${String(row[searchKey] ?? row.id)}"? Thao tác này không thể hoàn tác.`)) return
    const { error } = await supabase.from(table).delete().eq('id', row.id)
    if (error) return toast(errorMessage(error), 'error')
    toast('Đã xóa')
    load()
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-extrabold">{title}</h2>
        <div className="flex gap-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#9c918c]" />
            <input value={q} onChange={e => setQ(e.target.value)} placeholder="Tìm..." aria-label="Tìm kiếm" className="h-10 w-44 rounded-xl border border-[#eaded8] bg-white pl-9 pr-3 text-sm outline-none focus:border-[#ff5b35]" />
          </div>
          <Button onClick={startCreate} className="h-10 rounded-xl bg-[#ff5b35] px-4 hover:bg-[#e94c29]"><Plus />Thêm</Button>
        </div>
      </div>

      <div className="mt-5 overflow-x-auto rounded-2xl bg-white shadow-sm">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="border-b border-[#f1e7e2] text-xs uppercase text-[#9c918c]">
            <tr>{columns.map(c => <th key={c.label} className="px-4 py-3 font-semibold">{c.label}</th>)}<th className="w-24" /></tr>
          </thead>
          <tbody>
            {rows === null && <tr><td colSpan={columns.length + 1} className="py-10 text-center text-[#9c918c]">Đang tải...</td></tr>}
            {shown?.length === 0 && <tr><td colSpan={columns.length + 1} className="py-10 text-center text-[#9c918c]">Không có dữ liệu</td></tr>}
            {shown?.map(r => (
              <tr key={r.id} className="border-b border-[#f8f3f0] last:border-0">
                {columns.map(c => <td key={c.label} className="px-4 py-3 align-middle">{c.render(r)}</td>)}
                <td className="px-2 py-3 text-right">
                  <button aria-label="Sửa" onClick={() => setEditing({ ...r })} className="inline-grid size-9 place-items-center rounded-lg text-[#746b67] hover:bg-[#f8f3f0]"><Pencil className="size-4" /></button>
                  <button aria-label="Xóa" onClick={() => remove(r)} className="inline-grid size-9 place-items-center rounded-lg text-[#746b67] hover:bg-red-50 hover:text-red-500"><Trash2 className="size-4" /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={() => setEditing(null)}>
          <form onSubmit={save} onClick={e => e.stopPropagation()} className="flex h-full w-full max-w-xl flex-col bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-[#f1e7e2] p-5">
              <h3 className="text-lg font-extrabold">{editing.id ? 'Chỉnh sửa' : 'Thêm mới'}</h3>
              <button type="button" aria-label="Đóng" onClick={() => setEditing(null)} className="grid size-10 place-items-center rounded-full bg-[#f8f3f0]"><X /></button>
            </div>
            <div className="grid flex-1 content-start gap-4 overflow-y-auto p-5 sm:grid-cols-2">
              {fields.map(f => (
                <div key={f.key} className={f.wide || f.type === 'textarea' || f.type === 'image' ? 'sm:col-span-2' : ''}>
                  <FieldInput def={f} value={editing[f.key]} onChange={v => setEditing({ ...editing, [f.key]: v })} />
                </div>
              ))}
            </div>
            <div className="flex gap-3 border-t border-[#f1e7e2] p-5">
              <Button type="submit" disabled={busy} className="h-11 flex-1 rounded-xl bg-[#ff5b35] hover:bg-[#e94c29]">{busy && <Loader2 className="animate-spin" />}Lưu</Button>
              <Button type="button" variant="outline" onClick={() => setEditing(null)} className="h-11 rounded-xl px-6">Hủy</Button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}

const inputCls = 'mt-2 h-11 w-full rounded-xl border border-[#eaded8] bg-white px-3 font-normal outline-none focus:border-[#ff5b35]'

function FieldInput({ def, value, onChange }: { def: FieldDef; value: unknown; onChange: (v: unknown) => void }) {
  const str = value === null || value === undefined ? '' : String(value)
  if (def.type === 'checkbox') {
    return (
      <label className="flex h-full cursor-pointer items-center gap-3 pt-6 text-sm font-semibold">
        <input type="checkbox" checked={Boolean(value)} onChange={e => onChange(e.target.checked)} className="size-5 accent-[#ff5b35]" />{def.label}
      </label>
    )
  }
  return (
    <label className="block text-sm font-semibold">
      {def.label}{def.required && <span className="text-[#ff5b35]"> *</span>}
      {def.type === 'textarea' ? (
        <textarea value={str} onChange={e => onChange(e.target.value)} rows={3} className="mt-2 w-full rounded-xl border border-[#eaded8] px-3 py-2 font-normal outline-none focus:border-[#ff5b35]" />
      ) : def.type === 'select' ? (
        <select value={str} onChange={e => onChange(e.target.value)} required={def.required} className={inputCls}>
          {!def.required && <option value="">— Không chọn —</option>}
          {def.options?.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      ) : def.type === 'image' ? (
        <ImageInput value={str} onChange={onChange} />
      ) : (
        <input type={def.type === 'number' ? 'number' : 'text'} step="any" value={str} onChange={e => onChange(e.target.value)} required={def.required} className={inputCls} />
      )}
    </label>
  )
}

function ImageInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { toast } = useApp()
  const [uploading, setUploading] = useState(false)
  const upload = async (file: File) => {
    if (file.size > 5 * 1024 * 1024) return toast('Ảnh tối đa 5MB', 'error')
    setUploading(true)
    const path = `${Date.now()}-${file.name.replace(/[^\w.-]/g, '_')}`
    const { error } = await supabase.storage.from('fg-images').upload(path, file, { contentType: file.type })
    setUploading(false)
    if (error) return toast(errorMessage(error), 'error')
    onChange(supabase.storage.from('fg-images').getPublicUrl(path).data.publicUrl)
  }
  return (
    <div className="mt-2 flex gap-3">
      <span className="size-20 shrink-0 overflow-hidden rounded-xl bg-[#f8f3f0]">{value && <img src={value} alt="" className="h-full w-full object-cover" />}</span>
      <div className="flex flex-1 flex-col gap-2">
        <input value={value} onChange={e => onChange(e.target.value)} placeholder="Dán URL ảnh hoặc tải lên" className="h-11 w-full rounded-xl border border-[#eaded8] px-3 font-normal outline-none focus:border-[#ff5b35]" />
        <span className="relative inline-flex h-9 w-fit cursor-pointer items-center gap-2 rounded-lg border border-[#eaded8] px-3 text-xs font-semibold hover:bg-[#fffaf7]">
          {uploading ? <Loader2 className="size-4 animate-spin" /> : <ImagePlus className="size-4" />}Tải ảnh lên
          <input type="file" accept="image/*" disabled={uploading} onChange={e => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = '' }} className="absolute inset-0 cursor-pointer opacity-0" />
        </span>
      </div>
    </div>
  )
}
