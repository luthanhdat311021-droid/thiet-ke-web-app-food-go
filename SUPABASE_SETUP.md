# Hướng dẫn kết nối FoodGo với Supabase

## 1. Tạo project Supabase

1. Vào https://supabase.com, đăng nhập rồi bấm **New project**.
2. Đặt tên (ví dụ `foodgo`), đặt mật khẩu database và chọn region **Southeast Asia (Singapore)** cho nhanh.
3. Đợi khoảng 1–2 phút để project khởi tạo.

## 2. Tạo bảng và dữ liệu mẫu

> Mọi bảng của FoodGo đều có tiền tố `fg_` (ví dụ `fg_foods`, `fg_orders`), nên có thể dùng chung một project với app khác mà không đụng bảng của app đó. Tài khoản đăng nhập (Auth) thì dùng chung.

1. Trong project, mở **SQL Editor** rồi bấm **New query**.
2. Dán toàn bộ nội dung file [`supabase/schema.sql`](supabase/schema.sql) và bấm **Run**.
3. Tạo một query mới, dán nội dung [`supabase/seed.sql`](supabase/seed.sql) và bấm **Run**. File này nạp 9 danh mục, 6 nhà hàng và 16 món ăn mẫu. Chỉ chạy **một lần**.
4. Lần lượt chạy các file trong [`supabase/migrations/`](supabase/migrations) theo thứ tự số: `002_maps.sql` (bản đồ), `003_momo.sql` (MoMo), `004_tdmu_restaurants.sql` (đặt quán quanh Trường ĐH Thủ Dầu Một), `005_single_store.sql` (chuyển sang mô hình **một quán duy nhất** FoodGo, gộp toàn bộ món).

## 3. Lấy key và tạo `.env.local`

1. Vào **Project Settings → API** (hoặc **API Keys**).
2. Sao chép file `.env.example` thành `.env.local` ở thư mục gốc dự án.
3. Điền các giá trị sau:
   - `NEXT_PUBLIC_SUPABASE_URL`: Project URL
   - `NEXT_PUBLIC_SUPABASE_ANON_KEY`: khóa `anon` / `publishable`
   - `SUPABASE_SERVICE_ROLE_KEY`: khóa `service_role` / `secret`. Khóa này **chỉ dùng cho webhook** và tuyệt đối không chia sẻ.
4. Khởi động lại server bằng `pnpm dev`.

## 4. Cấu hình đăng nhập

**Authentication → URL Configuration**
- **Site URL**: `http://localhost:3000` (khi deploy thì đổi thành domain thật)
- **Redirect URLs**: thêm `http://localhost:3000/**` (và `https://<domain>/**` khi deploy)

**Email + mật khẩu** đã được bật sẵn. Mặc định Supabase yêu cầu xác nhận email. Nếu muốn tắt khi thử nghiệm, vào **Authentication → Sign In / Providers → Email** và tắt *Confirm email*.

**Quên mật khẩu (mã OTP 6 số)**: app gửi mã 6 số qua email, khách nhập mã và mật khẩu mới ngay trong app.
- Vào **Authentication → Email Templates → Reset password**, dán nội dung file `supabase/email-templates/reset-password.html` (mẫu dùng `{{ .Token }}` thay cho liên kết).
- Ở **Authentication → Sign In / Providers → Email**, giữ *Email OTP Length* = `6` (*Email OTP Expiration* mặc định 3600 giây = 1 giờ).

**Google**
1. Vào https://console.cloud.google.com, chọn **APIs & Services → Credentials → Create credentials → OAuth client ID** (loại *Web application*).
2. Ở mục **Authorized redirect URIs**, thêm: `https://<project-ref>.supabase.co/auth/v1/callback`. Bạn có thể sao chép URL này trong Supabase, ở mục Google provider.
3. Sao chép **Client ID** và **Client secret**, rồi dán vào Supabase ở **Authentication → Sign In / Providers → Google**. Bật provider và bấm **Save**.

## 5. Tạo tài khoản admin

1. Đăng ký một tài khoản trên trang web như bình thường.
2. Vào **SQL Editor** và chạy lệnh sau (thay email bằng email của bạn):

```sql
update public.fg_profiles set role = 'admin'
where id = (select id from auth.users where email = 'email-cua-ban@example.com');
```

3. Tải lại trang web. Menu tài khoản sẽ có thêm **Trang quản trị** (`/admin`). Từ đây bạn có thể cấp quyền admin cho người khác ngay trong tab **Người dùng**.

