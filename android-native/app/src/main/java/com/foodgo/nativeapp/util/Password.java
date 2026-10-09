package com.foodgo.nativeapp.util;

import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.core.Net;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/** lib/password.ts */
public final class Password {
    private Password() {}

    public static final int MIN_PASSWORD = 8;

    public static String problem(String pw) {
        if (pw.length() < MIN_PASSWORD) return "Mật khẩu cần ít nhất " + MIN_PASSWORD + " ký tự";
        if (!pw.matches("(?s).*[a-zA-Z].*") || !pw.matches("(?s).*\\d.*")) return "Mật khẩu cần có cả chữ và số";
        if (pw.matches("(.)\\1+") || pw.matches("(?i)0123456789|123456789|12345678|abcd1234|password\\d*|matkhau\\d*")) return "Mật khẩu quá dễ đoán";
        return null;
    }

    private static final OkHttpClient HIBP = Net.http.newBuilder().callTimeout(5, TimeUnit.SECONDS).build();

    /** Have I Been Pwned (k-anonymity): only 5 hex chars of the SHA-1 leave the device. 0 when unreachable. */
    private static long breachCount(String pw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(pw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02X", b));
            String h = hex.toString();
            try (okhttp3.Response r = HIBP.newCall(new Request.Builder().url("https://api.pwnedpasswords.com/range/" + h.substring(0, 5))
                    .header("Add-Padding", "true").build()).execute()) {
                if (!r.isSuccessful() || r.body() == null) return 0;
                String suffix = h.substring(5);
                for (String line : r.body().string().split("\n")) {
                    String[] p = line.trim().split(":");
                    if (p.length == 2 && p[0].equals(suffix)) {
                        try { return Long.parseLong(p[1].trim()); } catch (NumberFormatException e) { return 0; }
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Full check for a NEW password: rules first, then the breach list. data = problem text or null. */
    public static void validateNew(String pw, Cb<String> cb) {
        String p = problem(pw);
        if (p != null) { cb.done(p, null); return; }
        Net.bg(() -> {
            long seen = breachCount(pw);
            String msg = seen > 0 ? "Mật khẩu này đã bị lộ " + Fmt.number(seen) + " lần trong các vụ rò rỉ dữ liệu. Hãy chọn mật khẩu khác." : null;
            Net.main(() -> cb.done(msg, null));
        });
    }
}
