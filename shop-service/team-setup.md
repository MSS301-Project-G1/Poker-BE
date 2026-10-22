# Chạy Auth + API Gateway sau khi pull code

Hướng dẫn này chạy **gateway, auth, PostgreSQL và RabbitMQ** để test đăng ký/đăng nhập.
Chưa phải lệnh chạy toàn bộ game hoặc toàn bộ service của nhóm.

## 1. Chuẩn bị

- Cài Docker và Docker Compose **2.24.4 trở lên**, bật Docker trước khi chạy.
- Pull code mới, mở terminal tại **root `Poker-BE`** (nơi có `.env.example`).
- Không cần cài Java, Maven hoặc PostgreSQL trên máy nếu chạy bằng Docker.

## 2. Tạo và điền `.env`

Nếu chưa có `.env`, copy từ `.env.example`:

```bash
# Linux/macOS
cp -n .env.example .env
```

```powershell
# Windows PowerShell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Mở `.env`, sửa tài khoản gửi email OTP:

```dotenv
AUTH_MAIL_HOST=smtp.gmail.com
AUTH_MAIL_PORT=587
AUTH_MAIL_FROM=your-email@gmail.com
AUTH_MAIL_USERNAME=your-email@gmail.com
AUTH_MAIL_PASSWORD=your-google-app-password
AUTH_MAIL_SMTP_AUTH=true
AUTH_MAIL_STARTTLS=true
```

`AUTH_MAIL_FROM` và `AUTH_MAIL_USERNAME` dùng cùng Gmail. Password là **Google App Password**,
không phải mật khẩu đăng nhập Gmail. OTP sẽ gửi tới email nhập trong form đăng ký.
Không commit `.env` hoặc chia sẻ secret trong GitHub.

Các giá trị DB, RabbitMQ, JWT và cookie trong `.env.example` giữ nguyên để test local.
Khóa JWT được tạo tự động và lưu trong volume; không cần tự tạo/copy khóa.

## 3. Build và chạy

Chạy lệnh sau tại root repo (dùng được cả Bash và PowerShell):

```bash
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f api-gateway/compose.yml -f api-gateway/compose.dev.yml up -d --build api-gateway
```

Lệnh chỉ chạy **4 container**: gateway, auth, PostgreSQL và RabbitMQ. Lần đầu cần
thời gian tải image/build. Chờ auth khởi động xong rồi mở:

- **Swagger qua gateway:** http://localhost:8080/swagger-ui.html
- **Health auth:** http://localhost:8090/actuator/health — mong đợi `UP`.
- **Health gateway:** http://localhost:8080/actuator/health — mong đợi `UP`.
- **RabbitMQ UI:** http://localhost:15679 — mặc định local `poker` / `poker_dev`.

## 4. Test nhanh trên Swagger port 8080

1. Đăng ký bằng email nhận được mail; nhập mật khẩu và xác nhận mật khẩu giống nhau.
2. Lấy OTP trong email và gọi API xác thực OTP. Xác thực xong mới login được.
3. Gọi **GET `/api/auth/csrf`**, copy `result.token` vào **Authorize → csrfToken**.
4. Gọi **login → `/me` → refresh → logout**. Gọi lại `/me` sau logout phải trả 401.

Trình duyệt tự giữ access/refresh cookie HttpOnly; không nhập access JWT vào Authorize.
CSRF token dùng lại khi cookie còn nguyên, không cần lấy trước mỗi API.
FE local gọi gateway ở `http://localhost:8080`, gửi `credentials: "include"`
(fetch) hoặc `withCredentials: true` (Axios); FE mặc định chạy `http://localhost:5173`.

## 5. Những lần chạy sau

Không đổi code thì dùng lại lệnh ở mục 3 và **bỏ `--build`**.
Đổi code auth/gateway thì giữ `--build`. Chỉ đổi `.env` thì chạy lại bằng `up -d`
để container nhận cấu hình mới; `restart` không nạp lại `.env`.

Để dừng bộ auth/gateway này, dùng đúng các file đã chọn:

```bash
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f api-gateway/compose.yml -f api-gateway/compose.dev.yml down
```

Không thêm `-v` nếu muốn giữ tài khoản đã đăng ký và khóa JWT.