## 6. Thanh toán bằng mã QR

### Mã QR tự sinh (VietQR)
Điền `NEXT_PUBLIC_VIETQR_BANK_ID`, `NEXT_PUBLIC_VIETQR_ACCOUNT_NO` và `NEXT_PUBLIC_VIETQR_ACCOUNT_NAME` trong `.env.local`.

Mỗi đơn chọn "Chuyển khoản QR" sẽ có mã QR riêng, đã điền sẵn **đúng số tiền** và **nội dung là mã đơn** (ví dụ `FG1A2B3C4D`). Khách chỉ cần quét mã bằng app ngân hàng.

### Xác nhận đã thanh toán
Có 2 cách:

- **Thủ công**: admin kiểm tra tài khoản ngân hàng rồi bấm **"Đã nhận tiền & xác nhận"** trong **/admin → Đơn hàng**.
- **Tự động (khuyến nghị)** qua [SePay](https://sepay.vn) – chạy migration [`010_sepay.sql`](supabase/migrations/010_sepay.sql) trước:
  1. Đăng ký SePay, liên kết tài khoản **MB Bank 0819883208** (ngân hàng phải có trong danh sách SePay hỗ trợ).
  2. Lấy khóa API: Supabase → SQL Editor → `select value from public.fg_secrets where key = 'sepay_api_key';`
  3. SePay → **Webhooks → + Thêm webhook**:
     - Sự kiện: **Có tiền vào**; URL: `https://thiet-ke-web-app-food-go.vercel.app/api/payment/webhook`
     - Tài khoản: MB 0819883208; Bảo mật: **API Key** = khóa ở bước 2
  4. Bấm **Gửi thử** trong trang chi tiết webhook: phản hồi phải là `{"success": true, ...}`.

  Khi khách chuyển khoản, hàm `fg_sepay_confirm()` kiểm tra khóa API **ngay trong database**, chống xử lý trùng theo `id` giao dịch (bảng `fg_bank_transactions`), tìm mã đơn `FGxxxxxxxx` trong nội dung, kiểm tra đủ tiền rồi đánh dấu **Đã thanh toán** + **xác nhận đơn**. Trang đơn của khách tự cập nhật. Không cần `SUPABASE_SERVICE_ROLE_KEY`.

> Webhook cần một URL công khai. Khi chạy ở localhost, bạn cần deploy (ví dụ lên Vercel) hoặc dùng tunnel như `ngrok` / `cloudflared` để SePay gọi được tới máy bạn.

## 7. Thanh toán ví MoMo

Chạy thêm [`supabase/migrations/003_momo.sql`](supabase/migrations/003_momo.sql) (sau `002_maps.sql`).

- Mặc định dùng **khóa SANDBOX công khai** của MoMo (`test-payment.momo.vn`), **không trừ tiền thật**. Trang thanh toán sandbox cho thanh toán bằng thẻ ATM thử nghiệm trong [tài liệu MoMo](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions) hoặc app MoMo bản UAT.
- Luồng xử lý: đặt hàng → `/api/payment/momo/create` ký yêu cầu (HMAC-SHA256) → khách thanh toán trên trang MoMo → MoMo gọi IPN `/api/payment/momo/ipn` và đưa khách về `/orders/<id>` → hàm `fg_momo_confirm()` **kiểm tra chữ ký ngay trong database** rồi đánh dấu đã thanh toán.

**Chuyển sang nhận tiền thật** (cần tài khoản doanh nghiệp tại https://business.momo.vn):
1. Vercel → Settings → Environment Variables: thêm `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` và `MOMO_ENDPOINT=https://payment.momo.vn/v2/gateway/api/create`, rồi deploy lại.
2. Supabase → SQL Editor:
   ```sql
   update public.fg_secrets set value = '<ACCESS_KEY thật>' where key = 'momo_access_key';
   update public.fg_secrets set value = '<SECRET_KEY thật>' where key = 'momo_secret_key';
   ```
   (Hai khóa trong database phải trùng với khóa trên Vercel.)

## 8. Deploy lên Vercel (tùy chọn)

1. Đẩy code lên GitHub rồi import vào https://vercel.com.
2. Vào **Settings → Environment Variables** và thêm tất cả biến trong `.env.local`.
3. Cập nhật **Site URL** và **Redirect URLs** trong Supabase thành domain Vercel.
