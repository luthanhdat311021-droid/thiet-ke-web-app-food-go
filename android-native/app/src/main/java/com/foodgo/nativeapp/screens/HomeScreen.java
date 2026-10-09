package com.foodgo.nativeapp.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.model.Category;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Recommendation;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.state.Recommend;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.StoreHours;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** app/page.tsx */
public class HomeScreen extends Screen implements AppState.Listener, LocationState.Listener {
    private static final String HERO_IMAGE = "https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&q=85";

    private LinearLayout categoriesRow, picksSection, picksBody, nearbyBody, popularBody;
    private TextView nearbyTitleHolder;
    private LinearLayout nearbyHeader;
    private List<Restaurant> restaurants;
    private String picksFor = "-";
    private boolean picksLoaded;

    // list ↔ grid view of the home sections, remembered on this device
    private static final String VIEW_KEY = "home-view-grid";
    private boolean grid;
    private LinearLayout viewToggle;
    private List<Food> popularList;
    private List<Recommendation> picksList;
    private boolean picksSignedIn;

    private android.content.SharedPreferences prefs() { return act.getSharedPreferences("foodgo", android.content.Context.MODE_PRIVATE); }

    /** "Hiển thị  [☰ Danh sách | ▦ Lưới]" */
    private View buildViewToggle() {
        LinearLayout row = U.row(act);
        U.addFlex(row, U.text(act, "Hiển thị", 14, U.MUTED, U.SEMI), 0);
        viewToggle = U.row(act);
        U.pad(viewToggle, 4);
        U.card(viewToggle, 12);
        row.addView(viewToggle);
        renderViewToggle();
        return row;
    }

    private void renderViewToggle() {
        viewToggle.removeAllViews();
        viewToggle.addView(toggleButton(R.drawable.ic_list, "Danh sách", !grid, false));
        viewToggle.addView(toggleButton(R.drawable.ic_grid, "Lưới", grid, true));
    }

    private View toggleButton(int icon, String label, boolean active, boolean toGrid) {
        TextView t = U.iconText(act, icon, 16, label, 13, active ? U.WHITE : U.MUTED, U.BOLD);
        U.pad(t, 12, 8);
        U.pressable(t, active ? U.ORANGE : U.WHITE, 8);
        t.setContentDescription("Xem dạng " + label.toLowerCase());
        t.setOnClickListener(v -> setGrid(toGrid));
        return t;
    }

    private void setGrid(boolean g) {
        if (g == grid) return;
        grid = g;
        prefs().edit().putBoolean(VIEW_KEY, g).apply();
        renderViewToggle();
        if (picksList != null) renderPicks(picksList, picksSignedIn);
        renderNearby();
        renderPopular();
    }

    /** Dishes as full cards (list) or 2-column tiles (grid). */
    private View foodsView(List<Food> foods, List<String> reasons) {
        if (grid) {
            List<View> cells = new ArrayList<>();
            for (int i = 0; i < foods.size(); i++) cells.add(new GridCards.FoodTile(act, foods.get(i), reasons != null ? reasons.get(i) : null));
            return U.grid(act, 2, cells, 12);
        }
        LinearLayout col = U.col(act);
        for (int i = 0; i < foods.size(); i++) U.add(col, new FoodCardView(act, foods.get(i), reasons != null ? reasons.get(i) : null), i == 0 ? 0 : 20);
        return col;
    }

    private void renderPopular() {
        if (popularBody == null || popularList == null) return;
        popularBody.removeAllViews();
        popularBody.addView(foodsView(popularList, null));
    }

