import Anthropic from '@anthropic-ai/sdk'
import { createClient } from '@supabase/supabase-js'
import { NextResponse } from 'next/server'
import { rateLimit } from '@/lib/rate-limit'

// AI food search for the apps: turns a typed or spoken Vietnamese request ("món gì cay cay dưới 50k",
// "trà sữa ít ngọt", "cơm gà") into the best-matching dishes on the current menu, each with a short reason.
// The API key stays on the server (ANTHROPIC_API_KEY on Vercel); without it the route answers 503 and the
// apps keep their own accent-insensitive search.

const clean = (v?: string) => v?.replace(/^﻿/, '').trim()

type MenuRow = {
  id: number
  name: string
  description: string | null
  price: number
  rating: number
  review_count: number
  sold_count: number
  is_available: boolean
  category: { name: string } | null
  restaurant: { name: string; cuisine: string | null } | null
}

// The menu changes rarely: keep it for a minute per server instance.
let menuCache: { at: number; text: string; ids: Set<number> } | null = null

async function loadMenu() {
  if (menuCache && Date.now() - menuCache.at < 60_000) return menuCache
  // anon key, no user: exactly what a guest may see (RLS hides inactive / unpaid restaurants)
  const supabase = createClient(clean(process.env.NEXT_PUBLIC_SUPABASE_URL)!, clean(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY)!, { auth: { persistSession: false } })
  const { data, error } = await supabase.from('fg_foods')
    .select('id, name, description, price, rating, review_count, sold_count, is_available, category:fg_categories(name), restaurant:fg_restaurants!inner(name, cuisine)')
    .order('id').limit(1000)
  if (error) throw new Error(error.message)
  const rows = (data ?? []) as unknown as MenuRow[]
  // one compact line per dish keeps the prompt small and cacheable
  const text = rows.map(r => [
    `#${r.id}`, r.name,
    r.category?.name ? `[${r.category.name}]` : '',
    `${r.price}đ`,
    r.review_count > 0 ? `${Number(r.rating).toFixed(1)}★(${r.review_count})` : '',
    r.sold_count > 0 ? `bán ${r.sold_count}` : '',
    r.is_available ? '' : '(tạm hết)',
    r.restaurant ? `— ${r.restaurant.name}${r.restaurant.cuisine ? ` (${r.restaurant.cuisine})` : ''}` : '',
    r.description ? `: ${r.description.replace(/\s+/g, ' ').slice(0, 160)}` : '',
  ].filter(Boolean).join(' ')).join('\n')
  menuCache = { at: Date.now(), text, ids: new Set(rows.map(r => r.id)) }
  return menuCache
}

const SYSTEM = `Bạn là trợ lý tìm món của FoodGo, ứng dụng giao đồ ăn ở Việt Nam.
Khách gõ hoặc nói một yêu cầu tìm món (có thể không dấu, sai chính tả, văn nói, lẫn tiếng Anh, do nhận dạng giọng nói chép lại).
Hãy hiểu ý khách rồi chọn từ THỰC ĐƠN bên dưới những món phù hợp nhất, xếp từ sát nhất đến kém hơn.

Quy tắc:
- Chỉ dùng mã món (#id) có trong thực đơn. Không bịa món.
- Hiểu ràng buộc trong câu: giá ("dưới 50k" = dưới 50.000đ), khẩu vị (cay, ngọt, ít béo...), loại món, nhà hàng, số người, bữa ăn, "đánh giá cao", "bán chạy".
- Món tạm hết vẫn có thể trả về nhưng xếp sau món đang bán.
- Trả tối đa 12 món; nếu không món nào hợp thì trả danh sách rỗng.
- "reason": một câu ngắn tiếng Việt (tối đa 10 từ) nói vì sao món hợp, ví dụ "Cay, giá 45.000đ dưới ngân sách".
- "interpretation": một câu ngắn tiếng Việt tóm tắt bạn hiểu khách muốn gì.`

const OUTPUT_SCHEMA = {
  type: 'object',
  properties: {
    interpretation: { type: 'string' },
    results: {
      type: 'array',
      items: {
        type: 'object',
        properties: { food_id: { type: 'integer' }, reason: { type: 'string' } },
        required: ['food_id', 'reason'],
        additionalProperties: false,
      },
    },
  },
  required: ['interpretation', 'results'],
  additionalProperties: false,
}

type AiAnswer = { interpretation: string; results: { food_id: number; reason: string }[] }

export async function POST(req: Request) {
  const limited = rateLimit(req, 'ai-search', 20)
  if (limited) return limited
  if (!clean(process.env.ANTHROPIC_API_KEY)) return NextResponse.json({ error: 'Chưa cấu hình tìm kiếm AI' }, { status: 503 })
  const { q } = await req.json().catch(() => ({})) as { q?: string }
  const query = typeof q === 'string' ? q.trim() : ''
  if (query.length < 2 || query.length > 200) return NextResponse.json({ error: 'Từ khóa không hợp lệ' }, { status: 400 })

  let menu
  try { menu = await loadMenu() } catch { return NextResponse.json({ error: 'Không tải được thực đơn' }, { status: 502 }) }
  if (!menu.ids.size) return NextResponse.json({ interpretation: '', results: [] })

  const client = new Anthropic({ apiKey: clean(process.env.ANTHROPIC_API_KEY), timeout: 25_000, maxRetries: 1 })
  try {
    const response = await client.beta.messages.create({
      model: 'claude-opus-5-5',
      max_tokens: 4000,
      // a search box: keep it quick
      output_config: { effort: 'low', format: { type: 'json_schema', schema: OUTPUT_SCHEMA } },
      // a declined request is retried on Anthropic's recommended fallback model instead of failing
      betas: ['server-side-fallback-2026-07-01'],
      fallbacks: 'default',
      // stable instructions + menu first (cached), the customer's words last
      system: [
        { type: 'text', text: SYSTEM },
        { type: 'text', text: `THỰC ĐƠN (mã #id, tên, [danh mục], giá, đánh giá, đã bán, nhà hàng, mô tả):\n${menu.text}`, cache_control: { type: 'ephemeral' } },
      ],
      messages: [{ role: 'user', content: `Khách tìm: "${query}"` }],
    })
    if (response.stop_reason === 'refusal') return NextResponse.json({ interpretation: '', results: [] })
    const text = response.content.find(b => b.type === 'text')
    if (!text || text.type !== 'text') return NextResponse.json({ error: 'AI không trả lời' }, { status: 502 })
    const answer = JSON.parse(text.text) as AiAnswer
    // keep only real dishes, once each
    const seen = new Set<number>()
    const results = (answer.results ?? []).filter(r => Number.isInteger(r.food_id) && menu.ids.has(r.food_id) && !seen.has(r.food_id) && seen.add(r.food_id)).slice(0, 12)
    return NextResponse.json({ interpretation: answer.interpretation ?? '', results }, { headers: { 'Cache-Control': 'no-store' } })
  } catch (e) {
    if (e instanceof Anthropic.RateLimitError) return NextResponse.json({ error: 'AI đang bận, thử lại sau' }, { status: 429 })
    if (e instanceof Anthropic.APIError) return NextResponse.json({ error: 'AI tạm thời không khả dụng' }, { status: 502 })
    return NextResponse.json({ error: 'Không xử lý được câu tìm kiếm' }, { status: 500 })
  }
}
