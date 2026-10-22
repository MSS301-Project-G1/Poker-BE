# Chat Service — kế hoạch triển khai

Nguồn yêu cầu: `docs/SPEC.md` §2.6, §3–§6, §7.2, §9.3, §10 và §12.
Quy tắc chi tiết: `chat-service/RULE.md`.

## Trạng thái hiện tại — 2026-10-17

- [x] Backend MVP: DIRECT/MATCH TEXT, REST/history cursor, STOMP và match lifecycle.
- [x] PostgreSQL riêng, JPA/Flyway, Validation, MapStruct, RabbitMQ, WebSocket/STOMP và JWT/JWKS.
- [x] REST chỉ tin Gateway key + X-User-Id; internal Social dùng key riêng. Gateway giữ path và proxy WS.
- [x] Testcontainers PostgreSQL/RabbitMQ, REST ingress, concurrency, STOMP JWT và user-isolated delivery.
- [x] Suite module hiện có 18 test; kết quả lần chạy cuối xem README.
- [ ] FE chưa triển khai trong đợt backend này.
- [ ] STICKER/SOUND, restrictions và ChatPanel thuộc các đợt tiếp theo.

Các checklist bên dưới vẫn là kế hoạch chi tiết. Mục chưa được kiểm chứng riêng không được xem là hoàn tất chỉ vì backend MVP đã có.

## Thứ tự triển khai

### Đợt 0 — nền module và hạ tầng

- [x] Thêm JPA/PostgreSQL, Flyway, Validation, AMQP, WebSocket/STOMP, Security,
  MapStruct, OpenAPI và test dependencies.
- [ ] Cấu hình datasource, RabbitMQ, Social URL, JWT/JWKS, CORS, message limits,
  timeout/retry/DLQ bằng env.
- [ ] Mở rộng `compose.yml` với `chat-db`, healthcheck, volume, `chat-net`; dùng
  RabbitMQ chung, không tạo Redis/broker riêng.
- [x] Tạo `ServiceException` với tiền tố CHAT_, exception handler và Clock bean.

Tiêu chí: application chạy với PostgreSQL/RabbitMQ thật; không cần Social để boot
nhưng call DIRECT phải fail closed nếu Social không sẵn sàng.

### Đợt 1 — schema và persistence

- [x] V1 tạo `conversations`, `conversation_members`, `messages`, constraint/index.
- [x] V1 tạo `processed_events`; WS delivery MVP best effort, chưa thêm `chat_outbox`.
- [x] Entity/repository/MapStruct DTO; tuyệt đối không serialize Entity ra API.
- [ ] Unique `direct_key`, unique `(type, ref_id)` cho MATCH, unique member và
  `(sender_id, client_message_id)` nếu field được chốt.
- [x] Test PostgreSQL thật cho constraint và concurrent get-or-create DIRECT.

### Đợt 2 — DIRECT REST MVP

- [x] `SocialClient` gọi `/internal/friends/{a}/with/{b}`, timeout ngắn, fail closed.
- [x] Get-or-create DIRECT; member lấy từ authenticated user và target hợp lệ.
- [x] List conversations và message history bằng cursor, ownership bắt buộc.
- [x] Application service gửi TEXT dùng chung cho REST fallback và STOMP.
- [x] Kiểm tra friendship/block tại thời điểm tạo và mỗi lần gửi.
- [ ] Unit/controller/contract test cho non-friend, blocked, non-member, empty/long
  content, duplicate request và pagination ordering.

Tiêu chí: hai bạn bè dùng cùng một DIRECT conversation dù gọi tạo đồng thời; user
thứ ba không đọc hoặc gửi được.

### Đợt 3 — `/ws/chat` MVP

- [x] WebSocket/STOMP config và JWT ChannelInterceptor theo mẫu bảo mật Game.
- [x] SEND `/app/conversations/{id}/send`, SUBSCRIBE `/user/queue/chat`.
- [x] Persist trước, sau commit mới fan-out message tới từng member.
- [x] User-specific error destination và error DTO `CHAT_*`.
- [ ] Rate/message-size/heartbeat/allowed-origin config.
- [x] STOMP integration test hai member và một outsider client.

