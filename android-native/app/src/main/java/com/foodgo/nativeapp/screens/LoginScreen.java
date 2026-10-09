package com.foodgo.nativeapp.screens;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.MainActivity;
import com.foodgo.nativeapp.core.Auth;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.TurnstileView;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;
import com.foodgo.nativeapp.util.Password;
import com.foodgo.nativeapp.util.Validate;

/** app/login/page.tsx — signin · signup · forgot (send 6-digit code) · verify (check it) · reset (new password) */
public class LoginScreen extends Screen implements AppState.Listener {
    private String mode = "signin";
    private String next = "/";
    private boolean adminOnly;

    private LinearLayout box;
    private EditText nameInput, emailInput, passwordInput, confirmInput, otpInput;
    private String name = "", email = "", password = "", confirm = "", otp = "";
    private String error = "", info = "";
    private boolean busy;
    private String captcha;
    private TurnstileView turnstile;
    private PButton submitBtn;

    @Override
    protected View build() {
        // only allow same-site relative redirects
        String rawNext = param("next") != null ? param("next") : "/";
        next = rawNext.startsWith("/") && !rawNext.startsWith("//") ? rawNext : "/";
        adminOnly = next.startsWith("/admin");
        String m = param("mode");
        mode = "forgot".equals(m) ? "forgot" : "signup".equals(m) ? "signup" : "signin";
        if (TurnstileView.enabled()) turnstile = new TurnstileView(act).onToken(t -> captcha = t);

        LinearLayout page = U.col(act);
        box = U.card(U.pad(U.col(act), 24), 24);
        page.addView(box);
        render();
        state().addListener(this);
        onAppState();
        android.widget.ScrollView s = scrollPage(page);
        U.pad(page, 20, 40, 20, 32);
        return s;
    }

    @Override
    public void onDestroy() {
        state().removeListener(this);
        if (turnstile != null) turnstile.destroy();
    }

    /** Signed in (and not mid password-reset): continue to `next`. */
    @Override
    public void onAppState() {
        AppState s = state();
        // verifying the reset code signs the user in; stay here until the new password is saved
        if (s.authLoading || !s.signedIn() || mode.equals("verify") || mode.equals("reset") || !alive()) return;
        // /admin is only a sensible destination for admins; anyone else signing in goes home
        if (!adminOnly) { replace(next); return; }
        if (s.profile != null) replace(s.profile.isAdmin() ? next : "/");
    }

    private boolean recovering() { return mode.equals("forgot") || mode.equals("verify") || mode.equals("reset"); }

    private void setMode(String m) { mode = m; error = ""; info = ""; render(); }

