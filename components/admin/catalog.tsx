'use client'

import { useEffect, useState } from 'react'
import { TicketPercent } from 'lucide-react'
import { Spinner } from '@/components/cards'
import { EntityManager, type FieldDef } from '@/components/admin/entity-manager'
import { supabase } from '@/lib/supabase'
import { money } from '@/lib/format'
import { storeHours } from '@/lib/store'
import type { Category, Restaurant } from '@/lib/types'

// Shared by the admin panel (every restaurant) and the restaurant owner's panel (`restaurantId` = their own).

export const thumb = (src: unknown) => src ? <img src={String(src)} alt="" className="size-12 rounded-lg object-cover" /> : <span className="block size-12 rounded-lg bg-[#f8f3f0]" />
export const yesNo = (v: unknown, yes: string, no: string) => <span className={`rounded-full px-2 py-1 text-xs font-bold ${v ? 'bg-[#e4f8eb] text-[#3eaa68]' : 'bg-[#f4f0ee] text-[#9c918c]'}`}>{v ? yes : no}</span>

function useLookups(restaurantId?: number) {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [loaded, setLoaded] = useState(false)
  useEffect(() => {
    let rq = supabase.from('fg_restaurants').select('*')
    if (restaurantId) rq = rq.eq('id', restaurantId)
    Promise.all([rq.order('name'), supabase.from('fg_categories').select('*').order('sort')]).then(([r, c]) => {
      setRestaurants((r.data ?? []) as Restaurant[])
      setCategories((c.data ?? []) as Category[])
      setLoaded(true)
    })
  }, [restaurantId])
  return { restaurants, categories, loaded }
}

