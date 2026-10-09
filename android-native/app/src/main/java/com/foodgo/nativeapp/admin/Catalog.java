package com.foodgo.nativeapp.admin;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.model.Category;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** components/admin/catalog.tsx — shared by the admin panel (every restaurant) and the owner's panel (their own). */
public final class Catalog {
    private Catalog() {}

    public static TextView plain(Activity a, String s) { return U.text(a, s, 14, U.INK); }

    public static TextView bold(Activity a, String s) { return U.ellipsize(U.text(a, s, 14, U.INK, U.BOLD), 1); }

    public static StoreHours hoursOf(JsonObject r) {
        return StoreHours.of(Json.bool(r, "is_open"), Json.str(r, "open_time"), Json.str(r, "close_time"));
    }

    public static View restaurantStatus(Activity a, JsonObject r) {
        if (!Json.bool(r, "is_active")) return Cards.yesNo(a, false, "", "Đang ẩn");
        return Cards.yesNo(a, hoursOf(r).open, "Đang mở cửa", "Đã đóng cửa");
    }

    /** Form fields for a restaurant; owners don't get the admin-only "hide" switch. */
    public static List<EntityManager.Field> restaurantFields(boolean admin) {
        List<EntityManager.Field> f = new ArrayList<>(Arrays.asList(
                new EntityManager.Field("name", "Tên nhà hàng", "text").req(),
                new EntityManager.Field("cuisine", "Món chính (VD: Cơm • Gà rán • Trà sữa)", "text"),
                new EntityManager.Field("location", "Vị trí nhà hàng", "location").req().address("address"),
                new EntityManager.Field("address", "Địa chỉ hiển thị cho khách", "text").req().wide().hint("Tự điền khi ghim trên bản đồ; có thể sửa lại cho dễ đọc"),
                new EntityManager.Field("phone", "Số điện thoại nhà hàng", "tel"),
                new EntityManager.Field("delivery_time", "Thời gian giao", "text").req().def("20-30 phút"),
                new EntityManager.Field("tag", "Nhãn (Freeship, Giảm 20%...)", "text"),
                new EntityManager.Field("open_time", "Giờ mở cửa", "time").def("07:00").hint("Giờ Việt Nam. Để trống cả hai = mở cả ngày"),
                new EntityManager.Field("close_time", "Giờ đóng cửa", "time").def("21:00").hint("Đóng sau nửa đêm: vd mở 18:00, đóng 02:00"),
                new EntityManager.Field("image", "Ảnh bìa", "image"),
                new EntityManager.Field("logo", "Logo", "image"),
                new EntityManager.Field("is_open", "Đang mở bán (tắt = tạm đóng cửa)", "checkbox")));
        if (admin) f.add(new EntityManager.Field("is_active", "Hiển thị nhà hàng (tắt = ẩn hoàn toàn)", "checkbox"));
        return f;
    }