    private EditText input(String hint, String value, int type, TextWatcherFn fn) {
        EditText e = U.input(act, hint);
        e.setInputType(type);
        e.setText(value);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fn.changed(e, s.toString()); }
        });
        return e;
    }

    private interface TextWatcherFn { void changed(EditText e, String v); }

    private void render() {
        box.removeAllViews();
        String title = mode.equals("signin") ? "Đăng nhập" : mode.equals("signup") ? "Tạo tài khoản" : mode.equals("forgot") ? "Quên mật khẩu" : mode.equals("verify") ? "Nhập mã xác nhận" : "Đặt lại mật khẩu";
        box.addView(U.h2(act, title));
        String sub = mode.equals("forgot") ? "Nhập email, chúng tôi sẽ gửi mã xác nhận 6 số để đặt lại mật khẩu."
                : mode.equals("verify") ? "Nhập mã 6 số trong email để xác nhận đây là bạn."
                : mode.equals("reset") ? "Nhập mật khẩu mới cho tài khoản của bạn."
                : "Đặt món nhanh hơn, theo dõi đơn hàng và lưu món yêu thích.";
        U.add(box, U.text(act, sub, 14, U.MUTED), 8);

        if (!recovering()) {
            LinearLayout g = U.row(act);
            g.setGravity(Gravity.CENTER);
            U.pressable(g, U.stroke(U.WHITE, U.dp(act, 12), U.BORDER, U.dp(act, 1)));
            ImageView gi = new ImageView(act);
            gi.setImageDrawable(googleIcon());
            g.addView(gi, new LinearLayout.LayoutParams(U.dp(act, 20), U.dp(act, 20)));
            U.add(g, U.text(act, "Tiếp tục với Google", 14, U.INK, U.SEMI), 12, U.WRAP, U.WRAP);
            g.setOnClickListener(v -> google());
            U.add(box, g, 24, U.MATCH, U.dp(act, 48));
            LinearLayout or = U.row(act);
            View l1 = U.divider(act, U.LINE), l2 = U.divider(act, U.LINE);
            or.addView(l1, new LinearLayout.LayoutParams(0, U.dp(act, 1), 1));
            U.add(or, U.text(act, "hoặc", 12, U.SUBTLE), 12, U.WRAP, U.WRAP);
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, U.dp(act, 1), 1);
            p2.leftMargin = U.dp(act, 12);
            or.addView(l2, p2);
            U.add(box, or, 24);
        }

        LinearLayout form = U.col(act);
        int gap = 16;
        if (mode.equals("signup")) {
            nameInput = input("", name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS, (e, v) -> name = v);
            form.addView(U.field(act, "Họ và tên", nameInput));
        }
        if (mode.equals("verify") || mode.equals("reset")) {
            LinearLayout r = U.row(act);
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("Email: ");
            int s = sb.length();
            sb.append(email);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, sb.length(), 0);
            sb.setSpan(new android.text.style.ForegroundColorSpan(U.INK), s, sb.length(), 0);
            U.addFlex(r, U.text(act, sb, 14, U.MUTED), 0);
            if (mode.equals("verify")) r.addView(U.link(act, "Đổi email", () -> setMode("forgot")));
            U.add(form, r, form.getChildCount() == 0 ? 0 : gap);
        } else {
            emailInput = input("ten@gmail.com", email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, (e, v) -> {
                String clean = v.replaceAll("\\s", "");
                if (!clean.equals(v)) { e.setText(clean); e.setSelection(clean.length()); return; }
                email = clean;
            });
            U.maxLength(emailInput, 254);
            U.add(form, U.field(act, "Email", emailInput), form.getChildCount() == 0 ? 0 : gap);
        }
        if (mode.equals("verify")) {
            otpInput = input("123456", otp, InputType.TYPE_CLASS_NUMBER, (e, v) -> {
                String clean = v.replaceAll("\\D", "");
                if (clean.length() > 6) clean = clean.substring(0, 6);
                if (!clean.equals(v)) { e.setText(clean); e.setSelection(clean.length()); return; }
                otp = clean;
            });
            U.add(form, U.field(act, "Mã xác nhận", otpInput), gap);
            TextView resend = U.link(act, "Gửi lại mã", this::resendCode);
            resend.setEnabled(!busy);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
            rp.gravity = Gravity.END;
            form.addView(resend, rp);
        }
        if (!mode.equals("forgot") && !mode.equals("verify")) {
            passwordInput = input("", password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, (e, v) -> password = v);
            U.add(form, U.field(act, mode.equals("reset") ? "Mật khẩu mới" : "Mật khẩu", passwordInput), gap);
        }
        if (mode.equals("reset")) {
            confirmInput = input("", confirm, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, (e, v) -> confirm = v);
            U.add(form, U.field(act, "Nhập lại mật khẩu mới", confirmInput), gap);
        }
        if (mode.equals("signin")) {
            TextView forgot = U.link(act, "Quên mật khẩu?", () -> setMode("forgot"));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
            fp.gravity = Gravity.END;
            form.addView(forgot, fp);
        }
        if (mode.equals("signup") || mode.equals("reset")) U.add(form, U.text(act, "Ít nhất 8 ký tự, gồm cả chữ và số.", 12, U.SUBTLE), 8);
        if (turnstile != null) {
            if (turnstile.getParent() != null) ((android.view.ViewGroup) turnstile.getParent()).removeView(turnstile);
            U.add(form, turnstile, 8);
        }
        if (!error.isEmpty()) U.add(form, U.errorBox(act, error), gap);
        if (!info.isEmpty()) U.add(form, U.infoBox(act, info), gap);
        String label = mode.equals("signin") ? "Đăng nhập" : mode.equals("signup") ? "Đăng ký" : mode.equals("forgot") ? "Gửi mã xác nhận" : mode.equals("verify") ? "Xác nhận mã" : "Đổi mật khẩu";
        submitBtn = new PButton(act, label, PButton.PRIMARY);
        submitBtn.label().setTextSize(16);
        submitBtn.setBusy(busy);
        submitBtn.onClick(this::submit);
        U.add(form, submitBtn, gap, U.MATCH, U.dp(act, 48));
        U.add(box, form, recovering() ? 24 : 0);

        // the code is already used up once verified, so finish here rather than leaving mid-reset
        if (!mode.equals("reset")) {
            LinearLayout foot = U.row(act);
            foot.setGravity(Gravity.CENTER);
            foot.addView(U.text(act, mode.equals("signin") ? "Chưa có tài khoản? " : "Đã có tài khoản? ", 14, U.MUTED));
            foot.addView(U.link(act, mode.equals("signin") ? "Đăng ký ngay" : "Đăng nhập", () -> setMode(mode.equals("signin") ? "signup" : "signin")));
            U.add(box, foot, 24);
        }
    }

    private void setBusy(boolean b) { busy = b; if (submitBtn != null) submitBtn.setBusy(b); }

    private void fail(String msg) {
        error = translate(msg);
        setBusy(false);
        if (turnstile != null) turnstile.reset(); // tokens are single-use
        render();
    }

    private void done() {
        setBusy(false);
        if (turnstile != null) turnstile.reset();
        render();
    }

    private void submit() {
        if (!Validate.isValidEmail(email) && !(mode.equals("verify") || mode.equals("reset"))) { error = Validate.EMAIL_ERROR; render(); return; }
        if ((mode.equals("verify") || mode.equals("reset")) && !Validate.isValidEmail(email)) { error = Validate.EMAIL_ERROR; render(); return; }
        if (TurnstileView.enabled() && captcha == null) { error = "Vui lòng chờ xác minh chống robot hoàn tất"; render(); return; }
        error = ""; info = "";
        setBusy(true);
        render();
        String mail = email.trim();
        String token = captcha;
        switch (mode) {
            case "signin":
                Auth.signInWithPassword(mail, password, token, (s, err) -> {
                    if (err != null) { fail(err); return; }
                    state().toast("Đăng nhập thành công");
                    done();
                });
                break;
            case "signup":
                Password.validateNew(password, (problem, e0) -> {
                    if (problem != null) { fail(problem); return; }
                    Auth.signUp(mail, password, name.trim(), Config.SITE + next, token, (hasSession, err) -> {
                        if (err != null) { fail(err); return; }
                        if (!Boolean.TRUE.equals(hasSession)) info = "Đã gửi email xác nhận. Vui lòng mở hộp thư và bấm vào liên kết để kích hoạt tài khoản.";
                        done();
                    });
                });
                break;
            case "forgot":
                Auth.resetPasswordForEmail(mail, token, (d, err) -> {
                    if (err != null) { fail(err); return; }
                    otp = ""; password = ""; confirm = "";
                    mode = "verify";
                    info = "Đã gửi mã xác nhận 6 số tới " + mail + ". Vui lòng kiểm tra hộp thư (cả mục Spam).";
                    done();
                });
                break;
            case "verify":
                if (!otp.matches("\\d{6}")) { fail("Mã xác nhận gồm 6 chữ số"); return; }
                // a correct code signs the user in with a recovery session, which is what lets updateUser set the password
                Auth.verifyRecovery(mail, otp, token, (s, err) -> {
                    if (err != null) { fail(err); return; }
                    mode = "reset";
                    info = "Mã xác nhận hợp lệ. Hãy đặt mật khẩu mới.";
                    done();
                });
                break;
            default:
                if (!password.equals(confirm)) { fail("Mật khẩu nhập lại không khớp"); return; }
                Password.validateNew(password, (problem, e0) -> {
                    if (problem != null) { fail(problem); return; }
                    Auth.updatePassword(password, (d, err) -> {
                        if (err != null) { fail(err); return; }
                        state().toast("Đã đặt mật khẩu mới");
                        done();
                        replace(next);
                    });
                });
        }
    }

    private void resendCode() {
        if (TurnstileView.enabled() && captcha == null) { error = "Vui lòng chờ xác minh chống robot hoàn tất"; render(); return; }
        error = ""; info = "";
        setBusy(true);
        render();
        Auth.resetPasswordForEmail(email.trim(), captcha, (d, err) -> {
            setBusy(false);
            if (turnstile != null) turnstile.reset();
            if (err != null) { error = translate(err); render(); return; }
            otp = "";
            info = "Đã gửi lại mã xác nhận mới. Mã cũ không còn dùng được.";
            render();
        });
    }

    /** Google refuses sign-in inside embedded WebViews: the system browser opens and comes back via deep link. */
    private void google() {
        MainActivity.oauthNext = next;
        U2.openTab(act, Auth.googleUrl(Config.AUTH_CALLBACK));
    }

    static String translate(String msg) {
        if (msg == null) return "";
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("invalid login credentials")) return "Email hoặc mật khẩu không đúng";
        if (m.contains("email not confirmed")) return "Email chưa được xác nhận. Vui lòng kiểm tra hộp thư.";
        if (m.contains("already registered")) return "Email này đã được đăng ký";
        if (m.contains("provider is not enabled")) return "Đăng nhập Google chưa được bật trong Supabase (xem SUPABASE_SETUP.md)";
        if (m.contains("rate limit") || m.contains("only request this after")) return "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút";
        if (m.contains("captcha")) return "Xác minh chống robot thất bại, vui lòng thử lại";
        if (m.contains("token has expired or is invalid") || m.matches("(?s).*otp.*(expired|invalid).*")) return "Mã xác nhận không đúng hoặc đã hết hạn";
        if (m.contains("should be different from the old password")) return "Mật khẩu mới phải khác mật khẩu cũ";
        if (m.matches("(?s).*error sending (confirmation|recovery|magic link).*")) return "Không gửi được email tới địa chỉ này. Vui lòng kiểm tra lại email hoặc thử email khác.";
        return msg;
    }

    /** The multicolour Google "G". */
    private android.graphics.drawable.Drawable googleIcon() {
        int s = U.dp(act, 20);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        float k = s / 48f;
        c.scale(k, k);
        String[][] parts = {
                {"#FFC107", "M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"},
                {"#FF3D00", "M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"},
                {"#4CAF50", "M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"},
                {"#1976D2", "M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"},
        };
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        for (String[] part : parts) {
            p.setColor(android.graphics.Color.parseColor(part[0]));
            c.drawPath(androidx.core.graphics.PathParser.createPathFromPathData(part[1]), p);
        }
        return new android.graphics.drawable.BitmapDrawable(act.getResources(), b);
    }

    @SuppressWarnings("unused")
    private static FrameLayout unused() { return null; }
}
