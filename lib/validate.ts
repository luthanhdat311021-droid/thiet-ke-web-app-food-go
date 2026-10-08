/** Drops characters a phone number can't contain while the user types. */
export const phoneInput = (v: string) => v.replace(/[^\d+\s.]/g, '').slice(0, 16)

/**
 * "0912 345 678", "+84 912.345.678" → "0912345678".
 * Accepts Vietnamese mobiles (10 digits: 03/05/07/08/09) and landlines (11 digits: 02x);
 * returns null for anything else.
 */
export function normalizePhone(v: string): string | null {
  const d = v.replace(/[\s.-]/g, '').replace(/^\+?84/, '0')
  return /^0(?:[35789]\d{8}|2\d{9})$/.test(d) ? d : null
}

export const PHONE_ERROR = 'Số điện thoại không hợp lệ (VD: 0912 345 678)'

/** Stricter than <input type="email">, which also accepts "a@b" with no domain suffix. */
export const isValidEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(v.trim())

export const EMAIL_ERROR = 'Email không hợp lệ (VD: ten@gmail.com)'
