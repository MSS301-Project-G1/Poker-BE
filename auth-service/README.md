# auth-service

Phase 2: đăng ký email/mật khẩu/tên hiển thị, xác nhận mật khẩu tại BE, OTP đăng ký,
gửi lại OTP qua SMTP và event `account.registered` qua RabbitMQ với outbox.
Port mặc định `8090`; `identity-service` giữ nguyên. Chưa triển khai login/refresh/logout,
JWT/cookie, gateway nghiệp vụ, profile API hoặc FE.
Tài liệu local trong `docs/` đang gitignore: [API reference](../docs/auth/api-reference.md),
[review phase 2](../docs/auth/phase-2.md), [kế hoạch](../docs/auth/implementation-plan.md).

## Chạy auth riêng bằng Docker

Từ root repo, điền SMTP trong `.env`, rồi chỉ khởi động hạ tầng/auth riêng:

```bash
docker compose --env-file .env -f auth-service/compose.dev.yml --profile app up --build -d auth-service
curl http://localhost:8090/actuator/health
```

PostgreSQL ở `127.0.0.1:54330`, DB `auth_db`, user `poker`, password local
`poker_dev`. Có thể đổi bằng `AUTH_DB_USERNAME`/`AUTH_DB_PASSWORD`.
RabbitMQ: `localhost:5673`, UI `http://localhost:15673`, user/password local `poker`/`poker_dev`.
Compose này có project/volume riêng `poker-auth-dev`, không dùng DB của service khác.
`-f auth-service/compose.dev.yml` chọn riêng file này; không tự ghép với
`docker-compose.yml` ở root. Chạy `docker compose up` tại root mà không có `-f`
sẽ dùng file root và khởi động toàn bộ service không có profile.

## Gửi OTP vào hộp thư thật

Cả Compose dev và root đều truyền các biến `AUTH_MAIL_*` trong `.env` vào app.
Ví dụ Gmail:

```dotenv
AUTH_MAIL_HOST=smtp.gmail.com
AUTH_MAIL_PORT=587
AUTH_MAIL_FROM=your-email@gmail.com
AUTH_MAIL_USERNAME=your-email@gmail.com
AUTH_MAIL_PASSWORD=your-google-app-password
AUTH_MAIL_SMTP_AUTH=true
AUTH_MAIL_STARTTLS=true
```

Thay email bằng tài khoản gửi thật; `AUTH_MAIL_FROM` dùng cùng email với
`AUTH_MAIL_USERNAME`. Dùng [Google App Password](https://support.google.com/mail/answer/185833),
không dùng mật khẩu đăng nhập Gmail. Gmail SMTP dùng
[port 587 với STARTTLS](https://support.google.com/mail/answer/7104828).
OTP được gửi đến email trong request đăng ký; lấy mã ở hộp thư đó, kiểm tra cả Spam.
Spring Boot giữ timeout SMTP 5 giây; gửi thất bại trả `AUTH_MAIL_UNAVAILABLE` và rollback.

Khi chỉ sửa `.env` hoặc cấu hình Compose và app đã được build, tạo lại riêng
container auth để nạp cấu hình mới (hạ tầng phải đang chạy):

```bash
docker compose --env-file .env -f auth-service/compose.dev.yml --profile app up -d --no-deps --force-recreate auth-service
```

Không cần build lại image chỉ để đổi biến môi trường; `restart` đơn thuần không
nạp lại cấu hình Compose mới. Tài khoản SMTP thực tế chưa được xác minh bằng việc gửi email.

### Mailpit tùy chọn

Mailpit chỉ chạy khi chọn profile `mailpit` hoặc gọi đích danh service. Nếu muốn
test không gửi mail thật, đổi `.env` sang `AUTH_MAIL_HOST=mailpit`,
`AUTH_MAIL_PORT=1025`, `AUTH_MAIL_FROM=no-reply@poker.local`, để trống username/password,
và đặt `AUTH_MAIL_SMTP_AUTH=false`, `AUTH_MAIL_STARTTLS=false`, rồi chạy:

```bash
docker compose --env-file .env -f auth-service/compose.dev.yml --profile app --profile mailpit up -d
```

Mailpit UI: `http://localhost:8026`. Khi Java chạy trên máy, dùng host `localhost`
và port `1026` thay cho tên service/port nội bộ Docker ở trên.
Root Compose dùng tên service `auth-mailpit`, cùng port nội bộ `1025` và profile `mailpit`.

## Chạy Java trên máy

```bash
docker compose --env-file .env -f auth-service/compose.dev.yml up -d postgres rabbitmq
mvn -B -pl auth-service -am install -DskipTests
# Export AUTH_* trong .env vào môi trường chạy Java trước khi chạy lệnh này.
mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local
```

Profile `local` dùng password mặc định của Compose dev. Ngoài local phải cung cấp
`AUTH_DB_PASSWORD` (hoặc `SPRING_DATASOURCE_PASSWORD`) và `AUTH_RABBIT_PASSWORD`.
`AUTH_DB_URL` và
`AUTH_DB_USERNAME` đổi kết nối; mặc định URL/user như trên. Spring Boot không tự đọc
file `.env`: export biến vào shell, cấu hình trong IDE hoặc dùng Compose `--env-file .env`.
Giá trị trong `.env.example` chỉ phục vụ local, không dùng làm secret triển khai.

## Test API bằng Swagger UI

Sau khi auth-service khởi động, mở **http://localhost:8090/swagger-ui.html**.
OpenAPI JSON: `http://localhost:8090/v3/api-docs`.

1. Mở nhóm **Đăng ký và OTP**, chọn **1. Đăng ký tài khoản** → **Try it out**.
2. Sửa JSON mẫu, giữ `password` và `confirmPassword` khớp nhau → **Execute**. Thành công trả 201.
3. Mở hộp thư của email đăng ký để lấy OTP; nếu chọn Mailpit thì mở `http://localhost:8026`.
4. Chọn **2. Xác thực OTP đăng ký**, nhập cùng email và OTP dạng string 6 chữ số → **Execute**. Thành công trả ACTIVE; chưa cấp token.
5. Khi chưa xác thực và cần mã mới, dùng **3. Gửi lại OTP đăng ký** sau thời gian chờ (mặc định 60 giây).

Swagger có mô tả field, JSON mẫu, response schema và mã lỗi cho cả ba API;
mã `012345` chỉ là ví dụ, cần thay bằng OTP thật trong email. Các API hiện tại
public, chưa cần bấm Authorize. UI gọi auth-service ở địa chỉ đang mở.

Response thành công theo style StellarStay: `code`, `message`, `result`, `timestamp`,
`path`; dữ liệu nghiệp vụ nằm trong `result` (ví dụ `result.accountId`,
`result.accountStatus`). `code` thành công là `success_request`, timestamp UTC.
Đăng ký trả HTTP 201, xác thực/gửi lại trả 200. Swagger hiển thị envelope và DTO
trong `result`. Lỗi dùng format chung `code`, `message`, `timestamp`, `requestId`.

Mặc định Swagger bật. `AUTH_SWAGGER_ENABLED=false` tắt cả UI và OpenAPI JSON;
biến được chuyển vào app ở cả Compose dev và root Compose. Java trên máy cần
export/IDE env như các biến cấu hình khác. Nếu BE đang chạy trước khi thêm dependency,
khởi động lại để tải Swagger. Nguồn thư viện: [springdoc-openapi](https://springdoc.org/).

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

Mở hộp thư email đăng ký (hoặc Mailpit nếu dùng cấu hình tùy chọn), lấy mã 6 chữ số
rồi thay `012345` bằng mã vừa nhận:

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
