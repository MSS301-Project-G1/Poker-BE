# Social Service — kế hoạch triển khai

Nguồn yêu cầu: `docs/SPEC.md` §3–§6, §7.2/§7.4, §9.3, §10 và §12.
Quy tắc chi tiết: `social-service/RULE.md`.

## Trạng thái hiện tại — 2026-10-16

- [x] Có scaffold Spring Boot, port `8084`, Actuator và context test.
- [x] Có `RULE.md` và `RESEARCH_REFERENCES.md` ánh xạ SPEC với nguồn kỹ thuật gốc.
- [ ] Chưa có PostgreSQL/Redis/RabbitMQ/WebSocket/Security/Validation/Flyway.
- [ ] Chưa có migration, entity, repository, API, consumer hay test nghiệp vụ.
- [ ] `compose.yml` mới chỉ dựng application, chưa có `social-db`/`social-redis`.
- [ ] FE friends/notifications chưa thuộc phạm vi đợt code backend này.

## Thứ tự triển khai

### Đợt 0 — nền module và hạ tầng

- [ ] Bổ sung JPA/PostgreSQL, Flyway, Validation, AMQP, WebSocket/STOMP, Security,
  Redis, MapStruct, OpenAPI và test dependencies.
- [ ] Cấu hình toàn bộ bằng env: datasource, Redis, RabbitMQ, JWT/JWKS, CORS,
  retry/DLQ, pagination; không hard-code secret.
- [ ] Mở rộng `compose.yml`: `social-db`, `social-redis`, healthcheck, volume và
  private `social-net`; dùng RabbitMQ chung, không tạo broker riêng.
- [ ] Tạo `SocialError`, `SocialApiException`, exception handler theo lỗi chuẩn.
- [ ] Tạo clock bean để test thời gian ổn định.

Tiêu chí: application chạy với PostgreSQL/Redis/RabbitMQ thật và health endpoint
phân biệt được dependency nào lỗi.

### Đợt 1 — schema và persistence

- [ ] V1 tạo `friend_requests`, `friendships`, `blocks`, constraint/index bắt buộc.
- [ ] Tạo entity/repository/mapper; query luôn loại dữ liệu soft-deleted.
- [ ] V2 tạo `processed_events`, `social_outbox`.
- [ ] Chưa tạo `notifications` cho tới đợt notification history GĐ2, hoặc tạo nhưng
  phải ghi rõ là FUTURE và không đánh dấu tính năng đã hoàn thành.
- [ ] Test PostgreSQL Testcontainers cho unique constraint và transaction hai chiều.

Tiêu chí: không thể có self relation, friendship trùng hoặc hai request pending cho
cùng một cặp dù hai transaction chạy đồng thời.

### Đợt 2 — Friends MVP

- [ ] API danh sách bạn bè, incoming/outgoing requests có phân trang.
- [ ] Gửi, accept, decline, cancel request và xóa bạn.
- [ ] Quyền thao tác lấy từ `X-User-Id`, không nhận requester từ body.
- [ ] Accept và remove cập nhật friendship hai chiều nguyên tử.
- [ ] Ghi outbox FRIEND_REQUEST/FRIEND_ACCEPTED cùng transaction business.
- [ ] Unit/controller/integration test success, duplicate, reverse request,
  unauthorized actor và concurrent accept.

Tiêu chí: chạy trọn luồng A gửi → B accept → hai bên thấy nhau; retry không tạo trùng.

### Đợt 3 — Blocks MVP

- [ ] API list/block/unblock.
- [ ] Block hủy request pending và gỡ friendship hai chiều nguyên tử.
- [ ] Friend request mới và internal relation query đều tôn trọng block hai chiều.
- [ ] Test block đồng thời với send/accept; kết quả cuối không còn friendship.

### Đợt 4 — Internal Friends contract