Tiêu chí: message lưu đúng một lần, hai member nhận được và user ngoài conversation
không nhận/đọc được.

### Đợt 4 — MATCH chat MVP

- [x] Bind `chat-service.match.started` và `chat-service.match.finished`.
- [x] `match.started` idempotently tạo MATCH conversation + members từ playerIds.
- [x] `match.finished` idempotently đóng conversation; sau đó gửi bị từ chối.
- [x] Retry hữu hạn + DLQ; processed event commit cùng business state.
- [ ] Test duplicate, finished-before-started, payload sai, service restart và
  nhiều trận chạy đồng thời.
- [ ] Phối hợp Bảo để xử lý khoảng trống `match.cancelled` trước demo full flow.

Tiêu chí: chạy Rank/Normal thật tạo phòng đúng người và tự đóng sau trận.

### Đợt 5 — `<ChatPanel>` FE MVP

- [ ] Tạo `Poker-FE/src/shared/chat/ChatPanel` nhận `conversationId`.
- [ ] Load lịch sử cursor, kết nối `/ws/chat`, optimistic state có reconcile và
  loading/empty/error/reconnect states.
- [ ] Dùng `useProfiles(ids)` cho tên/avatar; không phụ thuộc code nội bộ feature khác.
- [ ] Friends dùng DIRECT conversation; Game Table dùng MATCH conversation/ref.
- [ ] Responsive và không làm che bàn chơi.

### Đợt 6 — GĐ2 sticker, sound và restrictions

- [ ] Thêm `STICKER`/`SOUND`; parse productId và gọi Shop owns trước khi persist.
- [ ] Cooldown/rate limit chống spam có test đồng thời.
- [ ] Kiểm tra chat ban từ restrictions hoặc `account.penalized` sau khi contract chốt.
- [ ] FE render/play asset an toàn; không tin URL tùy ý từ message content.

### Đợt 7 — hardening và vận hành

- [ ] Full Docker E2E: Gateway/Auth → Social → Chat → Rabbit → Game events.
- [ ] Load test concurrent send/history và nhiều conversation.
- [ ] Metric: active WS sessions, messages/s, send latency, consumer lag/error/DLQ,
  open MATCH conversations và Social dependency errors.
- [ ] Quyết định durable WebSocket delivery/read receipt/unread/retention bằng SPEC
  trước khi code; không tự thêm scope.
- [ ] README local run, Swagger, STOMP examples và troubleshooting.

## Dependency và mock

| Cần gì | Nguồn | Mock khi chờ |
|---|---|---|
| Friendship/block | Social `/internal/friends/{a}/with/{b}` | dev stub coi mọi người là bạn |
| Match lifecycle | Game `match.started/finished` | fixture publish qua RabbitMQ |
| JWT/JWKS | Khanh Auth/Gateway | test signing key/profile test |
| Restrictions | Khanh | luôn không bị phạt, chỉ GĐ2 |
| Sticker/sound ownership | Shop | luôn owned, chỉ GĐ2 |

Mock chỉ bật explicit dev/test profile; production không fallback âm thầm.

## Việc phải chốt trước khi tích hợp production

- [ ] Social hoàn tất và Chat contract-test hai internal friends endpoints.
- [ ] Bảo xác nhận payload/routing của `match.started` và `match.finished`, đồng thời
  chốt cách đóng chat khi trận bị hủy/restart.
- [ ] Khanh route `/api/chat/**`, `/ws/chat`, chặn `/internal/**`, cung cấp JWKS.
- [ ] Nhóm chốt thêm `clientMessageId` vào SEND payload nếu cần dedup end-to-end.
- [ ] Tùng chốt content/productId cho STICKER/SOUND ở GĐ2.

## Definition of Done riêng

- Chỉ member đọc/gửi; DIRECT luôn kiểm tra friend/block; MATCH chỉ do event quản lý.
- Consumer idempotent theo eventId và không làm mất event khi transaction lỗi.
- Message là từng row, cursor ordering ổn định, không lưu name/avatar.
- Có unit, controller, PostgreSQL, RabbitMQ và WebSocket integration test.
- Docker Compose chạy cùng DB riêng và RabbitMQ chung.
- Mỗi lần code phải cập nhật checklist và số test thực tế trong file này.
