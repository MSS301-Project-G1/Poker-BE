# auth-service

Phase 2: đăng ký email/mật khẩu/tên hiển thị, xác nhận mật khẩu tại BE, OTP đăng ký,
gửi lại OTP qua SMTP và event `account.registered` qua RabbitMQ với outbox.
Port mặc định `8090`; `identity-service` giữ nguyên. Chưa triển khai login/refresh/logout,
JWT/cookie, gateway nghiệp vụ, profile API hoặc FE.
Tài liệu local trong `docs/` đang gitignore: [API reference](../docs/auth/api-reference.md),
[review phase 2](../docs/auth/phase-2.md), [kế hoạch](../docs/auth/implementation-plan.md).

## Chạy auth riêng bằng Docker

Từ root repo, chỉ khởi động hạ tầng/auth riêng:

```bash
docker compose -f auth-service/compose.dev.yml --profile app up --build -d
curl http://localhost:8090/actuator/health
```

PostgreSQL ở `127.0.0.1:54330`, DB `auth_db`, user `poker`, password local
`poker_dev`. Có thể đổi bằng `AUTH_DB_USERNAME`/`AUTH_DB_PASSWORD`.
Mailpit UI: `http://localhost:8026`, SMTP: `localhost:1026`.
RabbitMQ: `localhost:5673`, UI `http://localhost:15673`, user/password local `poker`/`poker_dev`.
Compose này có project/volume riêng `poker-auth-dev`, không dùng DB của service khác.

## Chạy Java trên máy

```bash
docker compose -f auth-service/compose.dev.yml up -d postgres mailpit rabbitmq
mvn -B -pl auth-service -am install -DskipTests
mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local
```

Profile `local` dùng password mặc định của Compose dev. Ngoài local phải cung cấp
`AUTH_DB_PASSWORD` (hoặc `SPRING_DATASOURCE_PASSWORD`) và `AUTH_RABBIT_PASSWORD`.
`AUTH_DB_URL` và
`AUTH_DB_USERNAME` đổi kết nối; mặc định URL/user như trên. Spring Boot không tự đọc
file `.env`: export biến vào shell, cấu hình trong IDE hoặc dùng Compose `--env-file .env`.
Giá trị trong `.env.example` chỉ phục vụ local, không dùng làm secret triển khai.

Root `docker-compose.yml` cũng có PostgreSQL riêng `auth-postgres`, không publish
port DB. Khi dùng root Compose, app kết nối tên service này trong mạng Docker;
không dùng URL localhost dành cho Java trên máy. Volume root và volume Compose dev
là hai database khác nhau. Chọn một cách chạy app để tránh trùng port 8090.

Flyway áp dụng V1, V2 và V3 khi khởi động.
V2 đổi Profile sang khóa chính `account_id` lấy từ Account, thêm field tài khoản/hồ sơ;
không sửa V1 đã commit. Nếu dữ liệu cũ có profile đã xóa nhưng Account còn hoạt động,
V2 dừng để review dữ liệu thay vì tự làm profile đó xuất hiện lại.
V3 tạo `registration_otps` và `account_registration_events`, không đổi V1/V2.
Hibernate chỉ `validate`, không tự sửa schema. Mọi thay đổi tiếp theo dùng migration mới.

Entity chỉ mapping dữ liệu; chuẩn hóa email và validation đăng ký nằm ở service/DTO.
Soft delete nghiệp vụ sẽ làm sau. Cờ xóa ở Account; Profile không có ID/cờ xóa riêng.
`cascade = ALL` ở Profile → Account có cả REMOVE: luồng soft delete cần cập nhật
`account.deleted`, không gọi `delete(profile)` hoặc `delete(account)`.

## Thử đăng ký và OTP

```bash
curl -i http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"khanh@example.com","password":"test-password","confirmPassword":"test-password","displayName":"Khanh"}'
```

Mở Mailpit, lấy mã 6 chữ số rồi thay `012345` bằng mã vừa nhận:

```bash
curl -i http://localhost:8090/api/auth/register/verify-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"khanh@example.com","otp":"012345"}'

curl -i http://localhost:8090/api/auth/register/resend-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"khanh@example.com"}'
```

Gửi lại dùng cho tài khoản chưa xác thực, cách lần gửi trước ít nhất 60 giây.
OTP mặc định hết hạn sau 5 phút, tối đa 5 lần nhập sai/mã. Các biến `AUTH_OTP_*`
nằm trong `.env.example`. Mật khẩu tối thiểu 8 ký tự, tối đa 72 byte UTF-8;
confirmPassword phải khớp, không lưu hoặc trim mật khẩu. OTP thành công trả ACTIVE;
FE chuyển về login, response chưa cấp token/cookie.

Email chưa xác thực đăng ký lại trả 409 `AUTH_EMAIL_VERIFICATION_PENDING`, không
thay password/profile; đã xác thực/đã xóa trả `AUTH_EMAIL_IN_USE`. SMTP lỗi trả 503
và rollback thao tác. Sai OTP vẫn lưu số lần thử dù trả lỗi HTTP.

Event được lưu cùng transaction xác thực rồi job phát sang topic exchange `poker.events`
với routing key `account.registered`. Consumer tạo queue durable/binding trước khi
tích hợp. Local chưa có queue thì event giữ pending để retry, không làm OTP thất bại.
Job mặc định 5000 ms/batch, tối đa 20 rows; timeout confirm 5 giây/event.
`AUTH_EVENTS_DISPATCH_ENABLED=false` tắt job; `AUTH_EVENTS_DISPATCH_DELAY` tính bằng ms.
ACK phải có ít nhất một queue nhận; không chứng minh tất cả consumer đã sẵn sàng.
Consumer chống trùng bằng `eventId`: crash sau ACK/trước DB commit có thể phát lại.
Không gọi ví/cấp quà trong phase 2.

## Kiểm tra

```bash
mvn -B -pl auth-service -am verify
docker compose -f auth-service/compose.dev.yml --profile app config --quiet
docker compose config --quiet
```

Test auth dùng PostgreSQL 17 thật qua Testcontainers; cần Docker daemon đang chạy
và user chạy Maven có quyền truy cập Docker. Không cần cài/chạy PostgreSQL trên máy,
không cần bật Compose dev và không dùng H2 trong module auth.

`PostgresTestConfiguration` tạo DB `auth_test` trong container riêng, port được Docker
cấp ngẫu nhiên, không mount volume dữ liệu. Spring Boot `@ServiceConnection` cấp URL,
user/password cho datasource và Flyway, không dùng kết nối DB local hay `.env`.
Container dùng chung trong test context và được dọn khi kết thúc; thiếu Docker thì
test báo lỗi, không tự bỏ qua hoặc chuyển sang database khác.

Test phase 2 bổ sung SMTP thật vào Mailpit và AMQP thật qua RabbitMQ, port ngẫu nhiên,
không volume. Test verify/resend điều khiển đồng hồ để kiểm tra đúng ranh giới hạn;
kiểm tra đồng thời, rollback SMTP và event retry. Không cần bật Compose dev cho test.
App dev/Compose, Docker build và luồng FE/gateway chưa được kiểm tra trong đợt này.
