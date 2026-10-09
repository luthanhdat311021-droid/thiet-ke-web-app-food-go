package com.foodgo.nativeapp.core;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.util.Validate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Minimal PostgREST client mirroring the supabase-js calls the web app makes:
 * Db.from("fg_foods").select("*").eq("id", 1).order("name", true).run(cb)
 */
public final class Db {
    private final String table;
    private final List<String[]> params = new ArrayList<>();
    private final List<String> orders = new ArrayList<>();
    private String method = "GET";
    private JsonElement body;
    private boolean returning;
    private boolean countOnly;

    private Db(String table) { this.table = table; }

    public static Db from(String table) { return new Db(table); }

    public Db select(String columns) {
        params.add(new String[]{"select", columns.replaceAll("\\s+", "")});
        if (!method.equals("GET")) returning = true;
        return this;
    }

    private Db filter(String col, String expr) { params.add(new String[]{col, expr}); return this; }

    private static String lit(Object v) { return v instanceof Boolean ? String.valueOf(v) : String.valueOf(v); }

    public Db eq(String col, Object v) { return filter(col, "eq." + lit(v)); }
    public Db neq(String col, Object v) { return filter(col, "neq." + lit(v)); }
    public Db gte(String col, Object v) { return filter(col, "gte." + lit(v)); }
    public Db lte(String col, Object v) { return filter(col, "lte." + lit(v)); }
    public Db isNull(String col) { return filter(col, "is.null"); }
    public Db notNull(String col) { return filter(col, "not.is.null"); }

    public Db in(String col, Collection<?> values) {
        StringBuilder sb = new StringBuilder("in.(");
        boolean first = true;
        for (Object v : values) { if (!first) sb.append(','); sb.append(lit(v)); first = false; }
        return filter(col, sb.append(')').toString());
    }

    /** Raw PostgREST or() expression, e.g. "name.ilike.%x%,description.ilike.%x%". */
    public Db or(String expr) { return filter("or", "(" + expr + ")"); }

    public Db order(String col) { return order(col, true); }
    public Db order(String col, boolean asc) { orders.add(col + (asc ? ".asc" : ".desc")); return this; }
    public Db order(String col, boolean asc, boolean nullsFirst) { orders.add(col + (asc ? ".asc" : ".desc") + (nullsFirst ? ".nullsfirst" : ".nullslast")); return this; }

    public Db limit(int n) { return filter("limit", String.valueOf(n)); }

    /** Rows from..to inclusive, like .range(). */
    public Db range(int from, int to) { filter("offset", String.valueOf(from)); return filter("limit", String.valueOf(to - from + 1)); }

    /** .select('id', { count: 'exact', head: true }) */
    public Db count() { countOnly = true; return this; }

    public Db insert(JsonElement rowOrRows) { method = "POST"; body = rowOrRows; return this; }
    public Db update(JsonObject patch) { method = "PATCH"; body = patch; return this; }
    public Db delete() { method = "DELETE"; return this; }

    private Request build(String token) {
        HttpUrl.Builder url = HttpUrl.get(Config.SUPABASE_URL + "/rest/v1/" + table).newBuilder();
        for (String[] p : params) url.addQueryParameter(p[0], p[1]);
        if (!orders.isEmpty()) url.addQueryParameter("order", String.join(",", orders));
        Request.Builder rb = new Request.Builder().url(url.build()).header("apikey", Config.SUPABASE_KEY);
        if (token != null) rb.header("Authorization", "Bearer " + token);
        if (countOnly) return rb.header("Prefer", "count=exact").head().build();
        switch (method) {
            case "POST":
                rb.header("Prefer", returning ? "return=representation" : "return=minimal");
                return rb.post(RequestBody.create(Json.gson.toJson(body), Net.JSON)).build();
            case "PATCH":
                rb.header("Prefer", returning ? "return=representation" : "return=minimal");
                return rb.patch(RequestBody.create(Json.gson.toJson(body), Net.JSON)).build();
            case "DELETE":
                rb.header("Prefer", "return=minimal");
                return rb.delete().build();
            default:
                return rb.get().build();
        }
    }

    /** data = JSON body (array for selects), or JsonNull for writes without .select(). */
    public void run(Cb<JsonElement> cb) {
        Net.bg(() -> {
            Net.Res res = Net.exec(build(Auth.accessToken()));
            JsonElement json = Json.parse(res.body);
            String err = res.ok() ? null : errorText(res, json);
            Net.main(() -> cb.done(res.ok() ? json : null, err));
        });
    }