// ---------------------------------------------------------------- dishes
export function FoodsManager({ restaurantId }: { restaurantId?: number }) {
  const { restaurants, categories, loaded } = useLookups(restaurantId)
  if (!loaded) return <Spinner />
  if (!restaurants.length) return <p className="rounded-2xl bg-white p-8 text-center text-sm text-[#746b67] shadow-sm">Chưa có nhà hàng nào. Thêm nhà hàng ở tab “Nhà hàng” trước.</p>
  const restaurantOptions = restaurants.map(r => ({ value: r.id, label: r.name }))
  return (
    <EntityManager
      table="fg_foods" title="Món ăn" select="*, categories:fg_categories(name), restaurant:fg_restaurants(name)"
      orderBy={restaurantId ? 'category_id' : 'restaurant_id'}
      match={restaurantId ? { restaurant_id: restaurantId } : undefined}
      filter={restaurantId ? undefined : { key: 'restaurant_id', label: 'Nhà hàng', options: restaurantOptions }}
      fields={[
        { key: 'name', label: 'Tên món', type: 'text', required: true },
        restaurantId
          ? { key: 'restaurant_id', label: 'Nhà hàng', type: 'hidden', default: restaurantId }
          : { key: 'restaurant_id', label: 'Nhà hàng', type: 'select', required: true, options: restaurantOptions },
        { key: 'category_id', label: 'Danh mục', type: 'select', options: categories.map(c => ({ value: c.id, label: c.name })) },
        { key: 'price', label: 'Giá bán (đ)', type: 'number', required: true },
        { key: 'old_price', label: 'Giá gốc (đ, để trống nếu không giảm)', type: 'number' },
        { key: 'description', label: 'Mô tả', type: 'textarea' },
        { key: 'image', label: 'Ảnh', type: 'image' },
        { key: 'is_available', label: 'Đang bán', type: 'checkbox' },
        { key: 'is_popular', label: 'Món nổi bật', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Ảnh', render: r => thumb(r.image) },
        { label: 'Tên món', render: r => <b>{String(r.name)}</b> },
        ...(restaurantId ? [] : [{ label: 'Nhà hàng', render: (r: Record<string, unknown>) => (r.restaurant as { name: string } | null)?.name ?? '—' }]),
        { label: 'Danh mục', render: r => (r.categories as { name: string } | null)?.name ?? '—' },
        { label: 'Giá', render: r => money(Number(r.price)) },
        { label: 'Đã bán', render: r => String(r.sold_count) },
        { label: 'Đánh giá', render: r => Number(r.review_count) > 0 ? `${Number(r.rating).toFixed(1)} ★ (${r.review_count})` : 'Chưa có' },
        { label: 'Trạng thái', render: r => yesNo(r.is_available, 'Đang bán', 'Tạm hết') },
      ]}
    />
  )
}

// ---------------------------------------------------------------- restaurant info
/** Form fields for a restaurant; owners don't get the admin-only "hide" switch. */
export function restaurantFields(admin: boolean): FieldDef[] {
  return [
    { key: 'name', label: 'Tên nhà hàng', type: 'text', required: true },
    { key: 'cuisine', label: 'Món chính (VD: Cơm • Gà rán • Trà sữa)', type: 'text' },
    { key: 'location', label: 'Vị trí nhà hàng', type: 'location', required: true, addressKey: 'address' },
    { key: 'address', label: 'Địa chỉ hiển thị cho khách', type: 'text', required: true, wide: true, hint: 'Tự điền khi ghim trên bản đồ; có thể sửa lại cho dễ đọc' },
    { key: 'phone', label: 'Số điện thoại nhà hàng', type: 'tel' },
    { key: 'delivery_time', label: 'Thời gian giao', type: 'text', required: true, default: '20-30 phút' },
    { key: 'tag', label: 'Nhãn (Freeship, Giảm 20%...)', type: 'text' },
    { key: 'open_time', label: 'Giờ mở cửa', type: 'time', default: '07:00', hint: 'Giờ Việt Nam. Để trống cả hai = mở cả ngày' },
    { key: 'close_time', label: 'Giờ đóng cửa', type: 'time', default: '21:00', hint: 'Đóng sau nửa đêm: vd mở 18:00, đóng 02:00' },
    { key: 'image', label: 'Ảnh bìa', type: 'image' },
    { key: 'logo', label: 'Logo', type: 'image' },
    { key: 'is_open', label: 'Đang mở bán (tắt = tạm đóng cửa)', type: 'checkbox' },
    ...(admin ? [{ key: 'is_active', label: 'Hiển thị nhà hàng (tắt = ẩn hoàn toàn)', type: 'checkbox' } as FieldDef] : []),
  ]
}

export const restaurantStatus = (r: Record<string, unknown>) =>
  !r.is_active ? yesNo(false, '', 'Đang ẩn') : yesNo(storeHours(r as unknown as Restaurant).open, 'Đang mở cửa', 'Đã đóng cửa')

// ---------------------------------------------------------------- vouchers
const VOUCHER_TYPES = [
  { value: 'amount', label: 'Giảm số tiền (đ)' },
  { value: 'percent', label: 'Giảm theo %' },
  { value: 'freeship', label: 'Miễn phí giao hàng' },
]

const describeVoucher = (r: Record<string, unknown>) => {
  const value = Number(r.discount_value)
  const main = r.discount_type === 'percent' ? `Giảm ${value}%${r.max_discount ? ` (tối đa ${money(Number(r.max_discount))})` : ''}`
    : r.discount_type === 'freeship' ? 'Freeship' : `Giảm ${money(value)}`
  return Number(r.min_subtotal) > 0 ? `${main} • đơn từ ${money(Number(r.min_subtotal))}` : main
}

const fmtDate = (d: unknown) => d ? new Date(`${String(d).slice(0, 10)}T00:00:00`).toLocaleDateString('vi-VN') : null

/** Admin: every code (platform-wide ones apply to all restaurants). Owner: codes for their restaurant only. */
export function VouchersManager({ restaurantId }: { restaurantId?: number }) {
  return (
    <EntityManager
      table="fg_vouchers" title="Mã giảm giá" select="*, fg_used_count, restaurant:fg_restaurants(name)" orderBy="created_at" searchKey="code"
      match={restaurantId ? { restaurant_id: restaurantId } : undefined}
      fields={[
        { key: 'code', label: 'Mã (VD: GIAM20K)', type: 'text', required: true, hint: 'Tự chuyển thành chữ HOA, bỏ khoảng trắng' },
        // admin-created codes stay platform-wide (null); an owner's codes belong to their restaurant
        { key: 'restaurant_id', label: 'Nhà hàng', type: 'hidden', default: restaurantId ?? null },
        { key: 'discount_type', label: 'Loại giảm giá', type: 'select', required: true, options: VOUCHER_TYPES },
        { key: 'discount_value', label: 'Mức giảm (số tiền hoặc %)', type: 'number', required: true, default: 0, hint: 'Freeship: nhập 0' },
        { key: 'max_discount', label: 'Giảm tối đa (đ, cho mã %)', type: 'number' },
        { key: 'min_subtotal', label: 'Đơn tối thiểu (đ, tiền món)', type: 'number', required: true, default: 0 },
        { key: 'usage_limit', label: 'Tổng lượt dùng (trống = không giới hạn)', type: 'number' },
        { key: 'per_user_limit', label: 'Lượt dùng mỗi khách', type: 'number', default: 1, hint: 'Trống = không giới hạn' },
        { key: 'starts_on', label: 'Áp dụng từ ngày', type: 'date' },
        { key: 'expires_on', label: 'Hết hạn sau ngày', type: 'date' },
        { key: 'description', label: 'Mô tả cho khách (VD: Giảm 20k cho đơn từ 80k)', type: 'text', wide: true },
        { key: 'is_active', label: 'Đang bật', type: 'checkbox' },
      ]}
      columns={[
        { label: 'Biểu tượng', render: () => <span className="grid size-12 place-items-center rounded-lg bg-[#fff0eb] text-[#ff5b35]"><TicketPercent className="size-5" /></span> },
        { label: 'Mã', render: r => <b className="font-mono">{String(r.code)}</b> },
        ...(restaurantId ? [] : [{ label: 'Áp dụng', render: (r: Record<string, unknown>) => (r.restaurant as { name: string } | null)?.name ?? 'Mọi nhà hàng' }]),
        { label: 'Ưu đãi', render: r => describeVoucher(r) },
        { label: 'Đã dùng', render: r => `${Number(r.fg_used_count ?? 0)}${r.usage_limit ? ` / ${r.usage_limit}` : ''}` },
        { label: 'Hạn dùng', render: r => [fmtDate(r.starts_on), fmtDate(r.expires_on)].some(Boolean) ? `${fmtDate(r.starts_on) ?? '…'} → ${fmtDate(r.expires_on) ?? '…'}` : 'Không thời hạn' },
        { label: 'Trạng thái', render: r => yesNo(r.is_active && !(r.expires_on && String(r.expires_on) < new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })), 'Đang bật', r.is_active ? 'Hết hạn' : 'Đã tắt') },
      ]}
    />
  )
}
