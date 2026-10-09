# Phase 1 — ghi chú để review

> Ngày 2026-10-10. Phạm vi: nền tảng auth và database; chưa triển khai API nghiệp vụ.

## Các lựa chọn trong đợt này

- `common`: `BaseEntity` và `ErrorResponse` theo SPEC. Không chuyển entity nghiệp vụ
  của auth/game vào common; không thêm base service hoặc base controller.
- `BaseEntity`: UUID v7 sinh lúc persist, `createdAt`/`updatedAt` là `Instant`,
  cột SQL `TIMESTAMP WITH TIME ZONE`; `is_deleted` mặc định false. Dùng Hibernate
  `@UuidGenerator(VERSION_7)` và Spring Data auditing (`@EnableJpaAuditing` tại auth).
- `accounts`: email, password hash, trạng thái, xác thực email, vai trò và các field
  của BaseEntity. Tài khoản mới mặc định `PENDING_VERIFICATION`, chưa xác thực,
  role `USER`. Enum hiện chỉ có trạng thái chờ xác thực/hoạt động; xử phạt bổ sung sau.
- Email bỏ khoảng trắng hai đầu, lowercase bằng `Locale.ROOT`, unique toàn bảng.
  Không bỏ dấu chấm hoặc phần `+tag`. Repository nhận email đã chuẩn hóa.
  Email của tài khoản soft delete vẫn được giữ chỗ; thay đổi chính sách tái sử dụng
  phải có quyết định/migration sau này.
- `profiles`: accountId unique/FK, displayName và avatarUrl nullable. Tên được trùng;
  giới hạn lưu hiện là 100 ký tự, avatar URL 2048 ký tự. Đây là giới hạn schema,
  chưa chốt field bắt buộc của request đăng ký hoặc quy tắc upload.
- Profile chưa tự tạo theo account: thời điểm tạo nằm trong luồng đăng ký phase 2.
  Không thêm gender/birthdate, firstLoginRewarded, OTP, refresh token hoặc outbox
  trước phase cần dùng.
- Soft delete dùng Hibernate `@SQLDelete` và `@SQLRestriction` ở hai entity:
  delete entity đổi cờ, truy vấn ORM lọc bản ghi đã xóa. Không dùng bulk delete,
  `deleteAllInBatch` hoặc native DELETE vì chúng bỏ qua lifecycle/SQLDelete.
  Chưa có API xóa tài khoản; khi thêm phải xử lý account/profile trong cùng transaction.
- `ProfileMapper` chỉ tạo DTO accountId/displayName/avatarUrl. DTO chưa được public
  qua endpoint và chưa phải hợp đồng FE cuối cùng. Không serialize Account/Profile trực tiếp.
