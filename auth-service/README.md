# auth-service

Phase 1: database và nền tảng tài khoản. Chưa có API đăng ký/login/profile,
OTP, JWT hoặc gửi mail. Port mặc định `8090`; `identity-service` giữ nguyên.
Xem [kế hoạch](../docs/auth/implementation-plan.md) và
[ghi chú review phase 1](../docs/auth/phase-1.md).

## Chạy auth riêng bằng Docker

Từ root repo, chỉ khởi động hạ tầng/auth riêng:

```bash
docker compose -f auth-service/compose.dev.yml --profile app up --build -d
curl http://localhost:8090/actuator/health
```

PostgreSQL ở `127.0.0.1:54330`, DB `auth_db`, user `poker`, password local
`poker_dev`. Có thể đổi bằng `AUTH_DB_USERNAME`/`AUTH_DB_PASSWORD`.
Mailpit UI: `http://localhost:8026`, SMTP: `localhost:1026`; phase 2 mới nối gửi mail.
Compose này có project/volume riêng `poker-auth-dev`, không dùng DB của service khác.

## Chạy Java trên máy

```bash
docker compose -f auth-service/compose.dev.yml up -d postgres mailpit
mvn -B -pl auth-service -am install -DskipTests
mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local
```

Profile `local` dùng password mặc định của Compose dev. Ngoài local phải cung cấp
`AUTH_DB_PASSWORD` (hoặc `SPRING_DATASOURCE_PASSWORD`). `AUTH_DB_URL` và
`AUTH_DB_USERNAME` đổi kết nối; mặc định URL/user như trên. Spring Boot không tự đọc
file `.env`: export biến vào shell, cấu hình trong IDE hoặc dùng Compose `--env-file .env`.
Giá trị trong `.env.example` chỉ phục vụ local, không dùng làm secret triển khai.

Root `docker-compose.yml` cũng có PostgreSQL riêng `auth-postgres`, không publish
port DB. Khi dùng root Compose, app kết nối tên service này trong mạng Docker;
không dùng URL localhost dành cho Java trên máy. Volume root và volume Compose dev
là hai database khác nhau. Chọn một cách chạy app để tránh trùng port 8090.

Flyway áp dụng `V1__create_accounts_and_profiles.sql` khi khởi động;
Hibernate chỉ `validate`, không tự sửa schema. Mọi thay đổi tiếp theo dùng migration mới.

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

Test đã chạy Flyway và Hibernate validate trên PostgreSQL thật. Mailpit và app qua
Compose/Docker build chưa chạy trong lượt này; gửi OTP làm ở phase 2.
