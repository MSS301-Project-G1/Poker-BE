# Chat Service — quy tắc kiến trúc và nghiệp vụ

> Phạm vi: `chat-service` (port `8085`) và PostgreSQL `chat_db`.
> Nguồn sự thật cao nhất là `docs/SPEC.md`; thay đổi API nội bộ, event hoặc
> WebSocket liên service phải cập nhật SPEC và được bên sử dụng review.

Tài liệu đi kèm:

- `IMPLEMENTATION_PLAN.md`: thứ tự triển khai, test và tiến độ.
- `RESEARCH_REFERENCES.md`: paper/standard và cách áp dụng vào từng tính năng.

## 0. Căn cứ từ SPEC

| Nội dung | Mục SPEC |
|---|---|
| Chat bạn bè và chat trận dùng chung một service/DB | §2.6 |
| Ownership, port, database | §3.1, §9.3 |
| Database per Service và chống chồng chéo | §4.1–§4.3 |
| REST header và WebSocket JWT | §5.1, §6.3 |
| UUID, UTC, JSON, enum, soft delete | §5.2 |
| Lỗi `CHAT_`, consumer idempotency | §5.3–§5.4 |
| Social internal friends, Shop owns, Identity restrictions | §6.1 |
| `match.started`, `match.finished`, `account.penalized` | §6.2 |
| `/ws/chat` STOMP contract | §6.3 |
| `/api/chat/**` route | §6.4 |
| Mở/đóng phòng theo luồng Rank | §7.2 |
| Bảng dữ liệu và checklist Chat | §9.3 |
| `<ChatPanel>` và FE ownership | §10 |

- Nội dung ghi **bắt buộc** lấy trực tiếp từ SPEC.
- Nội dung ghi **đề xuất triển khai** là quyết định nội bộ của Chat.
- Khi RULE mâu thuẫn SPEC, dùng SPEC và sửa RULE trong cùng PR.

## 1. Ownership và ranh giới

Chat sở hữu:

- Conversation `DIRECT` giữa bạn bè và `MATCH` trong một trận.
- Membership và từng message riêng lẻ trong `chat_db`.
- Consumer `match.started` để mở phòng, `match.finished` để đóng phòng.
- REST `/api/chat/**`, STOMP `/ws/chat`, destination send và user queue đúng SPEC.
- FE `shared/chat/<ChatPanel conversationId>` dùng cho Friends và Game Table.
- GĐ2: STICKER/SOUND, inventory ownership check, chat restriction và cooldown.

Chat không được:

- Tự tạo quan hệ bạn bè/block; gọi Social internal API.
- Đọc `social_db`, `game_db`, `shop_db` hoặc database service khác.
- Tự xác định thành viên trận ngoài payload `match.started` từ Game.
- Chạy game, matchmaking, notification hoặc presence.
- Lưu display name/avatar; FE dùng `useProfiles(ids)`.
- Tự cấp sticker/sound hoặc tin client khai sở hữu; GĐ2 gọi Shop internal API.

## 2. Mô hình conversation

### 2.1. DIRECT

- Chỉ tạo/mở DIRECT khi Social trả `isFriend=true` và `isBlocked=false`.
- Một cặp account chỉ có một conversation DIRECT. Đề xuất lưu `directKey` là
  `min(accountId):max(accountId)` với unique constraint để chống tạo trùng đồng thời.
- DIRECT không tự đóng khi unfriend, nhưng gửi message mới phải kiểm tra lại quan hệ;
  nếu không còn là bạn hoặc đã block thì từ chối. Lịch sử chỉ thành viên được đọc.
- Hai thành viên được ghi trong `conversation_members`; không nhận member list từ client.

### 2.2. MATCH

- `match.started` tạo hoặc lấy conversation `MATCH` theo `refId = matchId`, unique
  `(type, refId)`, rồi thêm đúng `playerIds` từ event.
- `match.finished` đặt `closedAt`; conversation đã đóng là read-only.
- Event được xử lý idempotent theo `eventId`; nhận trùng không tạo conversation/member trùng.
- Nếu `match.finished` đến khi chưa thấy `match.started`, consumer phải ghi nhận/dedup
  và retry/reconcile hữu hạn; không mở một phòng không biết membership.
- Người rời game giữa chừng vẫn là member để đọc lịch sử, nhưng không có quyền gửi sau
  `closedAt`. Việc thay người giữa trận không thuộc Chat; cần event contract mới nếu Game hỗ trợ.

## 3. Message

