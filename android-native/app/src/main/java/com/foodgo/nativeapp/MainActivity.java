package com.foodgo.nativeapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.splashscreen.SplashScreen;

import com.foodgo.nativeapp.admin.AdminActivity;
import com.foodgo.nativeapp.core.Auth;
import com.foodgo.nativeapp.screens.AccountScreen;
import com.foodgo.nativeapp.screens.CartDrawer;
import com.foodgo.nativeapp.screens.CheckoutScreen;
import com.foodgo.nativeapp.screens.HomeScreen;
import com.foodgo.nativeapp.screens.LocationPicker;
import com.foodgo.nativeapp.screens.LoginScreen;
import com.foodgo.nativeapp.screens.MenuScreen;
import com.foodgo.nativeapp.screens.OrderDetailScreen;
import com.foodgo.nativeapp.screens.OrdersScreen;
import com.foodgo.nativeapp.screens.RestaurantScreen;
import com.foodgo.nativeapp.screens.Screen;
import com.foodgo.nativeapp.screens.SearchScreen;
import com.foodgo.nativeapp.shop.ShopActivity;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.ui.Overlay;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Geo;

import java.util.ArrayList;
import java.util.List;

/**
 * The customer site: header (logo, "Giao đến", search, cart), the current page and the bottom nav
 * (components/site-shell.tsx), plus the cart drawer and the "định vị" map.
 */
public class MainActivity extends BaseActivity implements AppState.Listener, LocationState.Listener {
    public static final String EXTRA_PATH = "path";
    /** where to go after Google sign-in (sessionStorage 'foodgo-oauth-next' on the web) */
    public static String oauthNext = "/";

