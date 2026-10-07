import { createClient } from '@supabase/supabase-js'
import { NextResponse } from 'next/server'

const clean = (v?: string) => v?.replace(/^﻿/, '').trim()

/**
 * SePay webhook (https://docs.sepay.vn/tich-hop-webhooks.html): bank balance changes for the
 * "Chuyển khoản QR" orders. SePay config: URL = https://<domain>/api/payment/webhook,
 * auth = "API Key" with the value stored in fg_secrets.sepay_api_key.
 *
 * The key check, duplicate protection (by SePay transaction id), order matching and the
 * "paid" update all happen inside fg_sepay_confirm(), so only the public anon key is needed here.
 */
export async function POST(req: Request) {
  const apiKey = req.headers.get('authorization')?.replace(/^Apikey\s+/i, '') ?? ''
  const payload = await req.json().catch(() => null)
  if (!payload) return NextResponse.json({ success: false, message: 'invalid body' }, { status: 400 })

  const supabase = createClient(clean(process.env.NEXT_PUBLIC_SUPABASE_URL)!, clean(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY)!, { auth: { persistSession: false } })
  const { data, error } = await supabase.rpc('fg_sepay_confirm', { p: payload, p_key: apiKey })
  if (error) {
    console.error('sepay webhook', error.message)
    return NextResponse.json({ success: false, message: 'server error' }, { status: 500 }) // SePay retries
  }
  if (data === 'unauthorized') return NextResponse.json({ success: false, message: 'unauthorized' }, { status: 401 })
  // every other outcome (ok, duplicate, no matching order, amount too low…) is final – don't make SePay retry
  return NextResponse.json({ success: true, message: data })
}
