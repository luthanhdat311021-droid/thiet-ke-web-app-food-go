package com.foodgo.nativeapp.util;

/** lib/validate.ts */
public final class Validate {
    private Validate() {}

    /** Drops characters a phone number can't contain while the user types. */
    public static String phoneInput(String v) {
        String s = v.replaceAll("[^\\d+\\s.]", "");
        return s.length() > 16 ? s.substring(0, 16) : s;
    }

    /** "0912 345 678", "+84 912.345.678" → "0912345678"; null if not a Vietnamese mobile/landline. */
    public static String normalizePhone(String v) {
        if (v == null) return null;
        String d = v.replaceAll("[\\s.-]", "").replaceFirst("^\\+?84", "0");
        return d.matches("0(?:[35789]\\d{8}|2\\d{9})") ? d : null;
    }

    public static final String PHONE_ERROR = "Số điện thoại không hợp lệ (VD: 0912 345 678)";

    public static boolean isValidEmail(String v) {
        return v != null && v.trim().matches("(?i)[^\\s@]+@[^\\s@]+\\.[a-z]{2,}");
    }

    public static final String EMAIL_ERROR = "Email không hợp lệ (VD: ten@gmail.com)";
}
