package com.foodgo.nativeapp.core;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class Net {
    private Net() {}

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    public static final String NETWORK_ERROR = "Không có kết nối mạng, vui lòng thử lại";

    public static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    public static void main(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else MAIN.post(r);
    }

    public static void later(Runnable r, long ms) { MAIN.postDelayed(r, ms); }

    public static void cancel(Runnable r) { MAIN.removeCallbacks(r); }

    public static void bg(Runnable r) { POOL.execute(r); }

    /** Plain HTTP response: status + body text (null body on network failure). */
    public static final class Res {
        public final int code;
        public final String body;
        public final okhttp3.Headers headers;
        Res(int code, String body, okhttp3.Headers headers) { this.code = code; this.body = body; this.headers = headers; }
        public boolean ok() { return code >= 200 && code < 300; }
    }

    /** Blocking call; use from a background thread. Returns code 0 on network errors. */
    public static Res exec(Request req) {
        try (Response r = http.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "";
            return new Res(r.code(), body, r.headers());
        } catch (IOException e) {
            return new Res(0, null, null);
        }
    }

    /** POST a JSON body; callback on main thread with the parsed reply (or error). */
    public static void postJson(String url, com.google.gson.JsonObject body, Cb<com.google.gson.JsonElement> cb) {
        bg(() -> {
            Res res = exec(new Request.Builder().url(url)
                    .post(okhttp3.RequestBody.create(Json.gson.toJson(body), JSON)).build());
            com.google.gson.JsonElement json = Json.parse(res.body);
            String err = res.ok() ? null : (res.code == 0 ? NETWORK_ERROR : Json.errorOf(json, "Đã có lỗi xảy ra, vui lòng thử lại"));
            main(() -> cb.done(res.ok() ? json : null, err));
        });
    }

    /** GET a JSON url in the background; callback on main thread with the parsed body (or error). */
    public static void getJson(String url, Cb<com.google.gson.JsonElement> cb) {
        bg(() -> {
            Res res = exec(new Request.Builder().url(url).get().build());
            com.google.gson.JsonElement json = Json.parse(res.body);
            String err = res.ok() ? null : (res.code == 0 ? NETWORK_ERROR : Json.errorOf(json, "Đã có lỗi xảy ra, vui lòng thử lại"));
            main(() -> cb.done(res.ok() ? json : null, err));
        });
    }
}
