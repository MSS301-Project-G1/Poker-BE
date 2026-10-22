# Social Service — quy tắc kiến trúc và nghiệp vụ

> Phạm vi: `social-service` (port `8084`), PostgreSQL `social_db` và Redis riêng
> cho presence. Nguồn sự thật cao nhất là `docs/SPEC.md`; thay đổi API nội bộ,
> event hoặc WebSocket liên service phải cập nhật SPEC và được bên sử dụng review.

Tài liệu đi kèm:

- `IMPLEMENTATION_PLAN.md`: thứ tự triển khai, test và tiến độ.
- `RESEARCH_REFERENCES.md`: paper/standard và cách áp dụng vào từng tính năng.

## 0. Căn cứ từ SPEC

| Nội dung | Mục SPEC |
|---|---|
| Ownership, port, database, Redis | §3.1, §3.2, §9.3 |
| Database per Service và chống chồng chéo | §4.1–§4.3 |
| `X-User-Id`, `/internal/**`, WebSocket JWT | §5.1, §6.3 |
| UUID, UTC, JSON, enum, soft delete | §5.2 |
| Lỗi tiền tố `SOCIAL_`, consumer deduplicate | §5.3–§5.4 |
| `/internal/friends/**` | §6.1 |
| `notification.requested`, `match.started/finished` | §6.2 |
| `/ws/social` | §6.3 |
| Route friends/blocks/notifications | §6.4 |
| Bảng và checklist Duy | §9.3 |
| FE và Definition of Done | §10, §12 |

- Nội dung ghi **bắt buộc** lấy trực tiếp từ SPEC.
- Nội dung ghi **đề xuất triển khai** là quyết định nội bộ của Social, được đổi nếu
  không phá hợp đồng liên service.
- Khi RULE mâu thuẫn SPEC, dùng SPEC và sửa RULE trong cùng PR.

## 1. Ownership và ranh giới

Social sở hữu:

- Lời mời kết bạn, quan hệ bạn bè và block.
- Hai API nội bộ `/internal/friends/{accountId}` và
  `/internal/friends/{a}/with/{b}`.
- Consumer `notification.requested` và đẩy realtime qua `/ws/social`.
- GĐ2: presence và lịch sử/đánh dấu đã đọc notification.
- FE `features/friends`, `features/notifications` và kết nối `/ws/social`.

Social không được:

- Lưu tên hiển thị/avatar; FE dùng `GET /api/profiles/batch?ids=...`.
- Đọc DB tài khoản, Chat, Matchmaking, Game hoặc Competition.
- Lưu nội dung chat hoặc mở `/ws/chat`.
- Tự tạo notification thay cho service sở hữu nghiệp vụ. Service nguồn phải phát
  `notification.requested`; riêng FRIEND_REQUEST/FRIEND_ACCEPTED do Social sở hữu.
- Tự parse JWT cho REST. REST chỉ tin `X-User-Id` do gateway gắn trong mạng tin cậy.
- Mở `/internal/**` qua gateway.

## 2. Quy tắc nghiệp vụ bạn bè và block

### 2.1. Friend request

- Không gửi lời mời cho chính mình.
- Không gửi nếu một trong hai phía đã block phía còn lại.
- Không tạo lời mời mới nếu hai tài khoản đã là bạn.
- Mỗi cặp tài khoản chỉ có tối đa một request `PENDING`, không phụ thuộc chiều.
- Nếu B gửi A trong khi request A → B đang `PENDING`, đề xuất xử lý như accept
  request hiện tại thay vì tạo bản ghi thứ hai.
- Chỉ addressee được accept/decline; chỉ requester được cancel.
- Accept phải trong một transaction: khóa request, đổi `ACCEPTED`, tạo đủ hai dòng
  friendship A → B và B → A. Unique constraint chống tạo trùng khi request đồng thời.
- Decline/cancel chỉ đổi request còn `PENDING`; request terminal dùng cho audit.
- Xóa bạn phải xóa/soft-delete cả hai chiều trong một transaction.

### 2.2. Block