- MVP chỉ cho `TEXT`; `STICKER` và `SOUND` là GĐ2.
- Mỗi message là một dòng trong `messages`, không lưu cả cuộc trò chuyện thành JSON.
- `senderId` lấy từ Principal/X-User-Id; không nhận hoặc tin senderId trong payload.
- Trước khi ghi phải kiểm tra membership, conversation chưa đóng và permission DIRECT.
- `content` phải trim, không rỗng, giới hạn độ dài bằng config; không render HTML thô.
- Thứ tự ổn định bằng `(createdAt, id)`; API lịch sử dùng cursor, không offset vô hạn.
- Sau khi transaction message commit mới đẩy DTO xuống `/user/queue/chat` cho từng
  member. Không broadcast destination công khai chứa nội dung riêng tư.
- Retry từ client không được tạo message trùng: đề xuất request có `clientMessageId`
  UUID và unique `(senderId, clientMessageId)`. Nếu nhóm không chọn cơ chế này thì
  phải chấp nhận at-least-once ở UI và ghi rõ.

## 4. WebSocket `/ws/chat`

- Dùng STOMP theo contract:
  - SEND `/app/conversations/{id}/send` body `{ messageType, content }`.
  - SUBSCRIBE `/user/queue/chat`.
- Client gửi JWT trong `Authorization` của frame `CONNECT`; service tự xác thực
  RS256/JWKS, issuer, audience, expiry và UUID subject.
- `ChannelInterceptor` đặt Principal từ JWT và kiểm tra SEND/SUBSCRIBE; không cho
  client giả `/user/{id}` hoặc subscribe broker destination nội bộ.
- Lỗi business gửi về user queue lỗi riêng được tài liệu hóa; không đóng toàn bộ
  socket vì một message sai.
- Giới hạn message size, send rate, heartbeat, idle timeout và allowed origins bằng config.
- Simple broker chỉ phù hợp một instance. Nếu scale nhiều instance, phải dùng STOMP
  broker relay hoặc cơ chế fan-out đã kiểm chứng; không tuyên bố hỗ trợ scale khi chưa có.

## 5. RabbitMQ và idempotency

- Dùng exchange topic chung `poker.events`; không tạo RabbitMQ riêng.
- Queue riêng:
  - `chat-service.match.started` bind `match.started`.
  - `chat-service.match.finished` bind `match.finished`.
  - GĐ2 `chat-service.account.penalized` bind `account.penalized` nếu dùng read model.
- Envelope gồm `eventId`, `eventType`, `occurredAt`, `payload`.
- Mỗi consumer lưu `processed_events`; chỉ đánh dấu processed trong cùng transaction
  với thay đổi conversation. Event lỗi dùng retry hữu hạn + DLQ, không ack rồi bỏ mất.
- Consumer bỏ qua event mode không cần thiết chỉ sau khi đã validate contract.
- Không dùng event để thay quyền hiện tại của DIRECT; quyền gửi vẫn hỏi Social.

## 6. Dependency contract

### 6.1. Social

- Gọi `GET /internal/friends/{a}/with/{b}` và chỉ đọc `{ isFriend, isBlocked }`.
- Timeout/error Social phải fail closed cho việc tạo/gửi DIRECT; không tự coi là bạn.
- Có interface `SocialClient`; mock dev theo SPEC có thể coi mọi người là bạn nhưng
  không được fallback sang mock ở production.

### 6.2. Shop — GĐ2

- STICKER/SOUND gọi `GET /internal/inventory/{accountId}/owns/{productId}`.
- Dependency lỗi thì không gửi item; không cache quyền sở hữu vô thời hạn.

### 6.3. Restrictions — GĐ2

- Gọi `GET /internal/accounts/{accountId}/restrictions` hoặc dùng read model từ
  `account.penalized` sau khi Khanh chốt contract.
- `banned=true` hoặc `chatBannedUntil > now` thì không cho gửi; vẫn có thể đọc lịch sử
  nếu không có quy tắc khác trong SPEC.

## 7. Persistence `chat_db`

- `conversations`: id, type (`DIRECT`/`MATCH`), refId, directKey, closedAt, timestamps.
- `conversation_members`: conversationId, accountId, joinedAt; unique cặp.
- `messages`: conversationId, senderId, messageType, content, clientMessageId,
  createdAt; index `(conversationId, createdAt, id)`.
- `processed_events`: eventId unique, eventType, processedAt.
- Nếu đẩy message qua outbox để bảo đảm sau restart, thêm `chat_outbox`; không dùng
  một bảng outbox đã có ở service khác.