    private final List<Screen> stack = new ArrayList<>();
    private FrameLayout host;
    private EditText search;
    private TextView cartBadge, navCartBadge;
    private TextView chipTitle, chipText;
    private ImageView chipIcon;
    private ProgressBar chipSpinner;
    private final List<View[]> navItems = new ArrayList<>(); // [view, icon, label] + path prefix in tag
    private Overlay cartOverlay;
    private LocationPicker picker;
    private boolean ready;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        buildShell();
        AppState.get().addListener(this);
        LocationState.get().addListener(this);
        LocationState.get().start();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        ready = true;
        String start = getIntent().getStringExtra(EXTRA_PATH);
        navigate(start != null ? start : "/");
        handleDeepLink(getIntent());
        onAppState();
        onLocation();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String p = intent.getStringExtra(EXTRA_PATH);
        if (p != null) {
            // back from MoMo onto the order page it started from: take that page's place
            Screen cur = current();
            if (cur != null && cur.pathname().equals(Uri.parse("https://foodgo.local" + p).getPath())) replace(p);
            else navigate(p);
        }
        handleDeepLink(intent);
    }

    @Override
    protected void onDestroy() {
        AppState.get().removeListener(this);
        LocationState.get().removeListener(this);
        for (Screen s : stack) s.onDestroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ shell

    private void buildShell() {
        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setFitsSystemWindows(true);

        // header: logo · "Giao đến" chip · cart, then the search box on its own row (phone layout)
        LinearLayout header = U.col(this);
        header.setBackgroundColor(U.WHITE);
        U.pad(header, 20, 12, 20, 12);
        LinearLayout top = U.row(this);
        top.addView(logo());
        LinearLayout chip = U.row(this);
        U.pad(chip, 8, 6);
        U.pressable(chip, U.WHITE, 12);
        chipIcon = U.icon(this, R.drawable.ic_pin, 16, U.ORANGE);
        chipSpinner = U.progress(this, 16, U.ORANGE);
        chipSpinner.setVisibility(View.GONE);
        chip.addView(chipIcon);
        chip.addView(chipSpinner);
        LinearLayout chipCol = U.col(this);
        chipTitle = U.text(this, "Giao đến", 11, U.SUBTLE);
        chipText = U.ellipsize(U.text(this, "Chọn vị trí giao hàng", 14, U.INK, U.BOLD), 1);
        chipCol.addView(chipTitle);
        chipCol.addView(chipText);
        U.addFlex(chip, chipCol, 8);
        chip.setOnClickListener(v -> LocationState.get().setPickerOpen(true));
        U.addFlex(top, chip, 16);
        FrameLayout cart = new FrameLayout(this);
        ImageView cartIcon = U.icon(this, R.drawable.ic_cart, 24, U.MUTED);
        cart.addView(cartIcon, new FrameLayout.LayoutParams(U.dp(this, 24), U.dp(this, 24), Gravity.CENTER));
        cartBadge = badge(20, 10);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(U.dp(this, 20), U.dp(this, 20), Gravity.TOP | Gravity.END);
        cart.addView(cartBadge, bp);
        U.pressable(cart, U.WHITE, 12);
        cart.setOnClickListener(v -> AppState.get().setCartOpen(true));
        cart.setContentDescription("Giỏ hàng");
        U.add(top, cart, 12, U.dp(this, 48), U.dp(this, 48));
        header.addView(top);

        search = U.input(this, "Bạn muốn ăn gì hôm nay?");
        search.setBackground(U.round(U.SOFT, U.dp(this, 12)));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        android.graphics.drawable.Drawable sd = U.drawable(this, R.drawable.ic_search, U.SUBTLE);
        sd.setBounds(0, 0, U.dp(this, 20), U.dp(this, 20));
        search.setCompoundDrawablesRelative(sd, null, null, null);
        search.setCompoundDrawablePadding(U.dp(this, 12));
        search.setTextSize(14);
        search.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN)) {
                String q = search.getText().toString().trim();
                hideKeyboard();
                navigate("/search" + (q.isEmpty() ? "" : "?q=" + Uri.encode(q)));
                return true;
            }
            return false;
        });
        // voice search: the mic sits inside the search box, at its end
        FrameLayout searchBox = new FrameLayout(this);
        search.setPadding(search.getPaddingLeft(), 0, U.dp(this, 52), 0);
        searchBox.addView(search, new FrameLayout.LayoutParams(U.MATCH, U.dp(this, 48)));
        ImageView mic = U.icon(this, R.drawable.ic_mic, 22, U.ORANGE);
        U.pad(mic, 9);
        U.pressable(mic, 0x00FFFFFF, 999);
        mic.setContentDescription("Tìm bằng giọng nói");
        mic.setOnClickListener(v -> startVoiceSearch());
        searchBox.addView(mic, new FrameLayout.LayoutParams(U.dp(this, 40), U.dp(this, 40), Gravity.END | Gravity.CENTER_VERTICAL));
        ((FrameLayout.LayoutParams) mic.getLayoutParams()).rightMargin = U.dp(this, 6);
        U.add(header, searchBox, 12, U.MATCH, U.dp(this, 48));
        root.addView(header);
        root.addView(U.divider(this, U.LINE));

        host = new FrameLayout(this);
        root.addView(host, new LinearLayout.LayoutParams(U.MATCH, 0, 1));

        root.addView(U.divider(this, U.LINE));
        LinearLayout nav = U.row(this);
        nav.setBackgroundColor(U.WHITE);
        U.pad(nav, 0, 10, 0, 10);
        navItem(nav, "/", "Trang chủ", R.drawable.ic_home);
        navItem(nav, "/menu", "Nhà hàng", R.drawable.ic_store);
        navItem(nav, "/orders", "Đơn hàng", R.drawable.ic_receipt);
        navItem(nav, "cart", "Giỏ hàng", R.drawable.ic_bag);
        navItem(nav, "/account", "Tài khoản", R.drawable.ic_person);
        root.addView(nav);
        setContentView(root);
    }

    private View logo() {
        LinearLayout l = U.row(this);
        TextView f = U.text(this, "F", 18, U.WHITE, U.XBOLD);
        f.setGravity(Gravity.CENTER);
        U.bg(f, U.ORANGE, 12);
        l.addView(f, new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 40)));
        TextView name = U.text(this, "", 20, U.INK, U.XBOLD);
        android.text.SpannableString s = new android.text.SpannableString("FoodGo");
        s.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), 4, 6, 0);
        name.setText(s);
        U.add(l, name, 8, U.WRAP, U.WRAP);
        l.setOnClickListener(v -> navigate("/"));
        return l;
    }

    private TextView badge(int sizeDp, float sp) {
        TextView b = U.text(this, "", sp, U.WHITE, U.BOLD);
        b.setGravity(Gravity.CENTER);
        U.bg(b, U.ORANGE, 999);
        b.setVisibility(View.GONE);
        b.setIncludeFontPadding(false);
        return b;
    }

    private void navItem(LinearLayout nav, String target, String label, int icon) {
        FrameLayout cell = new FrameLayout(this);
        LinearLayout item = U.col(this);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView i = U.icon(this, icon, 24, U.SUBTLE);
        item.addView(i);
        TextView t = U.text(this, label, 12, U.SUBTLE);
        U.add(item, t, 4, U.WRAP, U.WRAP);
        cell.addView(item, new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER));
        if (target.equals("cart")) {
            navCartBadge = badge(16, 9);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(U.dp(this, 16), U.dp(this, 16), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            bp.leftMargin = U.dp(this, 16);
            cell.addView(navCartBadge, bp);
        }
        U.click(cell, () -> {
            if (target.equals("cart")) AppState.get().setCartOpen(true);
            else if (target.equals("/account")) navigate(AppState.get().signedIn() ? "/account" : "/login");
            else navigate(target);
        });
        nav.addView(cell, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View[] entry = {cell, i, t};
        cell.setTag(target);
        navItems.add(entry);
    }

    private void updateNav() {
        String p = current() != null ? current().pathname() : "/";
        for (View[] e : navItems) {
            String target = (String) e[0].getTag();
            boolean active;
            switch (target) {
                case "/": active = p.equals("/"); break;
                case "/menu": active = p.startsWith("/menu") || p.startsWith("/restaurant") || p.startsWith("/search"); break;
                case "/orders": active = p.startsWith("/orders"); break;
                case "/account": active = p.startsWith("/account") || p.equals("/login"); break;
                default: active = false;
            }
            int color = active ? U.ORANGE : U.SUBTLE;
            ((ImageView) e[1]).setColorFilter(color);
            ((TextView) e[2]).setTextColor(color);
        }
    }

    // ------------------------------------------------------------------ voice search

    private final androidx.activity.result.ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                java.util.ArrayList<String> said = r.getData().getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (said == null || said.isEmpty() || said.get(0).trim().isEmpty()) return;
                String q = said.get(0).trim();
                search.setText(q);
                search.setSelection(q.length());
                navigate("/search?q=" + Uri.encode(q));
            });

    /** Android's speech recognizer (Vietnamese); what was said goes into the search box and is searched. */
    private void startVoiceSearch() {
        hideKeyboard();
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Bạn muốn ăn gì hôm nay?")
                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            voiceLauncher.launch(i);
        } catch (android.content.ActivityNotFoundException e) {
            AppState.get().toastError("Thiết bị chưa hỗ trợ tìm bằng giọng nói");
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (imm != null && f != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        if (f != null) f.clearFocus();
    }

    // ------------------------------------------------------------------ state

    @Override
    public void onAppState() {
        AppState s = AppState.get();
        int n = s.cartCount();
        cartBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        cartBadge.setText(String.valueOf(n));
        navCartBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        navCartBadge.setText(String.valueOf(n));

        if (s.cartOpen && (cartOverlay == null || !cartOverlay.isShowing())) {
            cartOverlay = new Overlay(this, new CartDrawer(this).view(), Overlay.RIGHT);
            cartOverlay.onDismiss(() -> { if (AppState.get().cartOpen) AppState.get().setCartOpen(false); });
            cartOverlay.show();
        } else if (!s.cartOpen && cartOverlay != null && cartOverlay.isShowing()) {
            cartOverlay.dismiss();
        }
        checkAuthGate();
    }

    @Override
    public void onLocation() {
        LocationState l = LocationState.get();
        boolean rough = "ready".equals(l.status) && l.place != null && l.place.accuracy > Geo.ROUGH_ACCURACY_M;
        chipSpinner.setVisibility("locating".equals(l.status) ? View.VISIBLE : View.GONE);
        chipIcon.setVisibility("locating".equals(l.status) ? View.GONE : View.VISIBLE);
        android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder("Giao đến");
        if (rough) {
            int start = t.length();
            t.append(" • vị trí ước tính, bấm để chỉnh");
            t.setSpan(new android.text.style.ForegroundColorSpan(U.WARN), start, t.length(), 0);
        }
        chipTitle.setText(t);
        String text = "locating".equals(l.status) ? "Đang xác định vị trí..."
                : l.place != null ? l.place.address
                : "error".equals(l.status) ? l.error
                : "Chọn vị trí giao hàng";
        chipText.setText(text);
        boolean err = "error".equals(l.status) && l.place == null;
        chipText.setTextColor(err ? U.WARN : U.INK);
        U.weight(chipText, err ? U.SEMI : U.BOLD);

        if (l.pickerOpen && (picker == null || !picker.isShowing())) {
            picker = new LocationPicker(this);
            picker.show();
        } else if (!l.pickerOpen && picker != null && picker.isShowing()) {
            picker.dismiss();
        }
    }

    // ------------------------------------------------------------------ routing

    public Screen current() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }

    public boolean isCurrent(Screen s) { return current() == s; }

    public boolean inStack(Screen s) { return stack.contains(s); }

    private Screen create(String path) {
        Uri u = Uri.parse("https://foodgo.local" + path);
        List<String> seg = u.getPathSegments();
        String first = seg.isEmpty() ? "" : seg.get(0);
        switch (first) {
            case "": return new HomeScreen();
            case "menu": return new MenuScreen();
            case "search": return new SearchScreen();
            case "restaurant": return seg.size() > 1 ? new RestaurantScreen() : new MenuScreen();
            case "checkout": return new CheckoutScreen();
            case "orders": return seg.size() > 1 ? new OrderDetailScreen() : new OrdersScreen();
            case "account": return new AccountScreen();
            case "login": return new LoginScreen();
            default: return new HomeScreen();
        }
    }

    /** router.push() */
    public void navigate(String path) { go(path, false); }

    /** router.replace() */
    public void replace(String path) { go(path, true); }

    private void go(String path, boolean replace) {
        if (!ready || path == null) return;
        if (path.isEmpty()) path = "/";
        String pathname = Uri.parse("https://foodgo.local" + path).getPath();
        if (pathname == null || pathname.isEmpty()) pathname = "/";
        if (pathname.startsWith("/admin")) {
            startActivity(new Intent(this, AdminActivity.class).putExtra(EXTRA_PATH, path));
            return;
        }
        if (pathname.startsWith("/shop")) {
            startActivity(new Intent(this, ShopActivity.class).putExtra(EXTRA_PATH, path));
            return;
        }
        Screen cur = current();
        if (cur != null && cur.path.equals(path) && !replace) return;
        hideKeyboard();
        Screen next = create(path);
        next.attach(this, path);
        if (pathname.equals("/")) {
            // home is the root of the history: going there clears what's above it
            for (Screen s : stack) s.onDestroy();
            stack.clear();
        } else if (replace && cur != null) {
            stack.remove(cur);
            cur.onDestroy();
        }
        stack.add(next);
        show();
    }

    private void show() {
        Screen s = current();
        host.removeAllViews();
        if (s == null) return;
        if (s.requiresAuth() && (AppState.get().authLoading || !AppState.get().signedIn())) {
            host.addView(U.spinner(this));
            checkAuthGate();
        } else {
            View v = s.view();
            if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
            host.addView(v, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            s.onShow();
        }
        // the search box mirrors ?q= on the search page
        Uri u = Uri.parse("https://foodgo.local" + s.path);
        if ("/search".equals(u.getPath())) {
            String q = u.getQueryParameter("q");
            search.setText(q != null ? q : "");
        }
        updateNav();
    }

    /** RequireAuth: signed-out visitors of a protected page go to /login?next=<page>. */
    private void checkAuthGate() {
        Screen s = current();
        if (s == null || !s.requiresAuth()) return;
        AppState st = AppState.get();
        if (st.authLoading) return;
        if (!st.signedIn()) {
            // after "Đăng xuất" the app is already heading home; only unexpected sign-outs come back here after login
            if (!st.signingOut) replace("/login?next=" + Uri.encode(s.pathname()));
            return;
        }
        if (!s.isBuilt() || host.getChildCount() == 0 || host.getChildAt(0) != s.view()) show();
    }

    private void back() {
        if (AppState.get().cartOpen) { AppState.get().setCartOpen(false); return; }
        Screen s = current();
        if (s != null && s.onBack()) return;
        if (stack.size() > 1 && s != null && !"/".equals(s.pathname())) {
            stack.remove(s);
            s.onDestroy();
            show();
            return;
        }
        // same as the WebView shell: on the home page (or with no history) the app is minimised
        moveTaskToBack(true);
    }

    // ------------------------------------------------------------------ Google sign-in deep link

    /** com.foodgo.app://native-auth-callback#access_token=...&refresh_token=... */
    private void handleDeepLink(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null || !data.toString().startsWith(Config.AUTH_CALLBACK)) return;
        intent.setData(null);
        Uri parsed = Uri.parse(data.toString().replace('#', data.toString().contains("?") ? '&' : '?'));
        String error = parsed.getQueryParameter("error_description");
        if (error != null) { AppState.get().toastError(error); return; }
        String access = parsed.getQueryParameter("access_token");
        String refresh = parsed.getQueryParameter("refresh_token");
        if (access == null || refresh == null) { AppState.get().toastError("Đăng nhập Google không thành công"); return; }
        long expiresAt = 0;
        try { expiresAt = Long.parseLong(parsed.getQueryParameter("expires_at")); } catch (Exception ignored) {}
        Auth.setSession(access, refresh, expiresAt, (session, err) -> {
            if (err != null) { AppState.get().toastError(err); return; }
            AppState.get().toast("Đăng nhập thành công");
            replace(oauthNext != null ? oauthNext : "/");
        });
    }
}
