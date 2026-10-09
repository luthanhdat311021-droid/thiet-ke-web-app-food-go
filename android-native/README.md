# FoodGo Native (Java)

Bản Android viết lại bằng **Java + Android View** (không dùng WebView cho giao diện), để so sánh với bản
WebView/Capacitor trong thư mục `../android`. Hai bản có `applicationId` khác nhau nên **cài song song** được:

| | Bản WebView (`android/`) | Bản native (`android-native/`) |
|---|---|---|
| Tên app | FoodGo | FoodGo Native |
| applicationId | `com.foodgo.app` | `com.foodgo.app.nativeapp` |
| Giao diện | Trang web Vercel trong WebView | Màn hình Java dựng sẵn trong app |
| Cập nhật giao diện | Deploy web là xong | Phải build lại APK |

Backend giữ nguyên 100%: cùng Supabase (database, RLS, RPC, realtime, storage), cùng các API trên Vercel
(`/api/geo/*`, `/api/payment/momo/*`, webhook SePay). Không cần sửa web hay Supabase.

## Mở và chạy
1. Android Studio → **Open** → chọn thư mục **`android-native`**.
2. Đợi Gradle Sync xong → chọn máy ảo / điện thoại → **Run ▶**.

## Các phần vẫn dùng WebView (không thể thay)
- **Captcha Cloudflare Turnstile** khi đăng nhập/đăng ký/quên mật khẩu: Supabase đang bật captcha và Cloudflare
  chỉ có widget dạng web, nên chạy trong một WebView nhỏ (thường vô hình).
- **Trang thanh toán MoMo**: là trang web của MoMo, mở trong app giống bản cũ; khi MoMo chuyển về
  `…/orders/<id>?resultCode=…` app bắt lại và mở màn hình đơn hàng native.

## Đăng nhập Google
Mở trình duyệt hệ thống rồi quay về app qua `com.foodgo.app://native-auth-callback`
(khớp mục `com.foodgo.app://**` đã có trong Redirect URLs của Supabase). Nếu máy cài cả hai bản,
Android có thể hỏi chọn app để mở — chọn **FoodGo Native**.

## Cấu trúc mã
- `core/` – client Supabase viết tay: `Db` (PostgREST), `Auth` (GoTrue), `Realtime` (websocket), `Net`, `Json`
- `state/` – `AppState` (giỏ hàng, yêu thích, thông báo, đăng nhập), `LocationState`, `Store`, `Recommend`
- `screens/` – các trang khách hàng (tương ứng `app/*/page.tsx`), `MainActivity` giữ header + menu dưới
- `admin/` – Trang quản trị và các thành phần dùng chung với Kênh nhà hàng
- `shop/` – Kênh nhà hàng (đăng ký quán, phí duy trì)
- `pay/` – MoMo
- Cấu hình công khai (URL Supabase, VietQR, Turnstile) ở `Config.java`