    /** "Chưa ghim vị trí • address" */
    public static View addressCell(Activity a, JsonObject r) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        if (Json.numOrNull(r, "lat") == null) {
            b.append("Chưa ghim vị trí • ");
            b.setSpan(new android.text.style.ForegroundColorSpan(U.WARN), 0, b.length(), 0);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
        }
        b.append(Json.str(r, "address", ""));
        return U.ellipsize(U.text(a, b, 12, U.INK), 2);
    }

    // ------------------------------------------------------------------ dishes

    public static View foodsManager(Activity a, Long restaurantId) {
        FrameLayout holder = new FrameLayout(a);
        holder.addView(U.spinner(a));
        Db rq = Db.from("fg_restaurants").select("*");
        if (restaurantId != null) rq.eq("id", restaurantId);
        rq.order("name").list(Restaurant.class, (restaurants, e1) -> Db.from("fg_categories").select("*").order("sort").list(Category.class, (categories, e2) -> {
            holder.removeAllViews();
            if (restaurants.isEmpty()) {
                TextView t = U.text(a, "Chưa có nhà hàng nào. Thêm nhà hàng ở tab “Nhà hàng” trước.", 14, U.MUTED);
                t.setGravity(Gravity.CENTER);
                U.pad(t, 32);
                U.card(t, 16);
                holder.addView(t);
                return;
            }
            List<String[]> rOpts = new ArrayList<>();
            for (Restaurant r : restaurants) rOpts.add(new String[]{String.valueOf(r.id), r.name});
            List<String[]> cOpts = new ArrayList<>();
            for (Category c : categories) cOpts.add(new String[]{String.valueOf(c.id), c.name});
            List<EntityManager.Field> fields = new ArrayList<>();
            fields.add(new EntityManager.Field("name", "Tên món", "text").req());
            fields.add(restaurantId != null
                    ? new EntityManager.Field("restaurant_id", "Nhà hàng", "hidden").def(restaurantId)
                    : new EntityManager.Field("restaurant_id", "Nhà hàng", "select").req().options(rOpts));
            fields.add(new EntityManager.Field("category_id", "Danh mục", "select").options(cOpts));
            fields.add(new EntityManager.Field("price", "Giá bán (đ)", "number").req());
            fields.add(new EntityManager.Field("old_price", "Giá gốc (đ, để trống nếu không giảm)", "number"));
            fields.add(new EntityManager.Field("description", "Mô tả", "textarea"));
            fields.add(new EntityManager.Field("image", "Ảnh", "image"));
            fields.add(new EntityManager.Field("is_available", "Đang bán", "checkbox"));
            fields.add(new EntityManager.Field("is_popular", "Món nổi bật", "checkbox"));
            List<EntityManager.Column> cols = new ArrayList<>();
            cols.add(new EntityManager.Column("Ảnh", r -> Cards.thumb(a, Json.str(r, "image"))));
            cols.add(new EntityManager.Column("Tên món", r -> bold(a, Json.str(r, "name", ""))));
            if (restaurantId == null) cols.add(new EntityManager.Column("Nhà hàng", r -> plain(a, nameOf(r, "restaurant"))));
            cols.add(new EntityManager.Column("Danh mục", r -> plain(a, nameOf(r, "categories"))));
            cols.add(new EntityManager.Column("Giá", r -> plain(a, Fmt.money((long) Json.num(r, "price", 0)))));
            cols.add(new EntityManager.Column("Đã bán", r -> plain(a, Json.str(r, "sold_count", "0"))));
            cols.add(new EntityManager.Column("Đánh giá", r -> plain(a, Json.num(r, "review_count", 0) > 0
                    ? Fmt.fixed1(Json.num(r, "rating", 0)) + " ★ (" + Json.str(r, "review_count") + ")" : "Chưa có")));
            cols.add(new EntityManager.Column("Trạng thái", r -> Cards.yesNo(a, Json.bool(r, "is_available"), "Đang bán", "Tạm hết")));
            EntityManager m = new EntityManager(a).table("fg_foods").title("Món ăn")
                    .select("*, categories:fg_categories(name), restaurant:fg_restaurants(name)")
                    .orderBy(restaurantId != null ? "category_id" : "restaurant_id")
                    .fields(fields).columns(cols);
            if (restaurantId != null) m.match("restaurant_id", restaurantId);
            else m.filter("restaurant_id", "Nhà hàng", rOpts);
            holder.addView(m.start());
        }));
        return holder;
    }

    static String nameOf(JsonObject r, String key) {
        JsonObject o = Json.obj(r, key);
        String n = o != null ? Json.str(o, "name") : null;
        return n != null ? n : "—";
    }

    // ------------------------------------------------------------------ vouchers

    private static String describeVoucher(JsonObject r) {
        double value = Json.num(r, "discount_value", 0);
        String type = Json.str(r, "discount_type", "");
        double max = Json.num(r, "max_discount", 0);
        String main = type.equals("percent") ? "Giảm " + Fmt.js(value) + "%" + (max > 0 ? " (tối đa " + Fmt.money((long) max) + ")" : "")
                : type.equals("freeship") ? "Freeship" : "Giảm " + Fmt.money((long) value);
        double min = Json.num(r, "min_subtotal", 0);
        return min > 0 ? main + " • đơn từ " + Fmt.money((long) min) : main;
    }

    private static String fmtDate(String d) {
        if (d == null || d.isEmpty()) return null;
        return Fmt.date(Iso.parse(d.substring(0, Math.min(10, d.length()))));
    }

    /** Admin: every code (platform-wide ones apply to all restaurants). Owner: codes for their restaurant only. */
    public static View vouchersManager(Activity a, Long restaurantId) {
        List<String[]> types = Arrays.asList(new String[]{"amount", "Giảm số tiền (đ)"}, new String[]{"percent", "Giảm theo %"}, new String[]{"freeship", "Miễn phí giao hàng"});
        List<EntityManager.Field> fields = Arrays.asList(
                new EntityManager.Field("code", "Mã (VD: GIAM20K)", "text").req().hint("Tự chuyển thành chữ HOA, bỏ khoảng trắng"),
                // admin-created codes stay platform-wide (null); an owner's codes belong to their restaurant
                new EntityManager.Field("restaurant_id", "Nhà hàng", "hidden").def(restaurantId),
                new EntityManager.Field("discount_type", "Loại giảm giá", "select").req().options(types),
                new EntityManager.Field("discount_value", "Mức giảm (số tiền hoặc %)", "number").req().def(0).hint("Freeship: nhập 0"),
                new EntityManager.Field("max_discount", "Giảm tối đa (đ, cho mã %)", "number"),
                new EntityManager.Field("min_subtotal", "Đơn tối thiểu (đ, tiền món)", "number").req().def(0),
                new EntityManager.Field("usage_limit", "Tổng lượt dùng (trống = không giới hạn)", "number"),
                new EntityManager.Field("per_user_limit", "Lượt dùng mỗi khách", "number").def(1).hint("Trống = không giới hạn"),
                new EntityManager.Field("starts_on", "Áp dụng từ ngày", "date"),
                new EntityManager.Field("expires_on", "Hết hạn sau ngày", "date"),
                new EntityManager.Field("description", "Mô tả cho khách (VD: Giảm 20k cho đơn từ 80k)", "text").wide(),
                new EntityManager.Field("is_active", "Đang bật", "checkbox"));
        List<EntityManager.Column> cols = new ArrayList<>();
        cols.add(new EntityManager.Column("Biểu tượng", r -> {
            FrameLayout f = new FrameLayout(a);
            U.bg(f, U.PEACH, 8);
            f.addView(U.icon(a, R.drawable.ic_ticket, 20, U.ORANGE), new FrameLayout.LayoutParams(U.dp(a, 20), U.dp(a, 20), Gravity.CENTER));
            f.setLayoutParams(new LinearLayout.LayoutParams(U.dp(a, 48), U.dp(a, 48)));
            return f;
        }));
        cols.add(new EntityManager.Column("Mã", r -> {
            TextView t = bold(a, Json.str(r, "code", ""));
            t.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            return t;
        }));
        if (restaurantId == null) cols.add(new EntityManager.Column("Áp dụng", r -> {
            String n = nameOf(r, "restaurant");
            return plain(a, n.equals("—") ? "Mọi nhà hàng" : n);
        }));
        cols.add(new EntityManager.Column("Ưu đãi", r -> plain(a, describeVoucher(r))));
        cols.add(new EntityManager.Column("Đã dùng", r -> plain(a, (long) Json.num(r, "fg_used_count", 0) + (Json.num(r, "usage_limit", 0) > 0 ? " / " + Json.str(r, "usage_limit") : ""))));
        cols.add(new EntityManager.Column("Hạn dùng", r -> {
            String s = fmtDate(Json.str(r, "starts_on")), e = fmtDate(Json.str(r, "expires_on"));
            return plain(a, s != null || e != null ? (s != null ? s : "…") + " → " + (e != null ? e : "…") : "Không thời hạn");
        }));
        cols.add(new EntityManager.Column("Trạng thái", r -> {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            String today = f.format(new Date());
            String exp = Json.str(r, "expires_on");
            boolean active = Json.bool(r, "is_active");
            return Cards.yesNo(a, active && !(exp != null && exp.compareTo(today) < 0), "Đang bật", active ? "Hết hạn" : "Đã tắt");
        }));
        EntityManager m = new EntityManager(a).table("fg_vouchers").title("Mã giảm giá").select("*, fg_used_count, restaurant:fg_restaurants(name)")
                .orderBy("created_at").searchKey("code").fields(fields).columns(cols);
        if (restaurantId != null) m.match("restaurant_id", restaurantId);
        return m.start();
    }
}
