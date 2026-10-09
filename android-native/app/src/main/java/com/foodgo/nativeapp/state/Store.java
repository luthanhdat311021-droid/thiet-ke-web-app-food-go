package com.foodgo.nativeapp.state;

import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.model.Restaurant;

import java.util.ArrayList;
import java.util.List;

/** lib/store.ts */
public final class Store {
    private Store() {}

    /** !inner: dishes of hidden (inactive) restaurants drop out, since RLS hides the restaurant row */
    public static final String FOOD_SELECT = "*, restaurants:fg_restaurants!inner(id, name, delivery_time, distance_km, lat, lng, is_open, open_time, close_time)";

    // Restaurants change rarely: one shared list (RLS hides inactive ones from customers).
    private static List<Restaurant> cache;
    private static List<Cb<List<Restaurant>>> waiting;
    private static int generation;

    public static void restaurants(boolean fresh, Cb<List<Restaurant>> cb) {
        if (cache != null && !fresh) { cb.done(cache, null); return; }
        if (waiting != null && !fresh) { waiting.add(cb); return; }
        waiting = new ArrayList<>();
        waiting.add(cb);
        int gen = ++generation;
        Db.from("fg_restaurants").select("*").order("id").list(Restaurant.class, (list, err) -> {
            if (gen != generation) return;
            cache = list;
            List<Cb<List<Restaurant>>> w = waiting;
            waiting = null;
            for (Cb<List<Restaurant>> c : w) c.done(list, err);
        });
    }

    public static void restaurants(Cb<List<Restaurant>> cb) { restaurants(false, cb); }

    /** Always fresh: hours / "tạm đóng cửa" may have just changed. */
    public static void restaurant(long id, Cb<Restaurant> cb) {
        Db.from("fg_restaurants").select("*").eq("id", id).maybeSingle(Restaurant.class, cb);
    }

    /** fetchRestaurants(true) after an edit: just forget the cached list. */
    public static void invalidate() { restaurants(true, (l, e) -> {}); }
}
