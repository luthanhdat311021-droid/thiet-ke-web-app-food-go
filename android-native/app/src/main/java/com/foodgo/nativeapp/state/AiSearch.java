package com.foodgo.nativeapp.state;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI search (app/api/ai/search on Vercel, Claude): understands the request and ranks the menu.
 * data = null when the AI is unavailable (not configured, offline, busy) — callers keep the on-device results.
 */
public final class AiSearch {
    private AiSearch() {}

    public static final class Result {
        public final String interpretation;
        /** food id → reason, in the AI's order (best first) */
        public final Map<Long, String> ranked;
        Result(String interpretation, Map<Long, String> ranked) { this.interpretation = interpretation; this.ranked = ranked; }
    }

    // same question twice in a row (back/forward, sort change): no second AI call
    private static final Map<String, Result> cache = new LinkedHashMap<String, Result>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Result> e) { return size() > 30; }
    };

    public static void search(String q, Cb<Result> cb) {
        String key = q.trim().toLowerCase();
        Result hit = cache.get(key);
        if (hit != null) { cb.done(hit, null); return; }
        Net.postJson(Config.SITE + "/api/ai/search", Json.obj("q", q.trim()), (data, err) -> {
            if (err != null || data == null || !data.isJsonObject()) { cb.done(null, err); return; }
            JsonObject o = data.getAsJsonObject();
            Map<Long, String> ranked = new LinkedHashMap<>();
            JsonElement results = o.get("results");
            if (results != null && results.isJsonArray()) {
                for (JsonElement e : results.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    JsonObject r = e.getAsJsonObject();
                    try { ranked.put(r.get("food_id").getAsLong(), Json.str(r, "reason", "")); } catch (Exception ignored) {}
                }
            }
            Result res = new Result(Json.str(o, "interpretation", ""), ranked);
            cache.put(key, res);
            cb.done(res, null);
        });
    }

    @SuppressWarnings("unused")
    private static List<Long> unused() { return new ArrayList<>(); }
}
