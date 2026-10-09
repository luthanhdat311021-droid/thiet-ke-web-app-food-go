package com.foodgo.nativeapp.admin;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.Overlay;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Validate;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** components/admin/entity-manager.tsx: generic list + create/edit/delete for a Supabase table (RLS applies). */
public class EntityManager extends LinearLayout {

    public static class Field {
        public final String key, label, type;
        public boolean required, wide;
        public String hint, addressKey;
        public List<String[]> options; // {value, label}
        public Object def;
        public boolean hasDef;

        /** type: text | tel | number | textarea | image | select | checkbox | date | time | location | hidden */
        public Field(String key, String label, String type) { this.key = key; this.label = label; this.type = type; }

        public Field req() { required = true; return this; }
        public Field hint(String h) { hint = h; return this; }
        public Field wide() { wide = true; return this; }
        public Field def(Object d) { def = d; hasDef = true; return this; }
        public Field address(String k) { addressKey = k; return this; }
        public Field options(List<String[]> o) { options = o; return this; }
    }

    public static class Column {
        public final String label;
        public final Function<JsonObject, View> render;
        public Column(String label, Function<JsonObject, View> render) { this.label = label; this.render = render; }
    }

    private final Activity act;
    private String table, title, select = "*", orderBy = "id", searchKey = "name", deleteWarning;
    private boolean allowCreate = true, allowDelete = true;
    private final Map<String, Object> match = new LinkedHashMap<>();
    private List<Field> fields = new ArrayList<>();
    private List<Column> columns = new ArrayList<>();
    private String filterKey, filterLabel;
    private List<String[]> filterOptions;
    private Runnable onChanged;

    private List<JsonObject> rows;
    private String q = "", filterValue = "";
    private LinearLayout list;

    public EntityManager(Activity a) { super(a); act = a; setOrientation(VERTICAL); }

    public EntityManager table(String t) { table = t; return this; }
    public EntityManager title(String t) { title = t; return this; }
    public EntityManager select(String s) { select = s; return this; }
    public EntityManager orderBy(String o) { orderBy = o; return this; }
    public EntityManager searchKey(String k) { searchKey = k; return this; }
    public EntityManager noCreate() { allowCreate = false; return this; }
    public EntityManager noDelete() { allowDelete = false; return this; }
    public EntityManager deleteWarning(String w) { deleteWarning = w; return this; }
    public EntityManager match(String k, Object v) { match.put(k, v); return this; }
    public EntityManager fields(List<Field> f) { fields = f; return this; }
    public EntityManager columns(List<Column> c) { columns = c; return this; }
    public EntityManager filter(String key, String label, List<String[]> options) { filterKey = key; filterLabel = label; filterOptions = options; return this; }
    public EntityManager onChanged(Runnable r) { onChanged = r; return this; }

    /** Builds the header and loads the rows (call after configuring). */
    public EntityManager start() {
        removeAllViews();
        addView(U.text(act, title, 22, U.INK, U.XBOLD));
        LinearLayout tools = U.col(act);
        if (filterKey != null) {
            Select f = new Select(act).title(filterLabel).option("", filterLabel + ": tất cả");
            for (String[] o : filterOptions) f.option(o[0], o[1]);
            f.onChange(v -> { filterValue = v; render(); });
            tools.addView(f, U.lp(U.MATCH, U.dp(act, 40)));
        }
        LinearLayout row = U.row(act);
        EditText search = U.input(act, "Tìm...");
        search.setTextSize(14);
        android.graphics.drawable.Drawable sd = U.drawable(act, R.drawable.ic_search, U.SUBTLE);
        sd.setBounds(0, 0, U.dp(act, 16), U.dp(act, 16));
        search.setCompoundDrawablesRelative(sd, null, null, null);
        search.setCompoundDrawablePadding(U.dp(act, 8));
        U.pad(search, 12, 0);
        search.setContentDescription("Tìm kiếm");
        search.addTextChangedListener(watch(v -> { q = v; render(); }));
        row.addView(search, new LayoutParams(0, U.dp(act, 40), 1));
        if (allowCreate) {
            PButton add = new PButton(act, "Thêm", PButton.PRIMARY, R.drawable.ic_add);
            add.label().setTextSize(14);
            U.pad(add, 16, 0);
            add.onClick(this::startCreate);
            U.add(row, add, 8, U.WRAP, U.dp(act, 40));
        }
        U.add(tools, row, filterKey != null ? 8 : 0);
        U.add(this, tools, 12);
        list = U.col(act);
        U.add(this, list, 16);
        render();
        load();
        return this;
    }

