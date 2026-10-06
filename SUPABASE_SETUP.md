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

- **Thủ công**: admin vào **/admin → Đơn hàng** và bấm **Xác nhận đã thanh toán**.
- **Tự động (khuyến nghị)** qua [SePay](https://sepay.vn), dịch vụ đọc biến động số dư và gửi webhook:
  1. Đăng ký SePay và liên kết tài khoản ngân hàng nhận tiền.
  2. Vào **Webhooks → Thêm webhook**:
     - URL: `https://<domain-cua-ban>/api/payment/webhook`
     - Kiểu xác thực: **API Key**, giá trị trùng với `SEPAY_WEBHOOK_KEY` trong `.env.local`
  3. Khi khách chuyển khoản, SePay gọi webhook. Hệ thống tìm mã đơn trong nội dung chuyển khoản, kiểm tra số tiền, rồi đánh dấu **Đã thanh toán** và tự **xác nhận đơn**. Trang đơn hàng của khách cập nhật ngay, không cần tải lại.

> Webhook cần một URL công khai. Khi chạy ở localhost, bạn cần deploy (ví dụ lên Vercel) hoặc dùng tunnel như `ngrok` / `cloudflared` để SePay gọi được tới máy bạn.

## 7. Deploy lên Vercel (tùy chọn)

1. Đẩy code lên GitHub rồi import vào https://vercel.com.
2. Vào **Settings → Environment Variables** và thêm tất cả biến trong `.env.local`.
3. Cập nhật **Site URL** và **Redirect URLs** trong Supabase thành domain Vercel.
