# Web phase 1 - Auth và Catalog

Mục tiêu của phase này là để frontend web có thể dựng trang đăng ký, đăng nhập,
profile cơ bản, danh sách và chi tiết sản phẩm qua một base URL duy nhất:

```text
http://localhost:8080/api
```

## Chuẩn bị Supabase

Chạy các file SQL theo thứ tự đã ghi trong `README-SUPABASE-SQL-PHASES.md`.
Riêng web phase 1 bắt buộc xác nhận các bảng sau đã tồn tại:

```text
customers
staff_members
user_accounts
customer_memberships
auth_audit_logs
products
product_images
outbox_events
processed_events
```

Các file bổ sung:

```text
db/supabase-user-accounts.sql
db/supabase-product-images.sql
db/supabase-outbox-events.sql
```

Điền `.env` từ `.env.example`:

```text
SUPABASE_URL=https://PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
SUPABASE_JWT_ISSUER=https://PROJECT_REF.supabase.co/auth/v1
SUPABASE_JWKS_URI=https://PROJECT_REF.supabase.co/auth/v1/.well-known/jwks.json
AUTH_EMAIL_REDIRECT_URL=http://localhost:3000/auth/confirm
AUTH_REFRESH_COOKIE_SECURE=false
SUPABASE_PUBLIC_STORAGE_URL=https://PROJECT_REF.supabase.co/storage/v1/object/public
```

Production phải dùng HTTPS và đặt `AUTH_REFRESH_COOKIE_SECURE=true`.
Không đưa database password, secret key hoặc `service_role` key vào frontend.

## Role và JWT custom claim

Ứng dụng dùng đúng ba role:

```text
ADMIN
SALES
CUSTOMER
```

`public.user_accounts.role` là nguồn dữ liệu chuẩn. Đăng ký công khai luôn tạo
`CUSTOMER`; client không được tự gửi role. Supabase Custom Access Token Hook
`public.custom_access_token_hook` đưa role này vào claim `user_role` của JWT.

Sau khi chạy lại `db/supabase-user-accounts.sql`, vào:

```text
Supabase Dashboard > Authentication > Hooks > Custom Access Token
```

Chọn function `public.custom_access_token_hook`. Người dùng phải đăng nhập lại
hoặc refresh session sau khi đổi role để nhận access token có claim mới.

Luồng chuẩn:

- Khách hàng tự gọi `POST /api/auth/register`; backend luôn tạo `CUSTOMER`.
- ADMIN tạo nhân viên kinh doanh qua `POST /api/auth/admin/sales`; backend gửi
  Supabase Invite và tạo đồng thời `user_accounts(role=SALES, account_type=staff)`
  cùng `staff_members(role=sales)`.
- Client public không bao giờ được gửi hoặc tự chọn role.

Để bootstrap tài khoản ADMIN đầu tiên, cấu hình backend:

```properties
SUPABASE_SECRET_KEY=sb_secret_REPLACE_ME
AUTH_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME=Administrator
```

Khi User/CRM Service khởi động:

1. Nếu đã có ADMIN, bootstrap được bỏ qua.
2. Nếu email đã là một Auth user, tài khoản đó được promote thành ADMIN.
3. Nếu email chưa tồn tại, backend gửi Supabase Invite bằng secret key.
4. Backend tạo/cập nhật `user_accounts` và `staff_members`.

Sau khi nhận invite hoặc được promote, ADMIN phải đăng nhập/refresh lại để JWT
có claim `user_role=ADMIN`. `SUPABASE_SECRET_KEY` chỉ được đặt ở backend.

## Auth API

### Đăng ký

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password",
  "displayName": "Nguyễn Văn A"
}
```

Nếu Supabase yêu cầu xác minh email, response có
`emailConfirmationRequired=true` và chưa có access token.

### Đăng nhập

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password"
}
```

Response trả `accessToken`, `expiresIn` và `account`. Refresh token chỉ nằm
trong cookie `HttpOnly`; JavaScript không đọc được cookie này.

Frontend giữ access token trong memory và gửi:

```http
Authorization: Bearer <accessToken>
```

Không lưu refresh token trong `localStorage`.

### Refresh session

```http
POST /api/auth/refresh
Credentials: include
```

Frontend phải gọi `fetch` với `credentials: "include"`. Backend xoay refresh
token và trả access token mới.

### Quên mật khẩu

```http
POST /api/auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

API luôn trả một thông báo chung để không làm lộ email nào đã đăng ký.

### Profile

```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

### Logout

```http
POST /api/auth/logout
Authorization: Bearer <accessToken>
Credentials: include
```

### Đổi role

Chỉ tài khoản có role `ADMIN` được gọi:

```http
PATCH /api/auth/users/{userId}/role
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{
  "role": "SALES"
}
```

### ADMIN mời tài khoản SALES

```http
POST /api/auth/admin/sales
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{
  "email": "sales@example.com",
  "fullName": "Nguyễn Văn Sales",
  "displayName": "Sales A",
  "phone": "0901234567",
  "positionTitle": "Nhân viên kinh doanh"
}
```

Role không xuất hiện trong request: endpoint này luôn tạo application role
`SALES`, staff role `sales` và department `sales`.

## Catalog API

Hai API đọc sản phẩm là public:

```http
GET /api/products?page=0&size=20&query=loa&category=audio&status=active
GET /api/products/{id}
```

Response danh sách:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

URL ảnh được backend dựng từ `bucket_name` và `object_key`; frontend không tự
ghép URL và không dùng `service_role`.

API ghi yêu cầu JWT và role:

```http
POST  /api/products
PATCH /api/products/{id}/commercial-data
```

## Trình tự chạy local

```powershell
Copy-Item .env.example .env
docker compose up -d redis kafka
```

Sau đó chạy:

```text
api-gateway                   :8080
user-crm-service              :8081
catalog-inventory-service     :8082
```

Frontend chỉ gọi `http://localhost:8080/api`; không gọi thẳng cổng 8081/8082.

Nếu chạy bằng Maven, mở ba terminal tại thư mục workspace:

```powershell
backend\mvnw.cmd -f backend\pom.xml -DskipTests install
backend\mvnw.cmd -f backend\user-crm-service\pom.xml spring-boot:run
backend\mvnw.cmd -f backend\catalog-inventory-service\pom.xml spring-boot:run
backend\mvnw.cmd -f backend\api-gateway\pom.xml spring-boot:run
```

Lệnh `install` chỉ cần chạy lại khi thay đổi module dùng chung hoặc dependency
giữa các module.
