// Password rules for sign-up / reset / change (Supabase checks length again on its side).

export const MIN_PASSWORD = 8

/** Instant checks; returns a message or null. */
export function passwordProblem(pw: string): string | null {
  if (pw.length < MIN_PASSWORD) return `Mật khẩu cần ít nhất ${MIN_PASSWORD} ký tự`
  if (!/[a-zA-Z]/.test(pw) || !/\d/.test(pw)) return 'Mật khẩu cần có cả chữ và số'
  if (/^(.)\1+$/.test(pw) || /^(?:0123456789|123456789|12345678|abcd1234|password\d*|matkhau\d*)$/i.test(pw)) return 'Mật khẩu quá dễ đoán'
  return null
}

/**
 * Times this password appeared in public data breaches (Have I Been Pwned, k-anonymity):
 * only the first 5 hex chars of its SHA-1 leave the device, never the password.
 * Returns 0 when the service can't be reached, so an outage never blocks sign-up.
 */
export async function breachCount(pw: string): Promise<number> {
  try {
    const digest = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(pw))
    const hex = [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase()
    const res = await fetch(`https://api.pwnedpasswords.com/range/${hex.slice(0, 5)}`, {
      headers: { 'Add-Padding': 'true' },
      signal: AbortSignal.timeout(5000),
    })
    if (!res.ok) return 0
    const suffix = hex.slice(5)
    for (const line of (await res.text()).split('\n')) {
      const [s, count] = line.trim().split(':')
      if (s === suffix) return Number(count) || 0
    }
    return 0
  } catch {
    return 0
  }
}

/** Full check for a NEW password: rules first, then the breach list. */
export async function validateNewPassword(pw: string): Promise<string | null> {
  const problem = passwordProblem(pw)
  if (problem) return problem
  const seen = await breachCount(pw)
  if (seen > 0) return `Mật khẩu này đã bị lộ ${seen.toLocaleString('vi-VN')} lần trong các vụ rò rỉ dữ liệu. Hãy chọn mật khẩu khác.`
  return null
}