- `PasswordEncoder` dùng `DelegatingPasswordEncoder`, mặc định bcrypt và lưu prefix
  `{bcrypt}`. Chỉ thêm module crypto, chưa bật HTTP Security hoặc form login mặc định.
  Quy tắc mật khẩu chốt phase 2, gồm giới hạn đầu vào phù hợp bcrypt (72 byte UTF-8).
  Tham khảo [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

## Style theo StellarStay identify

Nguồn tham khảo đã đọc: module
`/home/tommy/Project/StellarStay/be/stellar_api/src/main/java/system/stellar_stay/modules/identify`
và các class BaseEntity/ApiException/GlobalExceptionHandler/ApiResponse trong shared.

- Controller mỏng, gọi service, validate request bằng `@Valid` và trả `ResponseEntity`.
  Service có interface + `service/impl`, dùng `@Service`, `@RequiredArgsConstructor`,
  dependency `private final`; transaction đặt ở service cho thao tác dữ liệu.
  Đây là quy ước cho phase 2 trở đi, chưa tạo controller/service nghiệp vụ ở phase 1.
- Request DTO ưu tiên record; response DTO dùng class với Lombok getter/setter,
  constructor. Dùng builder cho entity và response chung, tránh tự viết getter/setter.
- Entity dùng `@Getter/@Setter`, `@SuperBuilder`, constructor JPA; default trạng thái/
  role có `@Builder.Default` để tạo bằng builder không mất giá trị ban đầu.
  Email chuẩn hóa cả ở setter và lifecycle trước persist/update vì builder bỏ qua setter.
- Repository là interface `JpaRepository`, có `@Repository`; query trả Optional khi
  bản ghi có thể không tồn tại. MapStruct là bean Spring, mapping field rõ ràng;
  update DTO về entity dùng `@MappingTarget`/ignore null khi triển khai chỉnh sửa.
  Giữ `ReportingPolicy.ERROR` để không bỏ sót field mới trong response.
- Lỗi dùng `ApiException(ErrorCode)` hoặc thông điệp nghiệp vụ tùy chỉnh,
  `GlobalExceptionHandler` tạo response bằng builder. ErrorCode nằm trong shared của
  auth, không đẩy mã nghiệp vụ vào common của toàn hệ thống.
- Giữ cây package auth-service đã chốt. Quan hệ JPA/FK chỉ trong database service;
  tích hợp wallet/game/ranking qua API/event. Không đưa các quan hệ entity liên module
  của monolith hoặc schema role/permission/workspace sang Poker.
- Giữ thời gian `Instant`, lỗi bốn field và tiền tố `AUTH_` của SPEC Poker.
  Không đổi sang `LocalDateTime` hoặc envelope lỗi của StellarStay.
  Comment `UuidGenerator.Style.TIME` là UUID v7 trong code tham khảo không chính xác:
  Hibernate TIME là v1, nên dùng VERSION_7. Xem
  [Hibernate UuidGenerator.Style](https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/annotations/UuidGenerator.Style.html).

## Lỗi và validation

JSON chung: `code`, `message`, `timestamp` UTC, `requestId`.
Nhận `X-Request-Id` nếu không trống và tối đa 128 ký tự; nếu thiếu/sai độ dài thì
sinh UUID cho lỗi. Phần request tracing/gắn header xuyên service làm ở gateway phase 4.

| Trường hợp | HTTP | Code |
|---|---|---|
| Body/JSON/parameter/validation sai | 400 | `AUTH_INVALID_REQUEST` |
| Không tìm thấy route/tài nguyên | 404 | `AUTH_NOT_FOUND` |
| Method không được hỗ trợ | 405 | `AUTH_METHOD_NOT_ALLOWED` |
| Xung đột dữ liệu/ràng buộc DB | 409 | `AUTH_CONFLICT` |
| Content-Type không được hỗ trợ | 415 | `AUTH_UNSUPPORTED_MEDIA_TYPE` |
| Lỗi ngoài dự kiến | 500 | `AUTH_INTERNAL_ERROR` |

Handler giữ HTTP status/header của lỗi framework, không trả SQL, tên constraint,
giá trị request hoặc exception message nội bộ trong response. Những mã theo nghiệp vụ
(email đã dùng, OTP hết hạn, sai mật khẩu...) bổ sung khi có API tương ứng.
Bean Validation dùng cho entity và có sẵn dependency để validate DTO ở phase 2.

## Kiểm chứng và giới hạn

Lệnh/hướng dẫn chạy nằm trong [auth-service/README.md](../../auth-service/README.md).
Test kiểm tra Flyway/Hibernate validate, UUID v7, UTC/default tài khoản, chuẩn hóa
và unique email kể cả soft delete, FK/unique profile, trùng displayName, cập nhật audit,
soft delete profile, mapper, hash mật khẩu và lỗi HTTP/validation.

Auth dùng PostgreSQL 17 thật qua Testcontainers: DB `auth_test`, port ngẫu nhiên,
không mount volume hoặc dùng database/container có sẵn. Spring quản lý vòng đời
container và dọn sau test. Dependency/profile H2 đã bỏ khỏi module auth;
không sửa hạ tầng test của game do chủ service đó quản lý.
Tham khảo [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html).

Kết quả ngày 2026-10-10 sau khi đổi PG/style:
`mvn -B -pl auth-service -am verify` pass 12 test auth trên PostgreSQL.
Lượt `mvn -B verify` đầu sau refactor bị dừng ở `ManyTablesTest` của game,
assertion `isCompletionPersisted()` dòng 44; toàn bộ 12 test auth vẫn pass.
Chạy lại `mvn -B -fae verify` pass tất cả 12 module với 57 test,
không failure/error/skip. Game không sử dụng BaseEntity mới và không có thay đổi
code trong đợt này; ghi nhận test game cần chủ module theo dõi độ ổn định.
Hai cấu hình Compose và `git diff --check` đều pass. Test chạy ngoài sandbox
để truy cập Docker và mở cổng localhost cho WebSocket test.

Mailpit, app qua Compose và Docker image build chưa được khởi chạy trong lượt này;
chạy hướng dẫn README để review môi trường dev. Mailpit mới có cấu hình hạ tầng,
chưa có luồng gửi/nhận OTP.