    public <T> void list(Class<T> type, Cb<List<T>> cb) {
        run((data, err) -> cb.done(err == null ? Json.list(data, type) : new ArrayList<>(), err));
    }

    /** First row or null, like .maybeSingle(). */
    public <T> void maybeSingle(Class<T> type, Cb<T> cb) {
        run((data, err) -> {
            T row = null;
            if (data != null && data.isJsonArray() && data.getAsJsonArray().size() > 0) row = Json.as(data.getAsJsonArray().get(0), type);
            else if (data != null && data.isJsonObject()) row = Json.as(data, type);
            cb.done(row, err);
        });
    }

    public void rows(Cb<List<JsonObject>> cb) {
        run((data, err) -> {
            List<JsonObject> out = new ArrayList<>();
            if (data != null && data.isJsonArray()) for (JsonElement e : data.getAsJsonArray()) if (e.isJsonObject()) out.add(e.getAsJsonObject());
            cb.done(out, err);
        });
    }

    /** Exact row count from Content-Range ("0-24/123" or "* /123"). */
    public void runCount(Cb<Integer> cb) {
        countOnly = true;
        Net.bg(() -> {
            Net.Res res = Net.exec(build(Auth.accessToken()));
            int n = 0;
            if (res.headers != null) {
                String cr = res.headers.get("Content-Range");
                if (cr != null && cr.contains("/")) {
                    try { n = Integer.parseInt(cr.substring(cr.indexOf('/') + 1)); } catch (NumberFormatException ignored) {}
                }
            }
            int count = n;
            Net.main(() -> cb.done(count, res.ok() ? null : errorText(res, JsonNull.INSTANCE)));
        });
    }

    // ------------------------------------------------------------------ rpc

    public static void rpc(String fn, JsonObject args, Cb<JsonElement> cb) {
        Net.bg(() -> {
            String token = Auth.accessToken();
            Request.Builder rb = new Request.Builder().url(Config.SUPABASE_URL + "/rest/v1/rpc/" + fn)
                    .header("apikey", Config.SUPABASE_KEY)
                    .post(RequestBody.create(Json.gson.toJson(args != null ? args : new JsonObject()), Net.JSON));
            if (token != null) rb.header("Authorization", "Bearer " + token);
            Net.Res res = Net.exec(rb.build());
            JsonElement json = Json.parse(res.body);
            String err = res.ok() ? null : errorText(res, json);
            Net.main(() -> cb.done(res.ok() ? json : null, err));
        });
    }

    public static <T> void rpcList(String fn, JsonObject args, Class<T> type, Cb<List<T>> cb) {
        rpc(fn, args, (d, e) -> cb.done(Json.list(d, type), e));
    }

    // ------------------------------------------------------------------ storage

    /** Uploads into a public bucket; data = the public URL. */
    public static void upload(String bucket, String path, byte[] bytes, String contentType, Cb<String> cb) {
        Net.bg(() -> {
            String token = Auth.accessToken();
            String encoded = encodePath(path);
            Request.Builder rb = new Request.Builder().url(Config.SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + encoded)
                    .header("apikey", Config.SUPABASE_KEY)
                    .header("x-upsert", "false")
                    .post(RequestBody.create(bytes, okhttp3.MediaType.parse(contentType != null ? contentType : "application/octet-stream")));
            if (token != null) rb.header("Authorization", "Bearer " + token);
            Net.Res res = Net.exec(rb.build());
            String url = Config.SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + encoded;
            String err = res.ok() ? null : errorText(res, Json.parse(res.body));
            Net.main(() -> cb.done(res.ok() ? url : null, err));
        });
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) { if (i > 0) sb.append('/'); sb.append(android.net.Uri.encode(parts[i])); }
        return sb.toString();
    }

    // ------------------------------------------------------------------ errors

    private static String errorText(Net.Res res, JsonElement json) {
        if (res.code == 0) return Net.NETWORK_ERROR;
        return friendly(Json.errorOf(json, "Đã có lỗi xảy ra, vui lòng thử lại"));
    }

    /** Same translation as errorMessage() in lib/supabase.ts. */
    public static String friendly(String msg) {
        if (msg == null) return "Đã có lỗi xảy ra, vui lòng thử lại";
        if (msg.matches("(?s).*violates check constraint \"fg_\\w+_phone_check\".*")) return Validate.PHONE_ERROR;
        return msg;
    }

    public static JsonArray arr(Collection<?> c) { return (JsonArray) Json.val(c); }
}