- Không block chính mình; `(blockerId, blockedId)` là duy nhất.
- Block là có hướng. `isBlocked=true` nếu A block B **hoặc** B block A, vì cả hai
  trường hợp đều chặn kết bạn/chat giữa cặp đó.
- Đề xuất triển khai: block đồng thời hủy mọi request `PENDING` và gỡ friendship hai
  chiều trong cùng transaction. Unblock không tự khôi phục friendship/request cũ.
- Không phát lộ ai đã block ai qua internal API; chỉ trả boolean tổng hợp.

### 2.3. Internal contract bắt buộc

- `GET /internal/friends/{accountId}` trả danh sách `accountId` bạn bè, không trả
  display name/avatar.
- `GET /internal/friends/{a}/with/{b}` trả đúng `{ isFriend, isBlocked }`.
- `isFriend=true` chỉ khi quan hệ hai chiều hợp lệ và không có block.
- Consumer như Chat/Competition không được truy cập `social_db` trực tiếp.

## 3. Notification và WebSocket

### 3.1. RabbitMQ

- Dùng exchange topic chung `poker.events`; không tạo RabbitMQ riêng.
- Queue của Social cho notification là `social-service.notification.requested`,
  bind routing key `notification.requested`.
- Envelope gồm `eventId`, `eventType`, `occurredAt`, `payload` theo SPEC.
- Consumer phải idempotent theo `eventId`; event trùng không được đẩy/lưu hai lần.
- Payload chỉ được gửi tới các `accountIds` trong event; không broadcast toàn hệ thống.
- Event lỗi parse/validate không được retry vô hạn. Dùng retry hữu hạn và DLQ có
  tên riêng; log phải chứa `eventId` và `X-Request-Id` nếu có.

### 3.2. Notification theo giai đoạn

- MVP: nhận event và đẩy realtime qua `/user/queue/notifications`. Người dùng
  offline có thể không nhận lại vì persistence notification thuộc GĐ2.
- GĐ2: lưu mỗi `(eventId, accountId)` đúng một lần vào `notifications`, hỗ trợ lịch
  sử và `readAt`, sau đó đẩy realtime.
- Social phát FRIEND_REQUEST/FRIEND_ACCEPTED bằng transactional outbox để commit
  quan hệ và event không lệch nhau. Không publish RabbitMQ trực tiếp trước DB commit.

### 3.3. `/ws/social`

- Dùng STOMP. Client gửi JWT trong header `Authorization` của frame `CONNECT`.
- Service tự xác thực JWT RS256/JWKS, issuer, audience, expiry và UUID subject.
- Principal luôn lấy từ token; không tin accountId do client gửi.
- Client chỉ subscribe `/user/queue/notifications` và GĐ2
  `/user/queue/presence`; không cho subscribe queue người khác.
- Không đưa service key/JWT private key vào FE hoặc log.

## 4. Presence — GĐ2

- Trạng thái: `ONLINE`, `IN_GAME`, `OFFLINE`.
- Redis là dữ liệu tạm, không tạo bảng presence trong PostgreSQL.
- Online dùng heartbeat/session TTL; đóng socket chỉ xóa session tương ứng, không
  đánh offline nếu account còn session khác.
- `match.started` đặt player trong payload thành `IN_GAME`; `match.finished` đưa về
  `ONLINE` nếu còn session, ngược lại `OFFLINE`.
- Presence không phải bằng chứng phân quyền và không được dùng thay Game active-table.

## 5. Persistence

### 5.1. PostgreSQL `social_db`

- `friend_requests`: requesterId, addresseeId, status, timestamps; index incoming/
  outgoing và unique cho cặp đang pending.
- `friendships`: accountId, friendId, timestamps; unique `(accountId, friendId)`;
  lưu hai chiều theo SPEC.
- `blocks`: blockerId, blockedId, timestamps; unique cặp có hướng.
- `notifications` (GĐ2): accountId, eventId, type, title, body, data JSONB, readAt;
  unique `(eventId, accountId)`.
- `processed_events`: eventId unique, eventType, processedAt để consumer deduplicate.
- `social_outbox`: eventId unique, eventType, payload JSONB, occurredAt,
  publishedAt, attempts, nextAttemptAt, lastError.

