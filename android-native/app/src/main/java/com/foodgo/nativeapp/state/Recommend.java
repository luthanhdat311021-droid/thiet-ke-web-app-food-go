package com.foodgo.nativeapp.state;

import android.content.Context;
import android.content.SharedPreferences;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Recommendation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * lib/recommend.ts: behaviour history feeding the recommender.
 * Signed in: stored server-side by fg_track(). Guests: kept on this device and sent along with each request.
 */
public final class Recommend {
    private Recommend() {}

    private static final String RECENT_KEY = "foodgo-recent";

    private static SharedPreferences prefs() { return App.get().getSharedPreferences("foodgo", Context.MODE_PRIVATE); }

    private static final class Recent { List<Long> foods = new ArrayList<>(); List<String> queries = new ArrayList<>(); }

    private static Recent readRecent() {
        Recent r = new Recent();
        JsonElement e = Json.parse(prefs().getString(RECENT_KEY, "{}"));
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("foods") && o.get("foods").isJsonArray()) for (JsonElement x : o.getAsJsonArray("foods")) try { r.foods.add(x.getAsLong()); } catch (Exception ignored) {}
            if (o.has("queries") && o.get("queries").isJsonArray()) for (JsonElement x : o.getAsJsonArray("queries")) try { r.queries.add(x.getAsString()); } catch (Exception ignored) {}
        }
        return r;
    }

    /** Fire-and-forget: a failed log must never get in the customer's way. kind: view | search | cart */
    public static void track(String kind, Object value, boolean signedIn) {
        if (signedIn) {
            JsonObject args = "search".equals(kind) ? Json.obj("p_kind", kind, "p_query", String.valueOf(value)) : Json.obj("p_kind", kind, "p_food_id", value);
            Db.rpc("fg_track", args, (d, e) -> {});
            return;
        }
        Recent r = readRecent();
        if ("search".equals(kind)) {
            String q = String.valueOf(value).trim();
            if (q.length() > 80) q = q.substring(0, 80);
            if (!q.isEmpty()) {
                List<String> next = new ArrayList<>();
                next.add(q);
                for (String x : r.queries) if (!x.toLowerCase(Locale.ROOT).equals(q.toLowerCase(Locale.ROOT))) next.add(x);
                r.queries = next.size() > 10 ? next.subList(0, 10) : next;
            }
        } else {
            long id = ((Number) value).longValue();
            List<Long> next = new ArrayList<>();
            next.add(id);
            for (Long x : r.foods) if (x != id) next.add(x);
            r.foods = next.size() > 20 ? next.subList(0, 20) : next;
        }
        prefs().edit().putString(RECENT_KEY, Json.gson.toJson(Json.obj("foods", r.foods, "queries", r.queries))).apply();
    }

    /** Loads the dishes behind an RPC's [{food_id, reason}] rows, keeping the RPC's order. */
    private static void withFoods(JsonElement rows, Cb<List<Recommendation>> cb) {
        if (rows == null || !rows.isJsonArray() || rows.getAsJsonArray().size() == 0) { cb.done(new ArrayList<>(), null); return; }
        JsonArray arr = rows.getAsJsonArray();
        List<Long> ids = new ArrayList<>();
        for (JsonElement e : arr) ids.add(e.getAsJsonObject().get("food_id").getAsLong());
        Db.from("fg_foods").select(Store.FOOD_SELECT).in("id", ids).list(Food.class, (foods, err) -> {
            Map<Long, Food> byId = new HashMap<>();
            for (Food f : foods) byId.put(f.id, f);
            List<Recommendation> out = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                Food f = byId.get(o.get("food_id").getAsLong());
                if (f != null) out.add(new Recommendation(f, Json.str(o, "reason", "")));
            }
            cb.done(out, null);
        });
    }

    /** "Gợi ý cho bạn" */
    public static void recommendations(boolean signedIn, int limit, Cb<List<Recommendation>> cb) {
        Recent recent = signedIn ? new Recent() : readRecent();
        Db.rpc("fg_recommend_foods", Json.obj("p_limit", limit, "p_recent_food_ids", recent.foods, "p_recent_queries", recent.queries),
                (data, err) -> { if (err != null) cb.done(new ArrayList<>(), null); else withFoods(data, cb); });
    }

    /** "Có thể bạn cũng thích" for one dish */
    public static void similar(long foodId, int limit, Cb<List<Recommendation>> cb) {
        Db.rpc("fg_similar_foods", Json.obj("p_food_id", foodId, "p_limit", limit),
                (data, err) -> { if (err != null) cb.done(new ArrayList<>(), null); else withFoods(data, cb); });
    }
}
