'use client'

import { useEffect, useMemo, useState } from 'react'
import { ImagePlus, Loader2, Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useApp } from '@/components/app-provider'
import { errorMessage, supabase } from '@/lib/supabase'
import { LocationPicker } from '@/components/admin/location-picker'

export type FieldDef = {
  key: string
  label: string
  /** 'hidden': not shown in the form; new rows get `default` */
  /** 'location': map pin stored in the row's lat/lng (key is just a name); fills `addressKey` too */
  type: 'text' | 'number' | 'textarea' | 'image' | 'select' | 'checkbox' | 'date' | 'time' | 'location' | 'hidden'
  required?: boolean
  hint?: string
  addressKey?: string
  options?: { value: number | string; label: string }[]
  wide?: boolean
  default?: unknown
}

type Row = Record<string, unknown> & { id: number }

/** Generic list + create/edit/delete for a simple Supabase table (admin RLS required). */
export function EntityManager({ table, title, fields, columns, select = '*', orderBy = 'id', searchKey = 'name', allowCreate = true, allowDelete = true, deleteWarning, filter, onChanged, match }: {
  table: string
  /** only rows with these column values (e.g. one restaurant's dishes); RLS still applies */
  match?: Record<string, string | number>

  title: string
  fields: FieldDef[]
  columns: { label: string; render: (row: Row) => React.ReactNode }[]
  select?: string
  orderBy?: string
  searchKey?: string
  allowCreate?: boolean
  allowDelete?: boolean
  /** extra sentence in the delete confirmation */
  deleteWarning?: string
  /** dropdown that narrows the list by one column */
  filter?: { key: string; label: string; options: { value: number | string; label: string }[] }
  /** after any create / update / delete */
  onChanged?: () => void
}) {
  const { toast } = useApp()
  const [rows, setRows] = useState<Row[] | null>(null)
  const [editing, setEditing] = useState<Record<string, unknown> | null>(null)
  const [busy, setBusy] = useState(false)
  const [q, setQ] = useState('')
  const [filterValue, setFilterValue] = useState('')

  const matchKey = JSON.stringify(match ?? {})
  const load = () => {
    let query = supabase.from(table).select(select)
    for (const [k, v] of Object.entries(match ?? {})) query = query.eq(k, v)
    return query.order(orderBy).then(({ data, error }) => {
      if (error) toast(errorMessage(error), 'error')
      setRows((data ?? []) as unknown as Row[])
    })
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [table, matchKey])

  const shown = useMemo(() => rows?.filter(r =>
    (!q || String(r[searchKey] ?? '').toLowerCase().includes(q.toLowerCase()))
    && (!filter || !filterValue || String(r[filter.key]) === filterValue)), [rows, q, searchKey, filter, filterValue])

  const startCreate = () => {
    const blank: Record<string, unknown> = {}
    for (const f of fields) {
      if (f.type === 'location') { blank.lat = null; blank.lng = null; continue }
      blank[f.key] = f.default !== undefined ? f.default : f.type === 'checkbox' ? true : f.type === 'select' ? f.options?.[0]?.value ?? null : ''
    }
    setEditing(blank)
  }

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editing) return
    const payload: Record<string, unknown> = {}
    for (const f of fields) {
      if (f.type === 'location') {
        const has = editing.lat != null && editing.lng != null && editing.lat !== '' && editing.lng !== ''
        if (f.required && !has) return toast(`Vui lòng ghim ${f.label.toLowerCase()} trên bản đồ`, 'error')
        payload.lat = has ? Number(editing.lat) : null
        payload.lng = has ? Number(editing.lng) : null
        continue
      }
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
    onChanged?.()
  }

  const remove = async (row: Row) => {
    if (!window.confirm(`Xóa "${String(row[searchKey] ?? row.id)}"? ${deleteWarning ? `${deleteWarning} ` : ''}Thao tác này không thể hoàn tác.`)) return
    const { error } = await supabase.from(table).delete().eq('id', row.id)
    if (error) return toast(errorMessage(error), 'error')
    toast('Đã xóa')
    load()
    onChanged?.()
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-extrabold">{title}</h2>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:flex-nowrap">
          {filter && (
            <select aria-label={filter.label} value={filterValue} onChange={e => setFilterValue(e.target.value)} className="h-10 w-full rounded-xl border border-[#eaded8] bg-white px-3 text-sm sm:w-48">
              <option value="">{filter.label}: tất cả</option>
              {filter.options.map(o => <option key={o.value} value={String(o.value)}>{o.label}</option>)}
            </select>
          )}
          <div className="relative flex-1 sm:flex-none">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#9c918c]" />
            <input value={q} onChange={e => setQ(e.target.value)} placeholder="Tìm..." aria-label="Tìm kiếm" className="h-10 w-full sm:w-44 rounded-xl border border-[#eaded8] bg-white pl-9 pr-3 text-sm outline-none focus:border-[#ff5b35]" />
          </div>
          {allowCreate && <Button onClick={startCreate} className="h-10 rounded-xl bg-[#ff5b35] px-4 hover:bg-[#e94c29]"><Plus />Thêm</Button>}
        </div>
      </div>

      {/* phones: one card per row (first column = picture, second = title, the rest as label/value) */}
      <div className="mt-4 flex flex-col gap-3 sm:hidden">
        {rows === null && <p className="rounded-2xl bg-white py-10 text-center text-sm text-[#9c918c] shadow-sm">Đang tải...</p>}
        {shown?.length === 0 && <p className="rounded-2xl bg-white py-10 text-center text-sm text-[#9c918c] shadow-sm">Không có dữ liệu</p>}
        {shown?.map(r => (
          <div key={r.id} className="flex gap-3 rounded-2xl bg-white p-3 shadow-sm">
            <div className="shrink-0">{columns[0]?.render(r)}</div>
            <div className="min-w-0 flex-1 text-sm">
              <div className="truncate">{columns[1]?.render(r)}</div>
              <dl className="mt-1 grid grid-cols-[auto_minmax(0,1fr)] gap-x-2 gap-y-0.5 text-xs">
                {columns.slice(2).map(c => (
                  <div key={c.label} className="contents">
                    <dt className="text-[#9c918c]">{c.label}</dt>
                    <dd className="truncate text-[#241c19]">{c.render(r)}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="flex shrink-0 flex-col gap-1">
              <button aria-label="Sửa" onClick={() => setEditing({ ...r })} className="grid size-9 place-items-center rounded-lg bg-[#f8f3f0] text-[#746b67]"><Pencil className="size-4" /></button>
              {allowDelete && <button aria-label="Xóa" onClick={() => remove(r)} className="grid size-9 place-items-center rounded-lg text-[#746b67] hover:bg-red-50 hover:text-red-500"><Trash2 className="size-4" /></button>}
            </div>
          </div>
        ))}
      </div>

      <div className="mt-5 hidden overflow-x-auto rounded-2xl bg-white shadow-sm sm:block">
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
                  {allowDelete && <button aria-label="Xóa" onClick={() => remove(r)} className="inline-grid size-9 place-items-center rounded-lg text-[#746b67] hover:bg-red-50 hover:text-red-500"><Trash2 className="size-4" /></button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={() => setEditing(null)}>
          <form onSubmit={save} onClick={e => e.stopPropagation()} className="flex h-full w-full max-w-xl flex-col bg-white shadow-2xl">
            {/* full-screen sheet on phones: keep header/footer clear of the status bar and gesture bar */}
            <div className="flex items-center justify-between border-b border-[#f1e7e2] px-5 pb-4 pt-[max(1rem,env(safe-area-inset-top))]">
              <h3 className="text-lg font-extrabold">{editing.id ? 'Chỉnh sửa' : 'Thêm mới'}</h3>
              <button type="button" aria-label="Đóng" onClick={() => setEditing(null)} className="grid size-10 place-items-center rounded-full bg-[#f8f3f0]"><X /></button>
            </div>
            <div className="grid flex-1 content-start gap-4 overflow-y-auto p-5 sm:grid-cols-2">
              {fields.filter(f => f.type !== 'hidden').map(f => (
                <div key={f.key} className={f.wide || f.type === 'textarea' || f.type === 'image' || f.type === 'location' ? 'sm:col-span-2' : ''}>
                  {f.type === 'location' ? (
                    <div className="text-sm font-semibold">
                      {f.label}{f.required && <span className="text-[#ff5b35]"> *</span>}
                      <div className="mt-2">
                        <LocationPicker
                          value={{ lat: editing.lat as number | null, lng: editing.lng as number | null, address: f.addressKey ? (editing[f.addressKey] as string | null) : null }}
                          // functional update: the picker reports the address after an async lookup
                          onChange={({ address, ...pos }) => setEditing(e => e && ({ ...e, ...pos, ...(f.addressKey && address !== undefined ? { [f.addressKey]: address } : {}) }))}
                        />
                      </div>
                    </div>
                  ) : (
                    <FieldInput def={f} value={editing[f.key]} onChange={v => setEditing(e => e && ({ ...e, [f.key]: v }))} />
                  )}
                </div>
              ))}
            </div>
            <div className="flex gap-3 border-t border-[#f1e7e2] px-5 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
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
      ) : def.type === 'date' ? (
        <input type="date" value={str.slice(0, 10)} onChange={e => onChange(e.target.value)} required={def.required} className={inputCls} />
      ) : def.type === 'time' ? (
        <input type="time" value={str.slice(0, 5)} onChange={e => onChange(e.target.value)} required={def.required} className={inputCls} />
      ) : (
        <input type={def.type === 'number' ? 'number' : 'text'} step="any" value={str} onChange={e => onChange(e.target.value)} required={def.required} className={inputCls} />
      )}
      {def.hint && <span className="mt-1 block text-xs font-normal text-[#9c918c]">{def.hint}</span>}
    </label>
  )
}

export function ImageInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { toast, user } = useApp()
  const [uploading, setUploading] = useState(false)
  const upload = async (file: File) => {
    if (file.size > 5 * 1024 * 1024) return toast('Ảnh tối đa 5MB', 'error')
    setUploading(true)
    // each account uploads into its own folder (storage policy for restaurant owners)
    const path = `${user?.id ?? 'public'}/${Date.now()}-${file.name.replace(/[^\w.-]/g, '_')}`
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