- [ ] `GET /internal/friends/{accountId}`.
- [ ] `GET /internal/friends/{a}/with/{b}` trả `{ isFriend, isBlocked }`.
- [ ] Chặn hai endpoint khỏi route public gateway; bổ sung service-to-service auth
  khi nhóm chốt cơ chế chung.
- [ ] Contract test để Chat và Competition dùng mock đúng shape.

Tiêu chí: Chat có thể quyết định DIRECT permission mà không đọc `social_db`.

### Đợt 5 — Notification realtime MVP

- [ ] Khai báo exchange/binding/queue `social-service.notification.requested` và DLQ.
- [ ] Consumer validate envelope/type/accountIds và deduplicate `eventId`.
- [ ] Transactional outbox publisher cho event do Social tạo, publisher confirm rồi
  mới đánh dấu published; retry giữ nguyên `eventId`.
- [ ] `/ws/social` xác thực JWT ở STOMP CONNECT và thiết lập Principal.
- [ ] Đẩy đúng `/user/queue/notifications`; không broadcast/rò dữ liệu user khác.
- [ ] Test duplicate event, offline user, nhiều session, JWT sai/hết hạn và DLQ.

Tiêu chí: FRIEND_REQUEST và event từ Matchmaking/Competition tới đúng user realtime;
Rabbit retry không tạo hai lần xử lý logic.

### Đợt 6 — FE MVP

- [ ] `features/friends`: danh sách, incoming/outgoing, block list, loading/empty/error.
- [ ] Kết nối `/ws/social`; toast/badge cho notification realtime.
- [ ] Dùng hook `useProfiles(ids)` của shared, không lưu name/avatar trong Social.
- [ ] Không triển khai history/bell persistence trước GĐ2.

### Đợt 7 — GĐ2 notification history và presence

- [ ] Migration `notifications`, unique `(eventId, accountId)`, JSONB data.
- [ ] API history phân trang, read một notification và read-all có ownership check.
- [ ] Redis presence nhiều session + heartbeat TTL.
- [ ] Consume `match.started`/`match.finished` idempotent để cập nhật `IN_GAME`.
- [ ] `/user/queue/presence` và màn `features/notifications` đầy đủ.

### Đợt 8 — hardening và tích hợp

- [ ] Test full Docker qua Gateway + RabbitMQ + Social DB/Redis.
- [ ] Load test concurrent friend operations và notification fan-out.
- [ ] Metric: pending requests, consumer lag/error/DLQ, outbox pending/oldest,
  active WebSocket sessions, notification delivery latency.
- [ ] Log có requestId/eventId, không log JWT hoặc nội dung nhạy cảm.
- [ ] README chạy local, Swagger và contract examples.

## Dependency và mock

| Cần gì | Nguồn | Mock khi chờ |
|---|---|---|
| JWT/JWKS, trusted gateway headers | Khanh Auth/Gateway | test JWT key + profile test, không bật mock production |
| `notification.requested` | mọi service | publish envelope qua RabbitMQ UI/integration test |
| `match.started/finished` cho presence | Game | fixture event; chỉ cần ở GĐ2 |
| Profile name/avatar | Auth/Profile API phía FE | FE hiển thị accountId khi profile chưa sẵn |

## Việc phải chốt trước khi tích hợp production

- [ ] Khanh xác nhận service thực tế cung cấp account existence/restrictions và URL.
- [ ] Gateway route đúng `/api/friends/**`, `/api/blocks/**`,
  `/api/notifications/**`, `/ws/social`; chặn `/internal/**`.
- [ ] Chat và Competition review hai internal friends contracts.
- [ ] Các producer review `notification.requested` envelope/type/data.

## Definition of Done riêng

- Business transaction và outbox không lệch nhau.
- Consumer deduplicate event theo `eventId`.
- Không đọc DB service khác, không lưu display name/avatar.
- Có unit, controller, PostgreSQL integration và Rabbit/WebSocket integration test.
- Docker Compose chạy được cùng broker chung.
- Mỗi lần code phải cập nhật checklist và số test thực tế trong file này.
