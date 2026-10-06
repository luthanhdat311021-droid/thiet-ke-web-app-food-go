import { createClient, type SupabaseClient } from '@supabase/supabase-js'

// strip stray BOM/whitespace that some shells add when env values are piped in
const clean = (v?: string) => v?.replace(/^﻿/, '').trim() || undefined
const url = clean(process.env.NEXT_PUBLIC_SUPABASE_URL)
const anonKey = clean(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY)

export const isSupabaseConfigured = Boolean(url && anonKey)

// Placeholder values keep the app renderable before .env.local is filled in;
// every page checks isSupabaseConfigured and shows the setup notice instead of querying.
export const supabase: SupabaseClient = createClient(
  url || 'https://placeholder.supabase.co',
  anonKey || 'placeholder-anon-key',
  { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } },
)

export function errorMessage(e: unknown) {
  if (e && typeof e === 'object' && 'message' in e) return String((e as { message: unknown }).message)
  return 'Đã có lỗi xảy ra, vui lòng thử lại'
}