    private interface Fn { void on(String v); }

    private static TextWatcher watch(Fn f) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { f.on(s.toString()); }
        };
    }

    public void load() {
        Db query = Db.from(table).select(select);
        for (Map.Entry<String, Object> e : match.entrySet()) query.eq(e.getKey(), e.getValue());
        query.order(orderBy).rows((data, err) -> {
            if (err != null) AppState.get().toastError(err);
            rows = data;
            render();
        });
    }

    private static String str(JsonObject r, String k) { String s = Json.str(r, k); return s != null ? s : ""; }

    private void render() {
        list.removeAllViews();
        if (rows == null) { list.addView(empty("Đang tải...")); return; }
        int n = 0;
        for (JsonObject r : rows) {
            if (!q.isEmpty() && !str(r, searchKey).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) continue;
            if (filterKey != null && !filterValue.isEmpty() && !str(r, filterKey).equals(filterValue)) continue;
            U.add(list, card(r), n++ == 0 ? 0 : 12);
        }
        if (n == 0) list.addView(empty("Không có dữ liệu"));
    }

    private View empty(String s) {
        TextView t = U.text(act, s, 14, U.SUBTLE);
        t.setGravity(Gravity.CENTER);
        U.pad(t, 0, 40);
        U.card(t, 16);
        return t;
    }

    /** phones: one card per row (first column = picture, second = title, the rest as label/value) */
    private View card(JsonObject r) {
        LinearLayout c = U.row(act);
        c.setGravity(Gravity.TOP);
        U.pad(c, 12);
        U.card(c, 16);
        if (!columns.isEmpty()) c.addView(columns.get(0).render.apply(r));
        LinearLayout info = U.col(act);
        if (columns.size() > 1) info.addView(columns.get(1).render.apply(r));
        for (int i = 2; i < columns.size(); i++) {
            LinearLayout line = U.row(act);
            line.setGravity(Gravity.TOP);
            line.addView(U.text(act, columns.get(i).label, 12, U.SUBTLE));
            View v = columns.get(i).render.apply(r);
            if (v instanceof TextView) { ((TextView) v).setTextSize(12); }
            U.addFlex(line, v, 8);
            U.add(info, line, 2);
        }
        U.addFlex(c, info, 12);
        LinearLayout actions = U.col(act);
        actions.addView(iconBtn(R.drawable.ic_edit, "Sửa", U.SOFT, () -> openEditor(r.deepCopy())));
        if (allowDelete) U.add(actions, iconBtn(R.drawable.ic_delete, "Xóa", U.WHITE, () -> remove(r)), 4, U.dp(act, 36), U.dp(act, 36));
        U.add(c, actions, 8, U.WRAP, U.WRAP);
        return c;
    }

    private View iconBtn(int icon, String label, int bg, Runnable r) {
        FrameLayout b = new FrameLayout(act);
        U.pressable(b, bg, 8);
        b.addView(U.icon(act, icon, 16, U.MUTED), new FrameLayout.LayoutParams(U.dp(act, 16), U.dp(act, 16), Gravity.CENTER));
        b.setContentDescription(label);
        b.setOnClickListener(v -> r.run());
        b.setLayoutParams(new LayoutParams(U.dp(act, 36), U.dp(act, 36)));
        return b;
    }

    private void remove(JsonObject row) {
        String name = Json.str(row, searchKey);
        if (name == null) name = Json.str(row, "id");
        Dialogs.confirm(act, "Xóa \"" + name + "\"? " + (deleteWarning != null ? deleteWarning + " " : "") + "Thao tác này không thể hoàn tác.", () ->
                Db.from(table).delete().eq("id", Json.str(row, "id")).run((d, err) -> {
                    if (err != null) { AppState.get().toastError(err); return; }
                    AppState.get().toast("Đã xóa");
                    load();
                    if (onChanged != null) onChanged.run();
                }));
    }

    private void startCreate() {
        JsonObject blank = new JsonObject();
        for (Field f : fields) {
            if (f.type.equals("location")) { blank.add("lat", JsonNull.INSTANCE); blank.add("lng", JsonNull.INSTANCE); continue; }
            Object v = f.hasDef ? f.def : f.type.equals("checkbox") ? Boolean.TRUE : f.type.equals("select") ? (f.options != null && !f.options.isEmpty() ? f.options.get(0)[0] : null) : "";
            blank.add(f.key, Json.val(v));
        }
        openEditor(blank);
    }

    // ------------------------------------------------------------------ editor

    private void openEditor(JsonObject editing) {
        boolean isEdit = editing.has("id") && !editing.get("id").isJsonNull();
        LinearLayout panel = U.col(act);
        panel.setBackgroundColor(U.WHITE);
        panel.setFitsSystemWindows(true);
        LinearLayout head = U.row(act);
        U.pad(head, 20, 16);
        U.addFlex(head, U.text(act, isEdit ? "Chỉnh sửa" : "Thêm mới", 18, U.INK, U.XBOLD), 0);
        FrameLayout close = new FrameLayout(act);
        U.pressable(close, U.SOFT, 999);
        close.addView(U.icon(act, R.drawable.ic_close, 24, U.INK), new FrameLayout.LayoutParams(U.dp(act, 24), U.dp(act, 24), Gravity.CENTER));
        close.setContentDescription("Đóng");
        head.addView(close, new LayoutParams(U.dp(act, 40), U.dp(act, 40)));
        panel.addView(head);
        panel.addView(U.divider(act, U.LINE));
        ScrollView sv = new ScrollView(act);
        LinearLayout form = U.col(act);
        U.pad(form, 20);
        sv.addView(form);
        panel.addView(sv, new LayoutParams(U.MATCH, 0, 1));
        panel.addView(U.divider(act, U.LINE));
        LinearLayout foot = U.row(act);
        U.pad(foot, 20, 16);
        PButton save = new PButton(act, "Lưu", PButton.PRIMARY);
        PButton cancel = new PButton(act, "Hủy", PButton.OUTLINE);
        foot.addView(save, new LayoutParams(0, U.dp(act, 44), 1));
        U.add(foot, cancel, 12, U.WRAP, U.dp(act, 44));
        panel.addView(foot);

        Map<String, EditText> required = new LinkedHashMap<>();
        Map<String, EditText> textInputs = new LinkedHashMap<>();
        List<PlacePicker> pickers = new ArrayList<>();
        for (Field f : fields) {
            if (f.type.equals("hidden")) continue;
            View v = fieldView(f, editing, required, textInputs, pickers);
            U.add(form, v, form.getChildCount() == 0 ? 0 : 16);
        }
        // the address text box and the map pin keep each other in sync
        for (PlacePicker p : pickers) {
            for (Field f : fields) if (f.type.equals("location") && f.addressKey != null && textInputs.containsKey(f.addressKey)) {
                EditText ae = textInputs.get(f.addressKey);
                ae.addTextChangedListener(watch(p::setAddress));
                p.setTag(ae);
            }
        }

        Overlay o = new Overlay(act, panel, Overlay.RIGHT);
        U.click(close, o::dismiss);
        cancel.onClick(o::dismiss);
        save.onClick(() -> save(editing, isEdit, required, save, o));
        o.show();
    }

    private View fieldView(Field f, JsonObject e, Map<String, EditText> required, Map<String, EditText> textInputs, List<PlacePicker> pickers) {
        String cur = e.has(f.key) && !e.get(f.key).isJsonNull() ? (e.get(f.key).isJsonPrimitive() ? e.get(f.key).getAsString() : e.get(f.key).toString()) : "";
        LinearLayout box = U.col(act);
        if (f.type.equals("checkbox")) {
            CheckBox cb = U.check(act, f.label, Json.bool(e, f.key));
            cb.setTextColor(U.INK);
            U.weight(cb, U.SEMI);
            cb.setOnCheckedChangeListener((b, c) -> e.addProperty(f.key, c));
            box.addView(cb);
            return box;
        }
        android.text.SpannableStringBuilder lb = new android.text.SpannableStringBuilder(f.label);
        if (f.required) {
            int s = lb.length();
            lb.append(" *");
            lb.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), s, lb.length(), 0);
        }
        box.addView(U.text(act, lb, 14, U.INK, U.SEMI));
        View input;
        switch (f.type) {
            case "location": {
                PlacePicker p = new PlacePicker(act, Json.numOrNull(e, "lat"), Json.numOrNull(e, "lng"),
                        f.addressKey != null ? Json.str(e, f.addressKey) : null, (lat, lng, address) -> {
                    e.add("lat", Json.val(lat));
                    e.add("lng", Json.val(lng));
                    if (f.addressKey != null && address != null) {
                        e.addProperty(f.addressKey, address);
                        Object t = pickersTag(pickers, lat, lng);
                        if (t instanceof EditText) ((EditText) t).setText(address);
                    }
                });
                pickers.add(p);
                input = p;
                break;
            }
            case "image": {
                input = new ImageInput(act, cur).onChange(v -> e.addProperty(f.key, v));
                break;
            }
            case "select": {
                Select s = new Select(act).title(f.label);
                if (!f.required) s.option("", "— Không chọn —");
                if (f.options != null) for (String[] o : f.options) s.option(o[0], o[1]);
                s.set(cur);
                if (s.value() == null || !cur.equals(s.value())) s.set(cur);
                s.onChange(v -> e.addProperty(f.key, v));
                input = s;
                s.setLayoutParams(U.lp(U.MATCH, U.dp(act, 44)));
                break;
            }
            case "textarea": {
                EditText t = U.textarea(act, "", 3);
                t.setTextSize(14);
                t.setText(cur);
                t.addTextChangedListener(watch(v -> e.addProperty(f.key, v)));
                input = t;
                break;
            }
            case "date":
            case "time": {
                boolean date = f.type.equals("date");
                EditText t = U.input(act, date ? "dd/mm/yyyy" : "--:--");
                t.setTextSize(14);
                t.setFocusable(false);
                t.setText(date ? (cur.length() >= 10 ? cur.substring(0, 10) : cur) : (cur.length() >= 5 ? cur.substring(0, 5) : cur));
                t.addTextChangedListener(watch(v -> e.addProperty(f.key, v)));
                t.setOnClickListener(v -> pickDateTime(t, date));
                if (f.required) required.put(f.key, t);
                input = t;
                break;
            }
            default: {
                EditText t = U.input(act, f.type.equals("tel") ? "0912 345 678" : "");
                t.setTextSize(14);
                if (f.type.equals("number")) t.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                if (f.type.equals("tel")) t.setInputType(InputType.TYPE_CLASS_PHONE);
                t.setText(f.type.equals("number") && cur.endsWith(".0") ? cur.substring(0, cur.length() - 2) : cur);
                t.addTextChangedListener(watch(v -> {
                    if (f.type.equals("tel")) {
                        String fl = Validate.phoneInput(v);
                        if (!fl.equals(v)) { t.setText(fl); t.setSelection(fl.length()); return; }
                    }
                    e.addProperty(f.key, v);
                }));
                if (f.required) required.put(f.key, t);
                textInputs.put(f.key, t);
                input = t;
            }
        }
        U.add(box, input, 8);
        if (f.hint != null) U.add(box, U.text(act, f.hint, 12, U.SUBTLE), 4);
        return box;
    }

    private static Object pickersTag(List<PlacePicker> pickers, Double lat, Double lng) {
        for (PlacePicker p : pickers) if (p.getTag() != null) return p.getTag();
        return null;
    }

    private void pickDateTime(EditText t, boolean date) {
        Calendar c = Calendar.getInstance();
        String cur = t.getText().toString();
        if (date) {
            if (cur.matches("\\d{4}-\\d{2}-\\d{2}")) c.set(Integer.parseInt(cur.substring(0, 4)), Integer.parseInt(cur.substring(5, 7)) - 1, Integer.parseInt(cur.substring(8, 10)));
            DatePickerDialog d = new DatePickerDialog(act, (v, y, m, day) -> t.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            d.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Xóa", (di, w) -> t.setText(""));
            d.show();
        } else {
            int h = 7, m = 0;
            if (cur.matches("\\d{2}:\\d{2}")) { h = Integer.parseInt(cur.substring(0, 2)); m = Integer.parseInt(cur.substring(3, 5)); }
            TimePickerDialog d = new TimePickerDialog(act, (v, hh, mm) -> t.setText(String.format(Locale.US, "%02d:%02d", hh, mm)), h, m, true);
            d.setButton(TimePickerDialog.BUTTON_NEUTRAL, "Xóa", (di, w) -> t.setText(""));
            d.show();
        }
    }

    private void save(JsonObject editing, boolean isEdit, Map<String, EditText> required, PButton btn, Overlay o) {
        AppState s = AppState.get();
        for (Map.Entry<String, EditText> r : required.entrySet()) {
            if (r.getValue().getText().toString().trim().isEmpty()) { r.getValue().setError("Vui lòng điền vào trường này"); r.getValue().requestFocus(); return; }
        }
        JsonObject payload = new JsonObject();
        for (Field f : fields) {
            if (f.type.equals("location")) {
                Double lat = Json.numOrNull(editing, "lat"), lng = Json.numOrNull(editing, "lng");
                boolean has = lat != null && lng != null;
                if (f.required && !has) { s.toastError("Vui lòng ghim " + f.label.toLowerCase(Locale.ROOT) + " trên bản đồ"); return; }
                payload.add("lat", has ? new JsonPrimitive(lat) : JsonNull.INSTANCE);
                payload.add("lng", has ? new JsonPrimitive(lng) : JsonNull.INSTANCE);
                continue;
            }
            JsonElement raw = editing.get(f.key);
            String v = raw == null || raw.isJsonNull() ? null : raw.isJsonPrimitive() ? raw.getAsString() : raw.toString();
            if (f.type.equals("tel")) {
                String phone = v != null && !v.isEmpty() ? Validate.normalizePhone(v) : null;
                if (v != null && !v.isEmpty() && phone == null) { s.toastError(f.label + ": " + Validate.PHONE_ERROR.toLowerCase(Locale.ROOT)); return; }
                payload.add(f.key, Json.val(phone));
                continue;
            }
            switch (f.type) {
                case "number":
                    if (v == null || v.isEmpty()) payload.add(f.key, JsonNull.INSTANCE);
                    else try { payload.add(f.key, numberValue(Double.parseDouble(v))); } catch (NumberFormatException ex) { payload.add(f.key, JsonNull.INSTANCE); }
                    break;
                case "select":
                    if (v == null || v.isEmpty()) payload.add(f.key, JsonNull.INSTANCE);
                    else try { payload.add(f.key, numberValue(Double.parseDouble(v))); } catch (NumberFormatException ex) { payload.addProperty(f.key, v); }
                    break;
                case "checkbox":
                    payload.addProperty(f.key, Json.bool(editing, f.key));
                    break;
                default:
                    if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) payload.add(f.key, raw);
                    else payload.add(f.key, v == null || v.isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(v));
            }
        }
        btn.setBusy(true);
        Db q = isEdit ? Db.from(table).update(payload).eq("id", Json.str(editing, "id")) : Db.from(table).insert(payload);
        q.run((d, err) -> {
            btn.setBusy(false);
            if (err != null) { s.toastError(err); return; }
            s.toast(isEdit ? "Đã cập nhật" : "Đã thêm mới");
            o.dismiss();
            load();
            if (onChanged != null) onChanged.run();
        });
    }

    private static JsonPrimitive numberValue(double d) {
        return d == Math.rint(d) && Math.abs(d) < 9e15 ? new JsonPrimitive((long) d) : new JsonPrimitive(d);
    }

    @SuppressWarnings("unused")
    private static ImageView unused() { return null; }
}
