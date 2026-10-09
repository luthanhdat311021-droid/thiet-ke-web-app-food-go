package com.foodgo.nativeapp.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.foodgo.nativeapp.Config;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Request;
import okhttp3.RequestBody;

/** Supabase Auth (GoTrue) over HTTP, with the session persisted like supabase-js does in localStorage. */
public final class Auth {
    private Auth() {}

    public static final class Session {
        public String access_token;
        public String refresh_token;
        public long expires_at; // seconds
        public JsonObject user;

        public String userId() { return Json.str(user, "id"); }
        public String email() { return Json.str(user, "email"); }
        public JsonObject meta() { JsonObject m = Json.obj(user, "user_metadata"); return m != null ? m : new JsonObject(); }
    }

    public interface Listener { void onAuthChange(Session session); }

    private static final String PREFS = "foodgo-auth";
    private static final String KEY = "session";
    private static SharedPreferences prefs;
    private static volatile Session session;
    private static final List<Listener> listeners = new ArrayList<>();
    private static final Object refreshLock = new Object();

    public static void init(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        session = Json.as(Json.parse(prefs.getString(KEY, null)), Session.class);
        if (session != null && (session.access_token == null || session.refresh_token == null)) session = null;
    }

    public static Session session() { return session; }

    public static void addListener(Listener l) { listeners.add(l); }

    public static void removeListener(Listener l) { listeners.remove(l); }

    private static void set(Session s) {
        session = s;
        if (prefs != null) {
            if (s == null) prefs.edit().remove(KEY).apply();
            else prefs.edit().putString(KEY, Json.gson.toJson(s)).apply();
        }
        Realtime.setToken(s != null ? s.access_token : null);
        Net.main(() -> { for (Listener l : new ArrayList<>(listeners)) l.onAuthChange(s); });
    }

    private static Request.Builder base(String path) {
        return new Request.Builder().url(Config.SUPABASE_URL + "/auth/v1" + path)
                .header("apikey", Config.SUPABASE_KEY)
                .header("X-Client-Info", "foodgo-native-android");
    }

    private static Session fromTokenResponse(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        if (!o.has("access_token")) return null;
        Session s = new Session();
        s.access_token = Json.str(o, "access_token");
        s.refresh_token = Json.str(o, "refresh_token");
        long now = System.currentTimeMillis() / 1000;
        s.expires_at = (long) Json.num(o, "expires_at", now + Json.num(o, "expires_in", 3600));
        s.user = Json.obj(o, "user");
        return s;
    }

    /**
     * Current access token, refreshed first when it is about to expire (blocking: call off the main thread).
     * null when signed out.
     */
    public static String accessToken() {
        Session s = session;
        if (s == null) return null;
        if (s.expires_at - System.currentTimeMillis() / 1000 > 60) return s.access_token;
        synchronized (refreshLock) {
            s = session;
            if (s == null) return null;
            if (s.expires_at - System.currentTimeMillis() / 1000 > 60) return s.access_token;
            refreshBlocking(s);
            return session != null ? session.access_token : null;
        }
    }

    /** Refresh now; drops the session only when the server rejects the refresh token. */
    private static void refreshBlocking(Session s) {
        Net.Res res = Net.exec(base("/token?grant_type=refresh_token")
                .post(RequestBody.create(Json.gson.toJson(Json.obj("refresh_token", s.refresh_token)), Net.JSON)).build());
        if (res.code == 0) return; // offline: keep the session, try again later
        Session next = res.ok() ? fromTokenResponse(Json.parse(res.body)) : null;
        if (next != null) {
            if (next.user == null) next.user = s.user;
            set(next);
        } else if (res.code >= 400 && res.code < 500) {
            set(null);
        }
    }

    /** Startup: like supabase-js getSession(), make sure a stored session is still usable. */
    public static void restore(Runnable done) {
        Net.bg(() -> {
            accessToken();
            Net.main(done);
        });
    }

    private static void call(Request req, Cb<JsonElement> cb) {
        Net.bg(() -> {
            Net.Res res = Net.exec(req);
            JsonElement json = Json.parse(res.body);
            String err = res.ok() ? null : res.code == 0 ? Net.NETWORK_ERROR : Json.errorOf(json, "Đã có lỗi xảy ra, vui lòng thử lại");
            Net.main(() -> cb.done(res.ok() ? json : null, err));
        });
    }

    private static JsonObject withCaptcha(JsonObject body, String captchaToken) {
        if (captchaToken != null) body.add("gotrue_meta_security", Json.obj("captcha_token", captchaToken));
        return body;
    }

    private static RequestBody body(JsonObject o) { return RequestBody.create(Json.gson.toJson(o), Net.JSON); }

