package com.foodgo.nativeapp.util;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.core.Iso;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** lib/format.ts */
public final class Fmt {
    private Fmt() {}

    private static final DecimalFormat GROUPED;
    static {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.US);
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        GROUPED = new DecimalFormat("#,##0", s);
    }

    /** 15000 → "15.000đ" (n.toLocaleString('vi-VN')) */
    public static String money(long n) { return GROUPED.format(n) + "đ"; }

    public static String number(long n) { return GROUPED.format(n); }

    public static final long FREE_SHIP_FROM = 100000;
    public static final long SHIPPING_FEE = 15000;

    public static long shippingFee(long subtotal) { return subtotal >= FREE_SHIP_FROM ? 0 : SHIPPING_FEE; }

    public static final String[][] ORDER_STEPS = {
            {"pending", "Đơn hàng đã đặt"},
            {"confirmed", "Nhà hàng đã xác nhận"},
            {"preparing", "Đang chuẩn bị món"},
            {"picking_up", "Tài xế đang lấy hàng"},
            {"delivering", "Đang giao hàng"},
            {"delivered", "Đã giao"},
    };

    public static int stepIndex(String status) {
        for (int i = 0; i < ORDER_STEPS.length; i++) if (ORDER_STEPS[i][0].equals(status)) return i;
        return -1;
    }

    public static String stepLabel(String status) {
        int i = stepIndex(status);
        return i >= 0 ? ORDER_STEPS[i][1] : null;
    }

    public static final Map<String, String> STATUS_LABEL = new LinkedHashMap<>();
    static {
        STATUS_LABEL.put("pending", "Chờ xác nhận");
        STATUS_LABEL.put("confirmed", "Đã xác nhận");
        STATUS_LABEL.put("preparing", "Đang chuẩn bị");
        STATUS_LABEL.put("picking_up", "Đang lấy hàng");
        STATUS_LABEL.put("delivering", "Đang giao");
        STATUS_LABEL.put("delivered", "Hoàn thành");
        STATUS_LABEL.put("cancelled", "Đã hủy");
    }

    public static String statusLabel(String s) { String l = STATUS_LABEL.get(s); return l != null ? l : s; }

    /** [background, text] colours of the status pill (STATUS_STYLE). */
    public static int[] statusStyle(String s) {
        switch (s == null ? "" : s) {
            case "pending": return new int[]{0xFFFFF7DF, 0xFFBD8300};
            case "delivered": return new int[]{0xFFE4F8EB, 0xFF3EAA68};
            case "cancelled": return new int[]{0xFFF4F0EE, 0xFF9C918C};
            default: return new int[]{0xFFFFF0EB, 0xFFFF5B35};
        }
    }

    public static String paymentLabel(String m) {
        switch (m == null ? "" : m) {
            case "cod": return "Tiền mặt khi nhận hàng";
            case "qr": return "Chuyển khoản QR";
            case "momo": return "Ví MoMo";
            default: return m;
        }
    }

    /** "14:05 08/10/2026" (toLocaleString('vi-VN', { hour, minute, day, month, year })) */
    public static String dateTime(String iso) {
        return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.US).format(new Date(Iso.parse(iso)));
    }

    /** "8/10/2026" (toLocaleDateString('vi-VN')) */
    public static String date(long ms) {
        return new SimpleDateFormat("d/M/yyyy", Locale.US).format(new Date(ms));
    }

    /** "08/10" */
    public static String dayMonth(long ms) {
        return new SimpleDateFormat("dd/MM", Locale.US).format(new Date(ms));
    }

    public static String timeAgo(String iso) {
        long s = Math.max(1, (System.currentTimeMillis() - Iso.parse(iso)) / 1000);
        if (s < 60) return "Vừa xong";
        if (s < 3600) return (s / 60) + " phút trước";
        if (s < 86400) return (s / 3600) + " giờ trước";
        return (s / 86400) + " ngày trước";
    }

    private static final String[] WEEKDAYS = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
    private static final String[] WEEKDAYS_LONG = {"Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};

    public static String weekdayShort(long ms) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms);
        return WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** "Thứ Năm, 08/10" (toLocaleDateString('vi-VN', { weekday: 'long', day, month })) */
    public static String weekdayLong(long ms) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms);
        return WEEKDAYS_LONG[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + dayMonth(ms);
    }

    /** VietQR quick link; null when no receiving account is configured. */
    public static String vietQrUrl(long amount, String content) {
        if (Config.VIETQR_BANK_ID.isEmpty() || Config.VIETQR_ACCOUNT_NO.isEmpty()) return null;
        return "https://img.vietqr.io/image/" + Config.VIETQR_BANK_ID + "-" + Config.VIETQR_ACCOUNT_NO + "-compact2.png"
                + "?amount=" + amount + "&addInfo=" + enc(content) + "&accountName=" + enc(Config.VIETQR_ACCOUNT_NAME);
    }

    /** URLSearchParams encoding (spaces as '+'). */
    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /** n >= 1000 → "1.2k" (formatCount in cards.tsx) */
    public static String count(int n) {
        if (n < 1000) return String.valueOf(n);
        return String.format(Locale.US, "%.1f", n / 1000.0).replace(".0", "") + "k";
    }

    /** How JS prints a number in a template string: 2 → "2", 1.5 → "1.5". */
    public static String js(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        String s = String.valueOf(v);
        return s.contains("E") ? new java.math.BigDecimal(v).stripTrailingZeros().toPlainString() : s;
    }

    /** Number(x).toFixed(1) */
    public static String fixed1(double v) { return String.format(Locale.US, "%.1f", v); }

    /** 0.125 → "12.5%" (pct in stats.tsx) */
    public static String pct(double r) { return String.format(Locale.US, "%.1f", r * 100).replace(".0", "") + "%"; }

    public static String initials(String s) {
        if (s == null || s.isEmpty()) s = "?";
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, words.length - 2);
        for (int i = start; i < words.length; i++) if (!words[i].isEmpty()) sb.append(words[i].charAt(0));
        // JS: split(' ').map(w => w[0]) keeps empty words as undefined → joined as ''
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