## 6. Nối service của mình qua gateway

**Chủ module gửi hợp đồng API; Khanh cấu hình gateway.** Service không cần gọi API
đăng ký với gateway. Khi thêm/sửa API, gửi Khanh:

- Tên service, port và địa chỉ chạy local/Docker; gateway phải kết nối được tới service.
- Danh sách **method + path controller thực tế**, kèm path muốn FE gọi qua gateway.
- Quyền từng API: không cần đăng nhập, cần đăng nhập, hay ADMIN.
- Endpoint chỉ dùng giữa backend (`/internal/**`), webhook hoặc WebSocket nếu có;
  ghi rõ cơ chế xác thực riêng, không tự đưa chúng vào allowlist public.

**“API cho FE gọi” không đồng nghĩa “API không cần đăng nhập”.** Ví dụ cách bàn giao:

| Method | Path FE gọi | Path ở controller | Quyền |
|---|---|---|---|
| GET | `/api/game/tables/{tableId}` | `/api/game/tables/{tableId}` | Cần đăng nhập; game kiểm tra người đó thuộc bàn |
| PUT | `/api/admin/match-settings/{mode}` | `/api/admin/match-settings/{mode}` | ADMIN |
| POST | `/api/auth/login` | `/api/auth/login` | Không cần access token; auth kiểm tra CSRF |

Có Swagger/OpenAPI thì gửi kèm; khi đổi quyền hoặc thêm prefix mới cần báo lại.
API thêm dưới một prefix đã route sẵn, giữ nguyên chính sách quyền, thường không
cần thêm route cho từng endpoint; vẫn cập nhật danh sách bàn giao.

**Phần Khanh làm:** cấu hình route/URL trong gateway, giữ hoặc rewrite path cho
khớp controller, thêm allowlist/quyền nếu cần và kiểm tra request qua gateway.
Gateway hiện mặc định yêu cầu đăng nhập cho `/api/**`, ADMIN cho `/api/admin/**`;
endpoint không cần đăng nhập phải được khai báo đúng method/path. Một số route
còn scaffold `StripPrefix=2`, nên cần đối chiếu controller trước khi tích hợp.

**Phần chủ module làm:** chạy service/dependency của mình, xử lý nghiệp vụ và
quyền sở hữu dữ liệu. Khi nhận danh tính qua `X-User-Id`/`X-User-Role`, service phải
có cơ chế chỉ tin request từ gateway, ví dụ service key như game đang dùng;
thống nhất cách bảo vệ ingress với Khanh trước khi tin các header này.
Không cần lấy private key JWT của auth. Với game, service key phải khớp ở cả
gateway và game; JWT/JWKS cho STOMP là phần cấu hình riêng.

**Phần FE làm:** gọi URL gateway kèm credentials; request ghi dữ liệu gửi header
CSRF. Không tự gắn `X-User-Id`/`X-User-Role`. API nội bộ gọi trực tiếp giữa backend,
không đi qua gateway. WebSocket cần bàn giao thêm endpoint và hợp đồng CONNECT.

Chạy riêng bộ auth/gateway ở mục 3 chưa bật service của các bạn. Muốn test tích hợp,
phải bật thêm service liên quan và cho gateway kết nối được tới địa chỉ đó.

## Nếu gặp lỗi

- **Port bị chiếm:** kiểm tra `docker ps`; không chạy thêm bộ root Compose trên cùng port.
- **Không gửi được OTP:** kiểm tra Gmail/App Password, email gửi và hộp thư Spam.
- **403 CSRF:** lấy lại `/api/auth/csrf`, cập nhật Authorize, dùng nhất quán `localhost`.
- **Đã từng chạy auth dev cũ với service DB tên `postgres`:** đọc
  [mục chuyển đổi](../../auth-service/README.md#chuyển-từ-compose-dev-cũ) trước lần chạy đầu.

Các service khác chưa được bật bởi lệnh này. Khi nối module của mình qua gateway,
cần thống nhất route, địa chỉ service và cách nhận danh tính với Khanh; phần
restrictions/WebSocket và tích hợp đầy đủ từng module còn thuộc phase tiếp theo.
