package com.foodgo.nativeapp.admin;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.foodgo.nativeapp.BaseActivity;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Consumer;

/** ImageInput (entity-manager.tsx): paste a URL or upload into the fg-images bucket. */
public class ImageInput extends LinearLayout {
    private final ImageView preview;
    private final EditText url;
    private final PButton upload;
    private Consumer<String> onChange;
    private boolean setting;

    public ImageInput(Context c, String value) {
        super(c);
        setOrientation(HORIZONTAL);
        preview = new ImageView(c);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        U.bg(preview, U.SOFT, 12);
        U.rounded(preview, 12);
        addView(preview, new LayoutParams(U.dp(c, 80), U.dp(c, 80)));
        LinearLayout col = U.col(c);
        url = U.input(c, "Dán URL ảnh hoặc tải lên");
        url.setTextSize(14);
        url.setLayoutParams(U.lp(U.MATCH, U.dp(c, 44)));
        col.addView(url);
        upload = new PButton(c, "Tải ảnh lên", PButton.OUTLINE, R.drawable.ic_image_add);
        upload.label().setTextSize(12);
        U.pad(upload, 12, 0);
        U.add(col, upload, 8, U.WRAP, U.dp(c, 36));
        U.addFlex(this, col, 12);
        url.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                U.load(preview, s.toString());
                if (!setting && onChange != null) onChange.accept(s.toString());
            }
        });
        set(value);
        upload.onClick(this::pick);
    }

    public ImageInput onChange(Consumer<String> c) { onChange = c; return this; }

    public void set(String v) {
        setting = true;
        url.setText(v != null ? v : "");
        setting = false;
        U.load(preview, v);
    }

    private void pick() {
        android.app.Activity a = U2.activity(getContext());
        if (!(a instanceof BaseActivity)) return;
        ((BaseActivity) a).pickImage(this::uploadUri);
    }

    private void uploadUri(Uri uri) {
        Context c = getContext();
        AppState s = AppState.get();
        upload.setBusy(true);
        Net.bg(() -> {
            String name = "image";
            long size = -1;
            try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
                if (cur != null && cur.moveToFirst()) {
                    int ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = cur.getColumnIndex(OpenableColumns.SIZE);
                    if (ni >= 0) name = cur.getString(ni);
                    if (si >= 0) size = cur.getLong(si);
                }
            } catch (Exception ignored) {}
            if (size > 5 * 1024 * 1024) { Net.main(() -> { upload.setBusy(false); s.toastError("Ảnh tối đa 5MB"); }); return; }
            byte[] bytes;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while (in != null && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 5 * 1024 * 1024) break;
                }
                bytes = out.toByteArray();
            } catch (Exception e) {
                Net.main(() -> { upload.setBusy(false); s.toastError("Không đọc được ảnh"); });
                return;
            }
            if (bytes.length > 5 * 1024 * 1024) { Net.main(() -> { upload.setBusy(false); s.toastError("Ảnh tối đa 5MB"); }); return; }
            String type = c.getContentResolver().getType(uri);
            // each account uploads into its own folder (storage policy for restaurant owners)
            String path = (s.userId() != null ? s.userId() : "public") + "/" + System.currentTimeMillis() + "-" + name.replaceAll("[^\\w.-]", "_");
            byte[] data = bytes;
            Net.main(() -> Db.upload("fg-images", path, data, type, (publicUrl, err) -> {
                upload.setBusy(false);
                if (err != null) { s.toastError(err); return; }
                url.setText(publicUrl);
            }));
        });
    }
}
