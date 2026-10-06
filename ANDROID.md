# Chạy FoodGo trên Android Studio

App Android là một vỏ native (Capacitor 8) mở trang web FoodGo đã deploy trên Vercel
(`https://thiet-ke-web-app-food-go.vercel.app`). Vì vậy:

- Sửa giao diện hoặc chức năng web, chạy `vercel deploy --prod` → app tự cập nhật, **không cần build lại APK**.
- Chỉ cần build lại khi đổi phần native: icon, splash, quyền, `capacitor.config.ts`, plugin.

## Mở và chạy

1. Mở Android Studio → **Open** → chọn thư mục **`android`** trong dự án (không chọn thư mục gốc).
   Hoặc từ thư mục gốc chạy: `pnpm android:open`
2. Đợi **Gradle Sync** xong (lần đầu mất vài phút để tải thư viện).
3. Chọn máy ảo **Pixel 10 Pro XL** (hoặc cắm điện thoại thật đã bật *USB debugging*) → bấm **Run ▶**.

### Nếu Gradle báo lỗi Java
Project đã dùng **Gradle 9.1** để chạy được với JDK 25 đi kèm Android Studio.
Nếu vẫn lỗi: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** → chọn
**`jbr` (JetBrains Runtime)** của Android Studio.

## Sau khi sửa phần native

```bash
pnpm android:sync
```
rồi bấm Run lại trong Android Studio. Lệnh này chép `capacitor.config.ts` và các plugin vào project Android.

## Đã tối ưu sẵn

| Hạng mục | Chi tiết |
|---|---|
| Nút Back Android | Đóng giỏ hàng → quay lại trang trước → thu nhỏ app (không thoát đột ngột) |
| Thanh trạng thái | Nền trắng, chữ tối, không đè lên nội dung |
| Tai thỏ / thanh cử chỉ | Header và thanh menu dưới tự chừa khoảng an toàn |
| Cảm giác chạm | Bỏ viền xanh khi chạm, không bôi đen chữ ngoài ô nhập liệu, không kéo giãn khi cuộn |
| Chế độ tối | Khóa giao diện sáng, WebView không tự đảo màu |
| Mất mạng | Hiện trang "Không có kết nối mạng" kèm nút Thử lại (`mobile-shell/offline.html`) |
| Icon & Splash | Icon cam chữ F (cả adaptive icon), màn hình khởi động màu cam |
| Đăng nhập Google | Mở trình duyệt hệ thống rồi quay về app qua `com.foodgo.app://auth-callback` (Google chặn đăng nhập trong WebView) |
| Gradle | Tăng RAM, bật build song song và cache |

### Để đăng nhập Google chạy trong app
Thêm `com.foodgo.app://**` vào Supabase → **Authentication → URL Configuration → Redirect URLs**.

## Tạo file APK để cài lên điện thoại

- APK thử nghiệm: Android Studio → **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
  → file ở `android/app/build/outputs/apk/debug/app-debug.apk`.
- Bản phát hành (Google Play): **Build → Generate Signed App Bundle / APK** và tạo keystore riêng
  (giữ keystore cẩn thận, mất là không cập nhật app được nữa).