    @Override
    protected View build() {
        grid = prefs().getBoolean(VIEW_KEY, false);
        LinearLayout page = U.col(act);

        // hero
        LinearLayout hero = U.col(act);
        U.bg(hero, U.HERO, 24);
        U.pad(hero, 28, 40);
        TextView chip = U.text(act, "GIAO HÀNG NHANH • TẬN TÂM", 12, U.ORANGE, U.BOLD);
        U.pad(chip, 16, 8);
        U.bg(chip, U.WHITE, 999);
        hero.addView(chip, U.lp(U.WRAP, U.WRAP));
        android.text.SpannableStringBuilder h = new android.text.SpannableStringBuilder("Món ngon\ngiao tận cửa");
        h.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), 9, h.length(), 0);
        TextView title = U.text(act, h, 36, U.INK, U.XBOLD);
        title.setLineSpacing(0, 1.05f);
        U.add(hero, title, 20);
        U.add(hero, U.text(act, "Cơm, gà rán, pizza, trà sữa… từ các nhà hàng quanh bạn, giao nhanh tận cửa.", 16, U.MUTED), 16);
        PButton cta = new PButton(act, "Xem nhà hàng", PButton.PRIMARY);
        cta.label().setTextSize(14);
        android.widget.ImageView arrow = U.icon(act, R.drawable.ic_arrow_forward, 16, U.WHITE);
        cta.addView(arrow);
        ((LinearLayout.LayoutParams) arrow.getLayoutParams()).leftMargin = U.dp(act, 8);
        cta.onClick(() -> navigate("/menu"));
        U.add(hero, cta, 28, U.WRAP, U.dp(act, 48));
        U.add(hero, U.img(act, HERO_IMAGE + "&w=800", U.MATCH, 192, 16), 32);
        page.addView(hero);

        // categories
        U.add(page, Cards.sectionTitle(act, "Bạn muốn ăn gì?", "/search", "Tất cả món"), 48);
        categoriesRow = U.row(act);
        categoriesRow.setGravity(Gravity.TOP);
        U.pad(categoriesRow, 0, 4, 0, 12);
        for (int i = 0; i < 8; i++) {
            View s = U.skeleton(act, 124);
            U.add(categoriesRow, s, i == 0 ? 0 : 16, U.dp(act, 100), U.dp(act, 124));
        }
        U.add(page, U.hscroll(act, categoriesRow), 20);

        // list / grid switch for the sections below
        U.add(page, buildViewToggle(), 32);

        // recommendations
        picksSection = U.col(act);
        page.addView(picksSection);
        picksSection.setVisibility(View.GONE);

        // nearby restaurants
        nearbyHeader = U.col(act);
        U.add(page, nearbyHeader, 48);
        nearbyBody = U.col(act);
        for (int i = 0; i < 4; i++) U.add(nearbyBody, U.skeleton(act, 240), i == 0 ? 0 : 20);
        U.add(page, nearbyBody, 20);

        // best sellers
        U.add(page, Cards.sectionTitle(act, "Món bán chạy", "/search", "Tất cả món"), 48);
        popularBody = U.col(act);
        for (int i = 0; i < 3; i++) U.add(popularBody, U.skeleton(act, 288), i == 0 ? 0 : 20);
        U.add(page, popularBody, 20);

        Db.from("fg_categories").select("*").order("sort").list(Category.class, (list, err) -> {
            categoriesRow.removeAllViews();
            for (Category c : list) {
                LinearLayout item = U.col(act);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                U.pad(item, 12);
                U.card(item, 16);
                android.widget.ImageView img = U.img(act, c.image, 64, 64, 16);
                img.setBackground(U.round(0xFFFFF4EF, U.dp(act, 16)));
                item.addView(img);
                U.add(item, U.text(act, c.name, 12, U.INK, U.SEMI), 12, U.WRAP, U.WRAP);
                U.click(item, () -> navigate("/search?category=" + c.id));
                item.setMinimumWidth(U.dp(act, 100));
                U.add(categoriesRow, item, categoriesRow.getChildCount() == 0 ? 0 : 16, U.WRAP, U.WRAP);
            }
        });
        Db.from("fg_foods").select(Store.FOOD_SELECT).eq("is_popular", true).order("sold_count", false).limit(6).list(Food.class, (list, err) -> {
            popularList = list;
            renderPopular();
        });
        Store.restaurants((list, err) -> { restaurants = list; renderNearby(); });

        state().addListener(this);
        LocationState.get().addListener(this);
        onAppState();
        renderNearby();
        return scrollPage(page);
    }

    @Override
    public void onDestroy() {
        state().removeListener(this);
        LocationState.get().removeListener(this);
    }

    /** useRecommendations(user?.id, !authLoading, 6): reload when the account changes. */
    @Override
    public void onAppState() {
        AppState s = state();
        if (s.authLoading) return;
        String key = String.valueOf(s.userId());
        if (key.equals(picksFor)) return;
        picksFor = key;
        boolean signedIn = s.signedIn();
        picksLoaded = false;
        renderPicks(null, signedIn);
        Recommend.recommendations(signedIn, 6, (list, err) -> {
            if (!key.equals(picksFor)) return;
            picksLoaded = true;
            renderPicks(list, signedIn);
        });
    }

    private void renderPicks(List<Recommendation> list, boolean signedIn) {
        picksList = list;
        picksSignedIn = signedIn;
        picksSection.removeAllViews();
        // {picks?.length !== 0 && …}: shown while loading, hidden when empty
        if (list != null && list.isEmpty()) { picksSection.setVisibility(View.GONE); return; }
        picksSection.setVisibility(View.VISIBLE);
        U.add(picksSection, Cards.sectionTitle(act, signedIn ? "Gợi ý cho bạn" : "Có thể bạn sẽ thích", null, null), 48);
        picksBody = U.col(act);
        if (list == null) for (int i = 0; i < 3; i++) U.add(picksBody, U.skeleton(act, 288), i == 0 ? 0 : 20);
        else {
            List<Food> foods = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            for (Recommendation r : list) { foods.add(r.food); reasons.add(r.reason); }
            picksBody.addView(foodsView(foods, reasons));
        }
        U.add(picksSection, picksBody, 20);
    }

    @Override
    public void onLocation() { renderNearby(); }

    /** open ones first, then nearest to the customer */
    private void renderNearby() {
        if (nearbyHeader == null) return;
        boolean hasPlace = LocationState.get().place != null;
        nearbyHeader.removeAllViews();
        nearbyHeader.addView(Cards.sectionTitle(act, hasPlace ? "Nhà hàng gần bạn" : "Nhà hàng", "/menu", null));
        if (restaurants == null) return;
        List<Restaurant> sorted = new ArrayList<>(restaurants);
        Collections.sort(sorted, (a, b) -> {
            int open = Boolean.compare(StoreHours.of(b).open, StoreHours.of(a).open);
            return open != 0 ? open : Double.compare(Cards.distanceOrStored(a), Cards.distanceOrStored(b));
        });
        nearbyBody.removeAllViews();
        int n = Math.min(8, sorted.size());
        if (grid) {
            List<View> cells = new ArrayList<>();
            for (int i = 0; i < n; i++) cells.add(GridCards.restaurantTile(act, sorted.get(i)));
            nearbyBody.addView(U.grid(act, 2, cells, 12));
        } else {
            for (int i = 0; i < n; i++) U.add(nearbyBody, Cards.restaurantCard(act, sorted.get(i)), i == 0 ? 0 : 20);
        }
    }
}
