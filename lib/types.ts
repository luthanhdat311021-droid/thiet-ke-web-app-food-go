export type Category = { id: number; name: string; image: string | null; sort: number }

export type Restaurant = {
  id: number
  name: string
  cuisine: string | null
  address: string | null
  image: string | null
  logo: string | null
  rating: number
  review_count: number
  distance_km: number
  delivery_time: string
  tag: string | null
  is_active: boolean
  lat: number | null
  lng: number | null
  /** "HH:MM:SS", Vietnam time; null = no fixed hours */
  open_time: string | null
  close_time: string | null
  /** manual "tạm đóng cửa" switch */
  is_open: boolean
  /** null = run by the admin (always live); otherwise visible only while paid_until is in the future */
  owner_id: string | null
  paid_until: string | null
  phone: string | null
}

export type SubscriptionPayment = {
  id: number
  code: string
  restaurant_id: number
  owner_id: string | null
  months: number
  amount: number
  status: 'pending' | 'paid' | 'cancelled'
  method: 'qr' | 'admin'
  paid_at: string | null
  created_at: string
}

export type Voucher = {
  id: number
  code: string
  description: string | null
  discount_type: 'percent' | 'amount' | 'freeship'
  discount_value: number
  /** null = platform-wide (admin); otherwise only for that restaurant */
  restaurant_id: number | null
  max_discount: number | null
  min_subtotal: number
  usage_limit: number | null
  per_user_limit: number | null
  starts_on: string | null
  expires_on: string | null
  is_active: boolean
}

/** fg_check_voucher() result */
export type VoucherQuote = { code: string; description: string | null; type: Voucher['discount_type']; discount: number }

export type FoodRestaurant = Pick<Restaurant, 'id' | 'name' | 'delivery_time' | 'distance_km' | 'lat' | 'lng' | 'is_open' | 'open_time' | 'close_time'>

export type Food = {
  id: number
  restaurant_id: number
  category_id: number | null
  name: string
  description: string | null
  price: number
  old_price: number | null
  image: string | null
  rating: number
  review_count: number
  sold_count: number
  is_available: boolean
  is_popular: boolean
  /** joined via FOOD_SELECT */
  restaurants?: FoodRestaurant | null
}

/** A dish suggested by fg_recommend_foods() / fg_similar_foods(), with the reason shown to the customer. */
export type Recommendation = { food: Food; reason: string }

export type Profile = {
  id: string
  full_name: string | null
  phone: string | null
  birthday: string | null
  avatar_url: string | null
  role: 'customer' | 'admin'
}

export type Address = {
  id: number
  user_id: string
  label: string
  recipient: string
  phone: string
  address: string
  is_default: boolean
  lat: number | null
  lng: number | null
}

export type OrderStatus = 'pending' | 'confirmed' | 'preparing' | 'picking_up' | 'delivering' | 'delivered' | 'cancelled'

export type OrderItem = { id: number; order_id: number; food_id: number | null; name: string; image: string | null; price: number; qty: number }

export type Order = {
  id: number
  code: string
  user_id: string
  restaurant_id: number | null
  restaurant_name: string
  status: OrderStatus
  payment_method: 'cod' | 'qr' | 'momo'
  payment_status: 'unpaid' | 'paid' | 'refunded'
  subtotal: number
  shipping_fee: number
  discount: number
  voucher_code: string | null
  total: number
  recipient: string
  phone: string
  address: string
  note: string | null
  paid_at: string | null
  created_at: string
  updated_at: string
  delivery_lat: number | null
  delivery_lng: number | null
  momo_trans_id?: string | null
  delivered_at?: string | null
  order_items?: OrderItem[]
  restaurant?: Pick<Restaurant, 'lat' | 'lng' | 'address'> | null
}

export type Review = {
  id: number
  user_id: string
  order_id: number
  food_id: number
  reviewer_name: string
  rating: number
  comment: string | null
  created_at: string
  updated_at: string
}

export type Notification = { id: number; order_id: number | null; title: string; body: string | null; is_read: boolean; created_at: string; link: string | null }

export type CartItem = {
  food_id: number
  name: string
  price: number
  image: string | null
  restaurant_id: number
  restaurant_name: string
  qty: number
}