Không sửa migration đã áp dụng; thay đổi schema bằng migration mới. PostgreSQL là
nguồn sự thật cho conversation/message. Chat core không thêm Redis vì SPEC không yêu
cầu; chỉ thêm khi có use case và ownership rõ.

## 8. Cấu trúc code chuẩn

```text
chat-service/
├── RULE.md
├── IMPLEMENTATION_PLAN.md
├── pom.xml
├── compose.yml
└── src/
    ├── main/
    │   ├── java/com/msspoker/chatservice/
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
    │   │   ├── service/impl/event/
    │   │   ├── security/
    │   │   └── websocket/
    │   └── resources/db/migration/
    └── test/
```

- Không tạo package rỗng.
- Controller/STOMP handler không chứa transaction/business.
- Entity không trả trực tiếp; dùng DTO và MapStruct.
- Service nghiệp vụ khai báo interface trong `service/` và implementation trong `service/impl/`.
- Repository kế thừa Spring Data JPA; SQL đặc thù nằm trong custom fragment.
- Thời gian dùng `Instant`, enum UPPER_SNAKE_CASE, ID theo chuẩn `common`.
- Error response dùng format chung, code bắt đầu `CHAT_`.

## 9. REST API đề xuất trong route Chat sở hữu

Các path chi tiết dưới đây là đề xuất để `<ChatPanel>` dùng; khi service khác phụ
thuộc trực tiếp phải cập nhật SPEC.

- `POST /api/chat/conversations/direct` body `{ accountId }` — get-or-create.
- `GET /api/chat/conversations` — danh sách conversation của chính user, phân trang.
- `GET /api/chat/conversations/{id}` — metadata/members nếu caller là member.
- `GET /api/chat/conversations/{id}/messages?before=&limit=` — lịch sử cursor.
- Có thể có REST fallback `POST .../messages`, nhưng một command gửi message phải dùng
  chung application service với STOMP để không lệch validation.

Chat MATCH không có public API tạo/đóng; chỉ event Game được phép thay đổi lifecycle.

## 10. Docker và test

- `chat-service/compose.yml` sở hữu `chat-service`, `chat-db` và private `chat-net`.
- RabbitMQ dùng từ `docker/compose.infra.yml`; không tạo broker/Redis riêng.
- Container dùng DNS `chat-db`, `rabbitmq`, `social-service`, `shop-service` và service
  restrictions đã được nhóm chốt; không dùng localhost trong container.
- Unit test: permission, closed conversation, content/type, dedup command.
- PostgreSQL Testcontainers: unique DIRECT/refId, ordering/cursor, concurrent create.
- Rabbit integration: duplicate/out-of-order events, retry và DLQ.
- WebSocket integration: JWT, membership, user-isolated delivery, reconnect.
- Contract test Social client và payload Game events.

## 11. Điểm cần chốt, không tự đoán

- SPEC chưa chốt retention/xóa message, read receipt, unread count và edit/delete;
  không triển khai âm thầm trong MVP.
- SPEC chưa có event `match.cancelled`. Nếu Game restart hủy trận mà không phát
  `match.finished`, MATCH conversation có thể không đóng; cần Bảo và nhóm bổ sung
  contract hoặc reconciliation endpoint.
- Body WebSocket trong SPEC chưa có `clientMessageId`; muốn chống gửi trùng end-to-end
  phải cập nhật contract theo quy tắc chỉ thêm field.
- Auth/Identity hiện lệch giữa SPEC và README; restrictions URL phải chờ Khanh chốt.


## Cấu trúc triển khai theo Competition Service

- `controller/`: REST transport; request và response khai báo riêng trong `dto/request/`, `dto/response/`.
- `service/`: interface nghiệp vụ; `service/impl/`: implementation và transaction.
- `service/client/`, `service/impl/client/`: hợp đồng và implementation gọi service ngoài, chỉ tạo khi có client thật.
- `service/event/`, `service/impl/event/`: publisher interface và implementation event, consumer, delivery adapter.
- `repository/`: Spring Data JPA cùng custom fragment cho SQL đặc thù.
- `mapper/`: MapStruct mapping entity sang response.
- `constant/`: header constants; `validator/`: kiểm tra quyền/đầu vào nghiệp vụ được tách khỏi service.
- `config/`, `security/`, `websocket/`: cấu hình và adapter đặc thù transport.
- DTO dùng hậu tố Request/Response; tách từng record thành file, không gom trong lớp Dtos.
- Refactor package và tên Java không đổi JSON field, endpoint, schema, quy tắc khóa hoặc transaction.
