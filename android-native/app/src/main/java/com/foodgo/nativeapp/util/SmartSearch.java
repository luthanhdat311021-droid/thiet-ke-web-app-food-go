package com.foodgo.nativeapp.util;

import com.foodgo.nativeapp.model.Food;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device search that forgives what people actually type or say: no accents ("tra sua" → "Trà sữa"),
 * small typos ("pizaa"), word order, filler words ("cho tôi món…") and a price cap ("dưới 50k").
 * Used on its own, and as the fallback / tail when the AI search is unavailable.
 */
public final class SmartSearch {
    private SmartSearch() {}

    /** lower case, accents and đ removed, punctuation → spaces */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replaceAll("[^a-z0-9]+", " ").trim();
    }

    // words that carry no meaning in a food request (after normalize)
    private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
            "toi", "minh", "muon", "an", "uong", "mon", "cho", "tim", "kiem", "giup", "voi", "di", "nhe", "nha", "oi", "a",
            "gi", "nao", "co", "khong", "la", "va", "hay", "mot", "it", "nhung", "cac", "o", "dau", "ngon", "thi", "con", "ban", "quan"));

    public static final class Query {
        public final List<String> tokens;
        public final String phrase;
        public final Long maxPrice;
        Query(List<String> tokens, String phrase, Long maxPrice) { this.tokens = tokens; this.phrase = phrase; this.maxPrice = maxPrice; }
    }

    private static final Pattern PRICE = Pattern.compile("(?:duoi|re hon|toi da|khong qua|max|<)\\s*(\\d+(?:[.,]\\d+)?)\\s*(k|nghin|ngan|trieu|tr|d|dong)?");

    public static Query parse(String q) {
        String n = normalize(q.replace("<", " < "));
        Long max = null;
        Matcher m = PRICE.matcher(n);
        if (m.find()) {
            double v = Double.parseDouble(m.group(1).replace(',', '.'));
            String unit = m.group(2);
            if (unit == null || unit.equals("k") || unit.equals("nghin") || unit.equals("ngan")) v *= v < 1000 ? 1000 : 1;
            else if (unit.equals("trieu") || unit.equals("tr")) v *= 1_000_000;
            max = Math.round(v);
            n = (n.substring(0, m.start()) + " " + n.substring(m.end())).trim();
        }
        List<String> all = new ArrayList<>(Arrays.asList(n.isEmpty() ? new String[0] : n.split("\\s+")));
        List<String> meaningful = new ArrayList<>();
        for (String t : all) if (!FILLER.contains(t)) meaningful.add(t);
        // a query made only of "filler" words (e.g. "an vat") still searches for them
        List<String> tokens = meaningful.isEmpty() ? all : meaningful;
        return new Query(tokens, String.join(" ", tokens), max);
    }

    private static int distance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) return limit + 1;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int best = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                best = Math.min(best, cur[j]);
            }
            if (best > limit) return limit + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** 0..1: how well one query word matches a text */
    private static double tokenMatch(String token, String text, String[] words) {
        if (text.isEmpty()) return 0;
        double best = 0;
        for (String w : words) {
            if (w.equals(token)) return 1;
            if (token.length() >= 2 && w.startsWith(token)) best = Math.max(best, 0.85);
            else if (w.length() >= 3 && token.startsWith(w)) best = Math.max(best, 0.6);
            else if (token.length() >= 4) {
                int limit = token.length() >= 7 ? 2 : 1;
                if (distance(token, w, limit) <= limit) best = Math.max(best, 0.65);
            }
        }
        if (best < 0.7 && token.length() >= 3 && text.contains(token)) best = Math.max(best, 0.7);
        return best;
    }

    private static final double W_NAME = 5, W_CATEGORY = 3, W_RESTAURANT = 2, W_DESCRIPTION = 1;

    /** Relevance of a dish; 0 = not a match. categoryNames: category id → name */
    public static double score(Food f, Query q, Map<Long, String> categoryNames) {
        if (q.maxPrice != null && f.price > q.maxPrice) return 0;
        if (q.tokens.isEmpty()) return q.maxPrice != null ? 1 : 0;
        String name = normalize(f.name);
        String cat = normalize(f.category_id != null ? categoryNames.get(f.category_id) : null);
        String rest = normalize(f.restaurants != null ? f.restaurants.name : null);
        String desc = normalize(f.description);
        String[] nw = name.split(" "), cw = cat.split(" "), rw = rest.split(" "), dw = desc.split(" ");
        double total = 0;
        int matched = 0;
        for (String t : q.tokens) {
            double s = Math.max(Math.max(W_NAME * tokenMatch(t, name, nw), W_CATEGORY * tokenMatch(t, cat, cw)),
                    Math.max(W_RESTAURANT * tokenMatch(t, rest, rw), W_DESCRIPTION * tokenMatch(t, desc, dw)));
            if (s > 0) matched++;
            total += s;
        }
        // most of the words must be found somewhere
        if (matched == 0 || matched * 2 < q.tokens.size()) return 0;
        if (name.contains(q.phrase)) total += 6;
        else if (q.tokens.size() > 1 && (cat.contains(q.phrase) || desc.contains(q.phrase))) total += 3;
        total *= (double) matched / q.tokens.size();
        if (!f.is_available) total *= 0.7;
        // popularity breaks ties
        return total + Math.min(0.5, f.sold_count / 2000.0) + (f.review_count > 0 ? f.rating / 50.0 : 0);
    }

    /** Does a restaurant name/cuisine match the query (accent-insensitive)? */
    public static boolean matchesText(String text, Query q) {
        if (q.tokens.isEmpty()) return false;
        String n = normalize(text);
        String[] words = n.split(" ");
        for (String t : q.tokens) if (tokenMatch(t, n, words) < 0.65) return false;
        return true;
    }
}