    public static void signInWithPassword(String email, String password, String captcha, Cb<Session> cb) {
        call(base("/token?grant_type=password").post(body(withCaptcha(Json.obj("email", email, "password", password), captcha))).build(), (data, err) -> {
            Session s = fromTokenResponse(data);
            if (err == null && s == null) err = "Đã có lỗi xảy ra, vui lòng thử lại";
            if (s != null) set(s);
            cb.done(s, err);
        });
    }

    /** data = true when the account is signed in right away (email confirmation off). */
    public static void signUp(String email, String password, String fullName, String redirectTo, String captcha, Cb<Boolean> cb) {
        JsonObject b = withCaptcha(Json.obj("email", email, "password", password, "data", Json.obj("full_name", fullName)), captcha);
        String q = redirectTo != null ? "?redirect_to=" + android.net.Uri.encode(redirectTo) : "";
        call(base("/signup" + q).post(body(b)).build(), (data, err) -> {
            if (err != null) { cb.done(null, err); return; }
            Session s = fromTokenResponse(data);
            if (s != null) set(s);
            cb.done(s != null, null);
        });
    }

    public static void resetPasswordForEmail(String email, String captcha, Cb<Void> cb) {
        call(base("/recover").post(body(withCaptcha(Json.obj("email", email), captcha))).build(), (d, e) -> cb.done(null, e));
    }

    /** A correct recovery code signs the user in (recovery session), which allows setting a new password. */
    public static void verifyRecovery(String email, String token, String captcha, Cb<Session> cb) {
        call(base("/verify").post(body(withCaptcha(Json.obj("email", email, "token", token, "type", "recovery"), captcha))).build(), (data, err) -> {
            Session s = fromTokenResponse(data);
            if (err == null && s == null) err = "Mã xác nhận không đúng hoặc đã hết hạn";
            if (s != null) set(s);
            cb.done(s, err);
        });
    }

    public static void updatePassword(String password, Cb<Void> cb) {
        Net.bg(() -> {
            String token = accessToken();
            if (token == null) { Net.main(() -> cb.done(null, "Bạn cần đăng nhập lại")); return; }
            Net.Res res = Net.exec(base("/user").header("Authorization", "Bearer " + token)
                    .put(body(Json.obj("password", password))).build());
            JsonElement json = Json.parse(res.body);
            if (res.ok() && json.isJsonObject() && session != null) {
                Session s = session;
                s.user = json.getAsJsonObject();
                set(s);
            }
            String err = res.ok() ? null : res.code == 0 ? Net.NETWORK_ERROR : Json.errorOf(json, "Đã có lỗi xảy ra, vui lòng thử lại");
            Net.main(() -> cb.done(null, err));
        });
    }

    /** Tokens from the Google sign-in deep link (like supabase.auth.setSession). */
    public static void setSession(String accessToken, String refreshToken, long expiresAt, Cb<Session> cb) {
        Net.bg(() -> {
            Net.Res res = Net.exec(base("/user").header("Authorization", "Bearer " + accessToken).get().build());
            JsonElement json = Json.parse(res.body);
            if (!res.ok() || !json.isJsonObject()) {
                String err = res.code == 0 ? Net.NETWORK_ERROR : Json.errorOf(json, "Đăng nhập Google không thành công");
                Net.main(() -> cb.done(null, err));
                return;
            }
            Session s = new Session();
            s.access_token = accessToken;
            s.refresh_token = refreshToken;
            s.expires_at = expiresAt > 0 ? expiresAt : System.currentTimeMillis() / 1000 + 3600;
            s.user = json.getAsJsonObject();
            set(s);
            Net.main(() -> cb.done(s, null));
        });
    }

    /** Fresh user object from the server (supabase.auth.getUser()). */
    public static void getUser(Cb<JsonObject> cb) {
        Net.bg(() -> {
            String token = accessToken();
            if (token == null) { Net.main(() -> cb.done(null, null)); return; }
            Net.Res res = Net.exec(base("/user").header("Authorization", "Bearer " + token).get().build());
            JsonElement json = Json.parse(res.body);
            Net.main(() -> cb.done(res.ok() && json.isJsonObject() ? json.getAsJsonObject() : null, null));
        });
    }

    public static void signOut(Runnable done) {
        Session s = session;
        Net.bg(() -> {
            if (s != null) {
                Net.exec(base("/logout?scope=global").header("Authorization", "Bearer " + s.access_token)
                        .post(RequestBody.create("{}", Net.JSON)).build());
            }
            set(null);
            Net.main(done);
        });
    }

    /** URL that starts Google sign-in in the system browser (implicit flow: tokens come back in the #fragment). */
    public static String googleUrl(String redirectTo) {
        return Config.SUPABASE_URL + "/auth/v1/authorize?provider=google&redirect_to=" + android.net.Uri.encode(redirectTo)
                + "&prompt=select_account";
    }
}
