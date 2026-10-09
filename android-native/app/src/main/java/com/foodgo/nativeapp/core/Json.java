package com.foodgo.nativeapp.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Json {
    private Json() {}

    /** Reads rows; writes keep explicit nulls (an update to null must reach PostgREST). */
    public static final Gson gson = new GsonBuilder().serializeNulls().create();

    public static JsonElement parse(String s) {
        if (s == null || s.isEmpty()) return JsonNull.INSTANCE;
        try { return JsonParser.parseString(s); } catch (Exception e) { return JsonNull.INSTANCE; }
    }

    public static <T> T as(JsonElement e, Class<T> c) {
        if (e == null || e.isJsonNull()) return null;
        try { return gson.fromJson(e, c); } catch (Exception ex) { return null; }
    }

    public static <T> List<T> list(JsonElement e, Class<T> c) {
        List<T> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return out;
        Type t = TypeToken.getParameterized(List.class, c).getType();
        try {
            List<T> l = gson.fromJson(e, t);
            if (l != null) out.addAll(l);
        } catch (Exception ignored) {}
        return out;
    }

    public static JsonElement tree(Object o) { return gson.toJsonTree(o); }

    public static String str(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return null;
        JsonElement e = o.get(k);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    public static String str(JsonObject o, String k, String def) { String s = str(o, k); return s == null ? def : s; }

    public static double num(JsonObject o, String k, double def) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return def;
        try { return o.get(k).getAsDouble(); } catch (Exception e) { return def; }
    }

    public static Double numOrNull(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return null;
        try { return o.get(k).getAsDouble(); } catch (Exception e) { return null; }
    }

    public static boolean bool(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return false;
        JsonElement e = o.get(k);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return !e.getAsString().isEmpty();
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble() != 0;
        return true;
    }

    public static JsonObject obj(JsonObject o, String k) {
        if (o == null || !o.has(k) || !o.get(k).isJsonObject()) return null;
        return o.getAsJsonObject(k);
    }

    public static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) o.add(String.valueOf(kv[i]), val(kv[i + 1]));
        return o;
    }

    public static JsonElement val(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof JsonElement) return (JsonElement) v;
        if (v instanceof Boolean) return new JsonPrimitive((Boolean) v);
        if (v instanceof Number) return new JsonPrimitive((Number) v);
        if (v instanceof Collection) {
            JsonArray a = new JsonArray();
            for (Object x : (Collection<?>) v) a.add(val(x));
            return a;
        }
        if (v instanceof String) return new JsonPrimitive((String) v);
        return gson.toJsonTree(v);
    }

    /** Error text from a Supabase / API error body (PostgREST, GoTrue or our Next.js routes). */
    public static String errorOf(JsonElement e, String fallback) {
        if (e != null && e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (String k : new String[]{"message", "msg", "error_description", "error"}) {
                String s = str(o, k);
                if (s != null && !s.isEmpty()) return s;
            }
        }
        return fallback;
    }
}
