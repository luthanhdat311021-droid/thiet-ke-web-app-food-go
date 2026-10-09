package com.foodgo.nativeapp.core;

import com.foodgo.nativeapp.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Supabase Realtime "postgres_changes" over the Phoenix websocket protocol (what supabase.channel(...).on(...) does).
 * One shared socket; channels re-join after reconnects and receive the new access token after refreshes.
 */
public final class Realtime {
    private Realtime() {}

    public interface Handler { void onChange(String eventType, JsonObject record, JsonObject oldRecord); }

    public static final class Channel {
        final String topic;
        final JsonObject binding;
        final Handler handler;
        boolean joined;

        Channel(String topic, JsonObject binding, Handler handler) { this.topic = topic; this.binding = binding; this.handler = handler; }
    }

    private static WebSocket socket;
    private static boolean open;
    private static int ref;
    private static String token;
    private static int retry;
    private static final Map<String, Channel> channels = new LinkedHashMap<>();
    private static final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (socket != null && open) send("phoenix", "heartbeat", new JsonObject());
            Net.later(this, 25000);
        }
    };
    private static final Runnable reconnect = Realtime::connect;

    /**
     * supabase.channel(name).on('postgres_changes', { event, schema: 'public', table, filter }, handler).subscribe()
     * event: "INSERT" | "UPDATE" | "*"; filter may be null.
     */
    public static Channel subscribe(String name, String event, String table, String filter, Handler handler) {
        JsonObject b = Json.obj("event", event, "schema", "public", "table", table);
        if (filter != null) b.addProperty("filter", filter);
        String topic = "realtime:" + name;
        Channel old = channels.get(topic);
        if (old != null) remove(old);
        Channel ch = new Channel(topic, b, handler);
        channels.put(topic, ch);
        if (socket == null) connect();
        else if (open) join(ch);
        return ch;
    }

    public static void remove(Channel ch) {
        if (ch == null) return;
        if (channels.get(ch.topic) == ch) channels.remove(ch.topic);
        if (open && ch.joined) send(ch.topic, "phx_leave", new JsonObject());
        ch.joined = false;
    }

    /** Called by Auth whenever the session changes. */
    static void setToken(String t) {
        Net.main(() -> {
            token = t;
            if (!open) return;
            for (Channel ch : channels.values()) if (ch.joined) send(ch.topic, "access_token", Json.obj("access_token", t != null ? t : Config.SUPABASE_KEY));
        });
    }

    private static void connect() {
        if (socket != null) return;
        String url = Config.SUPABASE_URL.replaceFirst("^http", "ws") + "/realtime/v1/websocket?apikey=" + Config.SUPABASE_KEY + "&vsn=1.0.0";
        token = Auth.session() != null ? Auth.session().access_token : null;
        socket = Net.http.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                Net.main(() -> {
                    if (ws != socket) return;
                    open = true;
                    retry = 0;
                    Net.cancel(heartbeat);
                    Net.later(heartbeat, 25000);
                    for (Channel ch : new ArrayList<>(channels.values())) join(ch);
                });
            }

            @Override public void onMessage(WebSocket ws, String text) {
                JsonElement e = Json.parse(text);
                if (!e.isJsonObject()) return;
                Net.main(() -> dispatch(e.getAsJsonObject()));
            }

            @Override public void onClosed(WebSocket ws, int code, String reason) { Net.main(() -> dropped(ws)); }

            @Override public void onFailure(WebSocket ws, Throwable t, Response response) { Net.main(() -> dropped(ws)); }
        });
    }

    private static void dropped(WebSocket ws) {
        if (ws != socket) return;
        socket = null;
        open = false;
        Net.cancel(heartbeat);
        for (Channel ch : channels.values()) ch.joined = false;
        if (channels.isEmpty()) return;
        long delay = Math.min(10000, 1000L * (1 + retry++));
        Net.cancel(reconnect);
        Net.later(reconnect, delay);
    }

    private static void join(Channel ch) {
        JsonArray changes = new JsonArray();
        changes.add(ch.binding);
        JsonObject config = Json.obj(
                "broadcast", Json.obj("ack", false, "self", false),
                "presence", Json.obj("key", ""),
                "postgres_changes", changes,
                "private", false);
        JsonObject payload = Json.obj("config", config);
        payload.addProperty("access_token", token != null ? token : Config.SUPABASE_KEY);
        send(ch.topic, "phx_join", payload);
        ch.joined = true;
    }

    private static void send(String topic, String event, JsonObject payload) {
        if (socket == null) return;
        JsonObject msg = Json.obj("topic", topic, "event", event, "payload", payload, "ref", String.valueOf(++ref));
        socket.send(Json.gson.toJson(msg));
    }

    private static void dispatch(JsonObject msg) {
        if (!"postgres_changes".equals(Json.str(msg, "event"))) return;
        Channel ch = channels.get(Json.str(msg, "topic"));
        if (ch == null) return;
        JsonObject payload = Json.obj(msg, "payload");
        JsonObject data = Json.obj(payload, "data");
        if (data == null) return;
        String type = Json.str(data, "type");
        JsonObject record = Json.obj(data, "record");
        JsonObject old = Json.obj(data, "old_record");
        ch.handler.onChange(type, record != null ? record : new JsonObject(), old != null ? old : new JsonObject());
    }
}
