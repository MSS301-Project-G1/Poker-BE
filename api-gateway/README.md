# api-gateway

Phase 4A: auth qua gateway tại port `8080`, JWT RS256/JWKS từ cookie HttpOnly,
CORS cho FE và kiểm tra quyền/CSRF. Auth vẫn sở hữu đăng ký, OTP, phiên và cookie;
gateway không truy cập database auth hoặc ký JWT.

## Chạy gateway cùng auth

Chạy từ root repo. Điền SMTP trong `.env` như [README auth](../auth-service/README.md).
Nếu bộ auth cũ còn container PostgreSQL tên `postgres`, thực hiện
[chuyển đổi DB](../auth-service/README.md#chuyển-từ-compose-dev-cũ) trước lần chạy đầu.

```bash
# Build và chạy gateway + auth + PostgreSQL + RabbitMQ.
docker compose --env-file .env \
  -f docker/compose.infra.yml \
  -f auth-service/compose.yml \
  -f auth-service/compose.dev.yml \
  -f api-gateway/compose.yml \
  -f api-gateway/compose.dev.yml \
  up -d --build api-gateway

# Không build lại.
docker compose --env-file .env \
  -f docker/compose.infra.yml \
  -f auth-service/compose.yml \
  -f auth-service/compose.dev.yml \
  -f api-gateway/compose.yml \
  -f api-gateway/compose.dev.yml \
  up -d api-gateway
```

`api-gateway/compose.dev.yml` chỉ override port gateway về localhost và dependency
về `auth-service`, tránh khởi động toàn bộ service của nhóm. Lệnh trên dùng thêm
auth dev override để giữ broker/volume auth cũ, mặc định `poker`/`poker_dev`, đồng thời
mở port DB `54330`. Nếu bỏ auth dev override, broker lấy nguyên cấu hình infra,
auth mặc định dùng `guest`/`guest`; bỏ/comment credential dev trong `.env` khi đó.
Các biến credential nếu đặt trong `.env` luôn ưu tiên hơn fallback của Compose.
Project vẫn là `poker-auth-dev`, dùng lại volume auth hiện có. Lệnh `up` không xóa
volume. File root `docker-compose.yml` và `docker/compose.infra.yml` không bị chỉnh.
Gateway chờ container auth được start, không chờ app auth hoàn tất startup; đợi
health UP trước khi test.

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8090/actuator/health
```

Swagger qua gateway: **http://localhost:8080/swagger-ui.html**.
OpenAPI: `http://localhost:8080/v3/api-docs`.
URL tài liệu và server API của Swagger là tương đối để API vẫn đi qua origin đang mở.

Nếu auth đã chạy Docker và muốn chạy gateway bằng Java trên máy:

```bash
mvn -B -pl api-gateway -am install -DskipTests
mvn -pl api-gateway spring-boot:run
```

Mặc định gateway trên host gọi auth `http://localhost:8090`. Spring không tự đọc
`.env`; export biến hoặc cấu hình IDE khi dùng giá trị khác mặc định.

## Cấu hình

| Biến | Mặc định khi chạy Java trên host | Ý nghĩa |
|---|---|---|
| `SERVER_PORT` | `8080` | Port server; Docker giữ port nội bộ 8080 |
| `AUTH_SERVICE_URL` | `http://localhost:8090` | Địa chỉ HTTP của auth; Compose dùng `http://auth-service:8090` |
| `GATEWAY_JWK_SET_URI` | `${AUTH_SERVICE_URL}/.well-known/jwks.json` | Nơi gateway tải public key, không cần OIDC discovery |
| `JWT_ISSUER` | `http://localhost:8090` | Phải khớp claim `iss` auth phát hành; không đổi thành URL Docker chỉ vì JWKS ở mạng Docker |
| `JWT_AUDIENCE` | `poker` | Phải khớp audience của access JWT |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8080,http://localhost:8090` | Danh sách origin chính xác, không wildcard; đồng bộ origin với auth khi thay đổi |
| `GAME_SERVICE_KEY` | Rỗng | Khóa gateway gửi riêng cho route game, phải khớp cấu hình phía game; thiếu khóa thì trả 503 |

JWKS được lấy khi xác thực JWT, không bắt auth phải sẵn sàng lúc gateway startup.
Nimbus quản lý cache public key. Gateway chỉ nhận RS256 và kiểm tra issuer,
audience, UUID `sub`, role USER/ADMIN, `exp`, `iat`, `nbf`, `jti`.
Không blacklist access token sau logout, đúng hợp đồng phase 3.
HTTP proxy có connect timeout 3 giây, response timeout 10 giây;
client tải JWKS có connect timeout 3 giây, response timeout 5 giây.

## Luồng auth qua gateway

FE gọi `http://localhost:8080`, dùng `credentials: "include"` / `withCredentials: true`.
HTTP xác thực bằng cookie `accessToken`; không truyền JWT vào Bearer Authorize.
Refresh cookie vẫn có Path `/api/auth`, không đổi path khi proxy.

| Method/path public | Xử lý |
|---|---|
| GET `/api/auth/csrf` | Auth cấp cookie/token CSRF |
| POST `/api/auth/register`, `/api/auth/register/verify-otp`, `/api/auth/register/resend-otp` | Auth xử lý đăng ký/OTP; không yêu cầu access token |
| POST `/api/auth/password/forgot`, `/api/auth/password/reset` | Public, không yêu cầu access token/CSRF; auth kiểm tra OTP khôi phục |
| POST `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | Không yêu cầu access token; auth kiểm tra CSRF và nghiệp vụ phiên |
| GET `/.well-known/jwks.json` | Public key từ auth |
| GET `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**` | Swagger/OpenAPI auth |
| GET `/actuator/health`, `/actuator/health/**` | Health của gateway |

GET `/api/auth/me`, `/api/auth/ws-token` và POST `/api/auth/password/change` yêu cầu access cookie hợp lệ.
Đổi mật khẩu còn cần CSRF do auth kiểm tra; gateway giữ nguyên body/header và cookie xóa khi thành công.
Access cookie hết hạn không ngăn login/refresh/logout; gateway bỏ qua xác thực
access trên các endpoint public được liệt kê theo đúng method/path.
POST `/api/checkin` cần access cookie + CSRF; GET `/api/checkin/calendar?month=yyyy-MM` cần access cookie.
Gateway route về auth, giữ nguyên HTTP status và envelope, không tự cấp thưởng.
Các `/api/**` khác cần đăng nhập; `/api/admin/**` cần ADMIN. `/internal/**` luôn bị
chặn. Không public cả prefix `/api/auth/**`.

Auth là nơi cấp/kiểm tra CSRF cho API auth. Gateway chuyển nguyên cookie và header
`X-XSRF-TOKEN`; không tạo CSRF cookie thứ hai. Với POST/PUT/PATCH/DELETE ngoài auth,
gateway kiểm tra header khớp cookie `XSRF-TOKEN` hiện có sau khi kiểm tra quyền.
CORS preflight được xử lý ở gateway; Origin lạ hoặc `Sec-Fetch-Site: cross-site`
không kèm Origin hợp lệ bị từ chối. Response không lặp header CORS từ auth.

Gateway xóa `X-User-Id`, `X-User-Role`, `X-Game-Service-Key`, `X-Dev-User`,
`Authorization` và các header forwarded do client gửi. Danh tính/role được lấy từ
JWT đã xác minh; game service key chỉ gắn cho route game. Mỗi request có
`X-Request-Id` mới, chuyển xuống service và trả về FE.

## Test bằng Swagger

1. Mở Swagger **port 8080**. Đăng ký và xác thực OTP nếu chưa có tài khoản.
2. GET `/api/auth/csrf`, copy `result.token` vào Authorize → csrfToken.
3. Login, kiểm tra cookie HttpOnly; GET `/me` phải trả đúng tài khoản.
4. Refresh rồi GET `/me`; cả access/refresh cookie được đổi.
5. Logout rồi GET `/me` phải trả 401. Không cần lấy lại CSRF trước mỗi request.

Error do gateway sinh có `code`, `message`, `timestamp`, `requestId`.
Error/body/status/cookie từ auth được chuyển tiếp, giữ các mã `AUTH_*`.

| HTTP | Mã gateway |
|---|---|
| 401 | `GATEWAY_UNAUTHENTICATED` |
| 403 | `GATEWAY_FORBIDDEN`, `GATEWAY_ORIGIN_NOT_ALLOWED`, `GATEWAY_CSRF_INVALID` |
| 404 | `GATEWAY_ROUTE_NOT_FOUND` |
| 503 | `GATEWAY_UPSTREAM_UNAVAILABLE` |
| 504 | `GATEWAY_UPSTREAM_TIMEOUT` |
| 500 | `GATEWAY_REQUEST_FAILED` |

## Kiểm tra và phạm vi

```bash
mvn -B -pl api-gateway,auth-service -am verify
```

Gateway test dùng HTTP server/JWKS test trên port ngẫu nhiên và JWT RSA ký thật;
kiểm tra route/body/query, cookie/CSRF, quyền, giả header, CORS, lỗi kết nối/timeout.
Auth test dùng PostgreSQL/Mailpit/RabbitMQ tạm qua Testcontainers, không dùng DB dev.

Route auth và game giữ nguyên path controller; các route HTTP khác còn scaffold,
chưa xác nhận hợp đồng của từng module. Các prefix hồ sơ/điểm danh/báo cáo trong
route auth chỉ chuẩn bị định tuyến, không có nghĩa nghiệp vụ đó đã triển khai.
WebSocket/STOMP qua gateway, profiles batch, restrictions và thưởng lần login đầu
thuộc đợt tiếp theo. `/api/auth/ws-token` hoạt động qua gateway nhưng chưa có
luồng game STOMP thật qua gateway.

Game cần service key khớp ở cả hai phía và tự kiểm tra JWT trong STOMP như code
hiện có. Không sửa game, wallet hoặc Compose chung để triển khai phase này.
Các service HTTP khác phải có cơ chế bảo vệ ingress tương ứng trước khi tin
header danh tính; gateway không tự làm các port được publish trở thành riêng tư.
