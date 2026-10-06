import type { OrderStatus } from './types'

export const money = (n: number) => `${n.toLocaleString('vi-VN')}đ`

export const FREE_SHIP_FROM = 100000
export const SHIPPING_FEE = 15000
export const shippingFee = (subtotal: number) => (subtotal >= FREE_SHIP_FROM ? 0 : SHIPPING_FEE)

export const ORDER_STEPS: { status: OrderStatus; label: string }[] = [
  { status: 'pending', label: 'Đơn hàng đã đặt' },
  { status: 'confirmed', label: 'Nhà hàng xác nhận' },
  { status: 'preparing', label: 'Đang chuẩn bị món' },
  { status: 'picking_up', label: 'Tài xế đang lấy hàng' },
  { status: 'delivering', label: 'Đang giao hàng' },
  { status: 'delivered', label: 'Đã giao' },
]

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending: 'Chờ xác nhận',
  confirmed: 'Đã xác nhận',
  preparing: 'Đang chuẩn bị',
  picking_up: 'Đang lấy hàng',
  delivering: 'Đang giao',
  delivered: 'Hoàn thành',
  cancelled: 'Đã hủy',
}

export const STATUS_STYLE: Record<OrderStatus, string> = {
  pending: 'bg-[#fff7df] text-[#bd8300]',
  confirmed: 'bg-[#fff0eb] text-[#ff5b35]',
  preparing: 'bg-[#fff0eb] text-[#ff5b35]',
  picking_up: 'bg-[#fff0eb] text-[#ff5b35]',
  delivering: 'bg-[#fff0eb] text-[#ff5b35]',
  delivered: 'bg-[#e4f8eb] text-[#3eaa68]',
  cancelled: 'bg-[#f4f0ee] text-[#9c918c]',
}

export const formatDateTime = (iso: string) =>
  new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })

export const timeAgo = (iso: string) => {
  const s = Math.max(1, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return 'Vừa xong'
  if (s < 3600) return `${Math.floor(s / 60)} phút trước`
  if (s < 86400) return `${Math.floor(s / 3600)} giờ trước`
  return `${Math.floor(s / 86400)} ngày trước`
}

// VietQR quick link: https://www.vietqr.io/danh-sach-api/link-tao-ma-nhanh
export function vietQrUrl(amount: number, content: string) {
  const bank = process.env.NEXT_PUBLIC_VIETQR_BANK_ID
  const account = process.env.NEXT_PUBLIC_VIETQR_ACCOUNT_NO
  const name = process.env.NEXT_PUBLIC_VIETQR_ACCOUNT_NAME ?? ''
  if (!bank || !account) return null
  const q = new URLSearchParams({ amount: String(amount), addInfo: content, accountName: name })
  return `https://img.vietqr.io/image/${bank}-${account}-compact2.png?${q}`
}
