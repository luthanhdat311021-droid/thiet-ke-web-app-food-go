package com.foodgo.nativeapp.admin;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.ui.FlowRow;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only trail written by DB triggers (fg_audit): who changed what, and when. */
public class AuditLog extends LinearLayout {
    private static final Map<String, String> TABLES = new LinkedHashMap<>();
    static {
        TABLES.put("fg_profiles", "Tài khoản");
        TABLES.put("fg_restaurants", "Nhà hàng");
        TABLES.put("fg_vouchers", "Mã giảm giá");
        TABLES.put("fg_subscription_payments", "Phí duy trì");
        TABLES.put("fg_orders", "Đơn hàng");
        TABLES.put("fg_foods", "Món ăn");
    }

    private final Activity a;
    private final LinearLayout list;
    private String table = "";
    private int gen;

    public AuditLog(Activity a) {
        super(a);
        this.a = a;
        setOrientation(VERTICAL);
        addView(U.text(a, "Nhật ký bảo mật", 22, U.INK, U.XBOLD));
        U.add(this, U.text(a, "Ghi tự động trong database, không ai sửa hay xóa được từ ứng dụng.", 14, U.MUTED), 4);
        Select s = new Select(a).title("Lọc theo loại").option("", "Tất cả");
        for (Map.Entry<String, String> e : TABLES.entrySet()) s.option(e.getKey(), e.getValue());
        s.onChange(v -> { table = v; load(); });
        U.add(this, s, 12, U.WRAP, U.dp(a, 40));
        list = U.col(a);
        U.add(this, list, 20);
        load();
    }

    private static String show(JsonElement v) {
        if (v == null || v.isJsonNull()) return "∅";
        return v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }

    private void load() {
        int g = ++gen;
        list.removeAllViews();
        list.addView(U.spinner(a));
        Db q = Db.from("fg_audit_log").select("*").order("at", false).limit(300);
        if (!table.isEmpty()) q.eq("table_name", table);
        q.rows((entries, err) -> {
            if (g != gen) return;
            Set<String> ids = new LinkedHashSet<>();
            for (JsonObject e : entries) if (Json.str(e, "actor") != null) ids.add(Json.str(e, "actor"));
            Map<String, String> names = new HashMap<>();
            if (ids.isEmpty()) { render(entries, names); return; }
            Db.from("fg_profiles").select("id, full_name").in("id", new ArrayList<>(ids)).rows((people, e2) -> {
                for (JsonObject p : people) names.put(Json.str(p, "id"), Json.str(p, "full_name", "Người dùng"));
                if (g == gen) render(entries, names);
            });
            render(entries, names);
        });
    }

    private void render(List<JsonObject> entries, Map<String, String> names) {
        list.removeAllViews();
        if (entries.isEmpty()) {
            TextView t = U.text(a, "Chưa có hoạt động nào", 14, U.SUBTLE);
            t.setGravity(Gravity.CENTER);
            U.pad(t, 0, 48);
            U.card(t, 16);
            list.addView(t);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            JsonObject e = entries.get(i);
            String action = Json.str(e, "action", "");
            LinearLayout c = U.card(U.pad(U.col(a), 16), 16);
            FlowRow head = new FlowRow(a, 8, 4);
            String label = action.equals("insert") ? "Thêm" : action.equals("update") ? "Sửa" : action.equals("delete") ? "Xóa" : action;
            int[] style = action.equals("insert") ? new int[]{U.GREEN_BG, U.GREEN_DARK} : action.equals("update") ? new int[]{U.AMBER_BG, U.AMBER_DARK} : action.equals("delete") ? new int[]{U.RED_BG, U.RED_TEXT} : new int[]{0, U.INK};
            TextView pill = U.text(a, label, 12, style[1], U.BOLD);
            U.pad(pill, 8, 2);
            U.bg(pill, style[0], 999);
            head.addView(pill);
            String tn = Json.str(e, "table_name", "");
            String row = Json.str(e, "row_id");
            head.addView(U.text(a, (TABLES.containsKey(tn) ? TABLES.get(tn) : tn) + (row != null ? " #" + row : ""), 14, U.INK, U.BOLD));
            String actor = Json.str(e, "actor");
            head.addView(U.text(a, "bởi " + (actor != null ? (names.containsKey(actor) ? names.get(actor) : "người dùng") : "hệ thống"), 14, U.MUTED));
            head.addView(U.text(a, Fmt.dateTime(Json.str(e, "at")), 12, U.SUBTLE));
            c.addView(head);
            JsonObject changes = Json.obj(e, "changes");
            if (changes != null) {
                LinearLayout dl = U.col(a);
                int n = 0;
                for (Map.Entry<String, JsonElement> ch : changes.entrySet()) {
                    if (n++ >= 8) break;
                    LinearLayout r = U.row(a);
                    r.setGravity(Gravity.TOP);
                    TextView k = U.text(a, ch.getKey(), 12, U.SUBTLE);
                    k.setTypeface(android.graphics.Typeface.MONOSPACE);
                    r.addView(k);
                    JsonElement v = ch.getValue();
                    boolean diff = action.equals("update") && v != null && v.isJsonObject() && v.getAsJsonObject().has("from");
                    android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
                    if (diff) {
                        b.append(show(v.getAsJsonObject().get("from")));
                        b.setSpan(new android.text.style.StrikethroughSpan(), 0, b.length(), 0);
                        b.setSpan(new android.text.style.ForegroundColorSpan(U.SUBTLE), 0, b.length(), 0);
                        b.append(" → ");
                        int s = b.length();
                        b.append(show(v.getAsJsonObject().get("to")));
                        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
                    } else b.append(show(v));
                    U.addFlex(r, U.ellipsize(U.text(a, b, 12, U.INK), 1), 12);
                    dl.addView(r);
                }
                U.add(c, dl, 8);
            }
            U.add(list, c, i == 0 ? 0 : 8);
        }
    }
}
