import { createClient } from '@supabase/supabase-js'
import { NextResponse } from 'next/server'

const clean = (v?: string) => v?.replace(/^﻿/, '').trim()

/**
 * MoMo IPN (server-to-server payment result). The HMAC signature is verified inside the database by
 * fg_momo_confirm(), so the public anon key is enough here – a forged request is simply rejected.
 */
export async function POST(req: Request) {
  const payload = await req.json().catch(() => null)
  if (!payload) return new NextResponse(null, { status: 400 })
  const supabase = createClient(clean(process.env.NEXT_PUBLIC_SUPABASE_URL)!, clean(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY)!, { auth: { persistSession: false } })
  const { data, error } = await supabase.rpc('fg_momo_confirm', { p: payload })
  if (error) console.error('momo ipn', error.message)
  else if (data !== 'ok' && data !== 'already_paid') console.warn('momo ipn rejected:', data, payload?.orderId)
  // MoMo only needs a 204 acknowledgement
  return new NextResponse(null, { status: 204 })
}