Không sửa migration đã áp dụng; thay đổi schema bằng migration phiên bản mới.

### 5.2. Redis `social-redis`

- Chỉ dùng cho WebSocket session/presence/TTL và fan-out nếu cần nhiều instance.
- Mọi key có prefix và version, ví dụ `social:v1:presence:{accountId}`.
- Không dùng Redis làm nguồn sự thật cho friendship, block hoặc notification history.

## 6. Cấu trúc code chuẩn

```text
social-service/
├── RULE.md
├── IMPLEMENTATION_PLAN.md
├── pom.xml
├── compose.yml
└── src/
    ├── main/
    │   ├── java/com/msspoker/socialservice/
    │   │   ├── config/
    │   │   ├── constant/
    │   │   ├── controller/
    │   │   ├── dto/{request,response,event}/
    │   │   ├── entity/
    │   │   ├── enums/
    │   │   ├── exception/
    │   │   ├── mapper/
    │   │   ├── repository/
    │   │   ├── service/
    │   │   ├── service/impl/
    │   │   ├── messaging/
    │   │   ├── security/
    │   │   └── websocket/
    │   └── resources/db/migration/
    └── test/
```

- Chỉ tạo package khi có class thật; không dựng package rỗng.
- Controller chỉ xác thực transport và gọi service; không chứa transaction/business.
- Entity không trả trực tiếp ra API; dùng DTO và MapStruct.
- Interface chỉ tạo tại boundary cần thay implementation/test double; không tạo
  interface một implementation nếu không đem lại giá trị.
- Thời gian dùng `Instant`, enum UPPER_SNAKE_CASE, ID theo chuẩn UUID của `common`.
- Lỗi API theo `ErrorResponse` chung, code bắt đầu `SOCIAL_`.

## 7. API public đề xuất trong phạm vi route đã được sở hữu

Các path chi tiết này là đề xuất nội bộ để FE Social dùng; nếu service khác phụ
thuộc thì phải đưa vào SPEC trước khi coi là contract liên service.

- `GET /api/friends`
- `GET /api/friends/requests?direction=INCOMING|OUTGOING`
- `POST /api/friends/requests` body `{ addresseeId }`
- `POST /api/friends/requests/{requestId}/accept`
- `POST /api/friends/requests/{requestId}/decline`
- `DELETE /api/friends/requests/{requestId}`
- `DELETE /api/friends/{friendId}`
- `GET /api/blocks`
- `POST /api/blocks/{accountId}`
- `DELETE /api/blocks/{accountId}`
- GĐ2: `GET /api/notifications`, `PATCH /api/notifications/{id}/read`,
  `PATCH /api/notifications/read-all`.

List API phải phân trang/cursor, không trả danh sách vô hạn.

## 8. Docker, cấu hình và test

- `social-service/compose.yml` sở hữu `social-service`, `social-db` và private network
  `social-net`; bổ sung `social-redis` khi triển khai presence GĐ2.
- RabbitMQ dùng `docker/compose.infra.yml`; container gọi `rabbitmq:5672`, Java
  chạy trên host gọi port publish của host.
- Không hard-code secret/localhost trong cấu hình container.
- Unit test: state transition, quyền requester/addressee, block cleanup.
- Integration test PostgreSQL thật: constraint, transaction, concurrent request.
- Integration test RabbitMQ/WebSocket: dedupe, routing đúng user, reconnect/auth.
- Redis Testcontainers: TTL và nhiều session presence ở GĐ2.
- Test phải chứng minh người dùng không đọc/sửa quan hệ hoặc notification của người khác.

## 9. Điểm SPEC chưa đủ để không tự đoán

- Chưa có internal API chính thức để kiểm tra một target account có tồn tại. Không
  đọc DB Auth/Identity; cần nhóm bổ sung contract trước khi enforce chắc chắn.
- SPEC gọi dependency restrictions là `identity-service`, trong khi README hiện nói
  nghiệp vụ tài khoản nằm ở `auth-service`. Chỉ cấu hình URL sau khi Khanh chốt.
- Cơ chế refresh/thu hồi token WebSocket và giới hạn kích thước notification chưa
  được chốt; phải để config và review cùng Gateway/Auth.
