package com.foodgo.nativeapp.screens;

import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.model.Category;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.AiSearch;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Recommend;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.SmartSearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * app/search/page.tsx — filters live in the query string (?q=&category=&sort=), like the web.
 * With a search term the app goes further than the web: an on-device search that ignores accents and typos
 * answers instantly, then the AI search (Claude, via the web app's /api/ai/search) re-ranks the menu by what
 * the customer meant and explains each pick.
 */
public class SearchScreen extends Screen implements AppState.Listener {
    private TextView count;
    private LinearLayout restaurantsBox, chips, list, aiBox;
    private Select sort;
    private List<Category> categories = new ArrayList<>();
    private List<Restaurant> restaurants;
    private int generation;
    private boolean tracked;

    // with a search term: the whole menu once, then ranked locally / by the AI
    private List<Food> menu;
    private AiSearch.Result ai;
    private String aiState = ""; // "" | loading | done | off

    private String q() { String v = param("q"); return v != null ? v : ""; }

    private boolean hasQuery() { return !q().trim().isEmpty(); }

    private String categoryId() { return param("category"); }

    private String sortValue() { String v = param("sort"); return v != null ? v : hasQuery() ? "relevance" : "popular"; }

    /** router.replace(`${pathname}?${next}`) with one parameter changed */
    private void update(String key, String value) {
        Uri.Builder b = new Uri.Builder();
        for (String k : new String[]{"q", "category", "sort"}) {
            String v = k.equals(key) ? value : param(k);
            if (v != null && !v.isEmpty()) b.appendQueryParameter(k, v);
        }
        String query = b.build().getEncodedQuery();
        path = "/search" + (query != null ? "?" + query : "");
        uri = Uri.parse("https://foodgo.local" + path);
        if (key.equals("q")) { menu = null; ai = null; aiState = ""; }
        load();
    }

    @Override
    protected View build() {
        LinearLayout page = U.col(act);
        page.addView(U.text(act, "TÌM MÓN", 14, U.ORANGE, U.SEMI));
        String qv = q();
        U.add(page, U.h1(act, qv.isEmpty() ? "Tất cả món ăn" : "Kết quả cho “" + qv + "”"), 8);
        count = U.text(act, "Đang tìm...", 14, U.MUTED);
        U.add(page, count, 8);
        sort = new Select(act).title("Sắp xếp");
        if (hasQuery()) sort.option("relevance", "Phù hợp nhất");
        sort.option("popular", "Phổ biến nhất").option("rating", "Đánh giá cao")
                .option("price_asc", "Giá thấp → cao").option("price_desc", "Giá cao → thấp");
        sort.set(sortValue());
        sort.onChange(v -> update("sort", v));
        U.add(page, sort, 16, U.WRAP, U.dp(act, 44));

        aiBox = U.col(act);
        page.addView(aiBox);
        restaurantsBox = U.col(act);
        page.addView(restaurantsBox);
        chips = U.row(act);
        U.pad(chips, 0, 0, 0, 8);
        U.add(page, U.hscroll(act, chips), 24);
        list = U.col(act);
        U.add(page, list, 28);

        Db.from("fg_categories").select("*").order("sort").list(Category.class, (l, e) -> { categories = l; renderChips(); renderResults(); });
        Store.restaurants((l, e) -> { restaurants = l; renderRestaurants(); });
        state().addListener(this);
        onAppState();
        renderChips();
        load();
        return scrollPage(page);
    }

    @Override
    public void onDestroy() { state().removeListener(this); }

    /** track('search', q) once auth is known */
    @Override
    public void onAppState() {
        if (tracked || state().authLoading) return;
        if (hasQuery()) {
            tracked = true;
            Recommend.track("search", q(), state().signedIn());
        }
    }

    private void renderChips() {
        if (chips == null) return;
        chips.removeAllViews();
        String cat = categoryId();
        chips.addView(chip("Tất cả", cat == null, () -> update("category", null)));
        for (Category c : categories) {
            U.add(chips, chip(c.name, String.valueOf(c.id).equals(cat), () -> update("category", String.valueOf(c.id))), 8, U.WRAP, U.WRAP);
        }
    }

    private View chip(String label, boolean active, Runnable onClick) {
        TextView t = U.text(act, label, 14, active ? U.WHITE : U.MUTED, U.SEMI);
        U.pad(t, 20, 10);
        if (active) U.pressable(t, U.ORANGE, 999);
        else { U.pressable(t, U.WHITE, 999); t.setElevation(U.dp(act, 1)); }
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private void renderRestaurants() {
        if (restaurantsBox == null) return;
        restaurantsBox.removeAllViews();
        if (!hasQuery() || categoryId() != null || restaurants == null) return;
        SmartSearch.Query sq = SmartSearch.parse(q());
        List<Restaurant> matched = new ArrayList<>();
        for (Restaurant r : restaurants) {
            if (SmartSearch.matchesText(r.name + " " + (r.cuisine != null ? r.cuisine : ""), sq)) matched.add(r);
            if (matched.size() == 4) break;
        }
        if (matched.isEmpty()) return;
        U.add(restaurantsBox, U.text(act, "Nhà hàng", 16, U.INK, U.XBOLD), 24);
        for (int i = 0; i < matched.size(); i++) U.add(restaurantsBox, Cards.restaurantCard(act, matched.get(i)), i == 0 ? 12 : 20);
    }

    private void skeletons() {
        count.setText("Đang tìm...");
        list.removeAllViews();
        for (int i = 0; i < 4; i++) U.add(list, U.skeleton(act, 288), i == 0 ? 0 : 20);
    }

    private void load() {
        renderChips();
        renderRestaurants();
        int gen = ++generation;
        if (hasQuery()) { loadSmart(gen); return; }
        renderAi();
        skeletons();
        // no search term: same server query as the web page
        Db fq = Db.from("fg_foods").select(Store.FOOD_SELECT);
        String cat = categoryId();
        if (cat != null) {
            try { fq.eq("category_id", Long.parseLong(cat)); } catch (NumberFormatException e) { fq.eq("category_id", 0); }
        }
        switch (sortValue()) {
            case "rating": fq.order("rating", false); break;
            case "price_asc": fq.order("price"); break;
            case "price_desc": fq.order("price", false); break;
            default: fq.order("sold_count", false);
        }
        fq.limit(100).list(Food.class, (foods, err) -> {
            if (gen != generation) return;
            show(foods, null);
        });
    }

    private void loadSmart(int gen) {
        if (menu == null) {
            skeletons();
            Db.from("fg_foods").select(Store.FOOD_SELECT).order("sold_count", false).limit(1000).list(Food.class, (foods, err) -> {
                if (gen != generation) return;
                menu = foods;
                renderResults();
            });
        } else renderResults();
        if (aiState.isEmpty()) {
            aiState = "loading";
            String asked = q();
            AiSearch.search(asked, (res, err) -> {
                if (!asked.equals(q())) return;
                ai = res;
                aiState = res != null ? "done" : "off";
                renderResults();
            });
        }
        renderAi();
    }

    /** "✨ AI hiểu: …" banner while / after the AI looks at the request */
    private void renderAi() {
        aiBox.removeAllViews();
        if (!hasQuery() || aiState.isEmpty() || aiState.equals("off")) return;
        LinearLayout box = U.row(act);
        U.pad(box, 14, 12);
        U.bg(box, U.PEACH2, 12);
        if (aiState.equals("loading")) {
            box.addView(U.progress(act, 16, U.ORANGE));
            U.addFlex(box, U.text(act, "AI đang tìm những món sát nhất với yêu cầu của bạn...", 13, U.ORANGE, U.SEMI), 10);
        } else {
            box.addView(U.icon(act, R.drawable.ic_sparkles, 18, U.ORANGE));
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("AI hiểu: ");
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
            b.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), 0, b.length(), 0);
            b.append(ai.interpretation != null && !ai.interpretation.isEmpty() ? ai.interpretation : q());
            U.addFlex(box, U.text(act, b, 13, U.INK), 10);
        }
        U.add(aiBox, box, 16);
    }

    /** Local relevance + AI ranking (AI picks first, then the rest of the local matches), filtered and sorted. */
    private void renderResults() {
        if (!hasQuery() || menu == null) return;
        renderAi();
        SmartSearch.Query sq = SmartSearch.parse(q());
        Map<Long, String> catNames = new HashMap<>();
        for (Category c : categories) catNames.put(c.id, c.name);
        String cat = categoryId();
        Long catId = null;
        if (cat != null) try { catId = Long.parseLong(cat); } catch (NumberFormatException e) { catId = -1L; }

        Map<Long, Food> byId = new HashMap<>();
        for (Food f : menu) byId.put(f.id, f);
        LinkedHashMap<Long, String> ordered = new LinkedHashMap<>(); // food id → AI reason (or null)
        if (ai != null) for (Map.Entry<Long, String> e : ai.ranked.entrySet()) if (byId.containsKey(e.getKey())) ordered.put(e.getKey(), e.getValue());
        List<Object[]> local = new ArrayList<>();
        for (Food f : menu) {
            double s = SmartSearch.score(f, sq, catNames);
            if (s > 0) local.add(new Object[]{f, s});
        }
        local.sort((x, y) -> Double.compare((Double) y[1], (Double) x[1]));
        for (Object[] x : local) {
            Food f = (Food) x[0];
            if (!ordered.containsKey(f.id)) ordered.put(f.id, null);
        }
        List<Food> foods = new ArrayList<>();
        Map<Long, String> reasons = new HashMap<>();
        for (Map.Entry<Long, String> e : ordered.entrySet()) {
            Food f = byId.get(e.getKey());
            if (catId != null && (f.category_id == null || !f.category_id.equals(catId))) continue;
            foods.add(f);
            if (e.getValue() != null && !e.getValue().isEmpty()) reasons.put(f.id, e.getValue());
        }
        switch (sortValue()) {
            case "popular": foods.sort((a, b) -> Integer.compare(b.sold_count, a.sold_count)); break;
            case "rating": foods.sort((a, b) -> Double.compare(b.rating, a.rating)); break;
            case "price_asc": foods.sort((a, b) -> Long.compare(a.price, b.price)); break;
            case "price_desc": foods.sort((a, b) -> Long.compare(b.price, a.price)); break;
            default: break; // relevance: keep the merged order
        }
        if (foods.size() > 100) foods = foods.subList(0, 100);
        show(foods, reasons);
    }

    private void show(List<Food> foods, Map<Long, String> reasons) {
        count.setText("Tìm thấy " + foods.size() + " món ăn phù hợp");
        list.removeAllViews();
        for (int i = 0; i < foods.size(); i++) {
            Food f = foods.get(i);
            String reason = reasons != null ? reasons.get(f.id) : null;
            U.add(list, new FoodCardView(act, f, reason), i == 0 ? 0 : 20);
        }
        if (foods.isEmpty() && "loading".equals(aiState)) {
            count.setText("Đang tìm...");
            for (int i = 0; i < 2; i++) U.add(list, U.skeleton(act, 288), i == 0 ? 0 : 20);
        } else if (foods.isEmpty()) {
            list.addView(U.empty(act, R.drawable.ic_search, "Không tìm thấy kết quả", U.text(act, "Thử từ khóa khác hoặc bỏ bớt bộ lọc.", 14, U.MUTED)));
        }
    }
}
