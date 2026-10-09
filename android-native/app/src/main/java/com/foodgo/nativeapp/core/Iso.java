package com.foodgo.nativeapp.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ISO-8601 timestamps from Postgres (minSdk 24 has no java.time). */
public final class Iso {
    private Iso() {}

    private static final Pattern P = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d+))?)?)?\\s*(Z|[+-]\\d{2}(?::?\\d{2})?)?");

    /** Millis since epoch; a bare date ("2026-10-08") is local midnight, like `new Date('2026-10-08T00:00:00')`. */
    public static long parse(String s) {
        if (s == null) return 0;
        Matcher m = P.matcher(s.trim());
        if (!m.find()) return 0;
        boolean hasTime = m.group(4) != null;
        String zone = m.group(8);
        Calendar c = Calendar.getInstance(hasTime && zone != null ? TimeZone.getTimeZone("UTC") : TimeZone.getDefault());
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                hasTime ? Integer.parseInt(m.group(4)) : 0, hasTime ? Integer.parseInt(m.group(5)) : 0,
                m.group(6) != null ? Integer.parseInt(m.group(6)) : 0);
        long ms = c.getTimeInMillis();
        if (m.group(7) != null) {
            String f = (m.group(7) + "000").substring(0, 3);
            ms += Integer.parseInt(f);
        }
        if (hasTime && zone != null && !zone.equals("Z")) {
            int sign = zone.charAt(0) == '-' ? -1 : 1;
            String z = zone.substring(1).replace(":", "");
            int h = Integer.parseInt(z.substring(0, 2));
            int mi = z.length() >= 4 ? Integer.parseInt(z.substring(2, 4)) : 0;
            ms -= sign * (h * 3600000L + mi * 60000L);
        }
        return ms;
    }

    /** UTC ISO string, like Date.toISOString(). */
    public static String format(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }
}
