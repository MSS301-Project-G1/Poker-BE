# Chat Service — cơ sở nghiên cứu và cách áp dụng

Tài liệu này bổ trợ `docs/SPEC.md` và `RULE.md`. Nó dẫn nguồn cho các quyết định
ordering, deduplication, authorization, event reliability và WebSocket security;
không tự mở rộng phạm vi sản phẩm.

## 1. Bảng ánh xạ nguồn → tính năng

| Nguồn | Kết luận dùng được | Áp dụng vào Chat |
|---|---|---|
| Lamport (1978) | Đồng hồ vật lý không tự tạo causal/global order | Thứ tự UI theo khóa ổn định trong từng conversation |
| Birrell & Nelson (1984) | Call ID + sequence/reply state loại duplicate retry | `clientMessageId` unique theo sender |
| Saltzer–Reed–Clark (1984) | Correctness/dedup phải kiểm tra ở application endpoint | Persist/dedup trước khi xác nhận gửi thành công |
| Helland & Campbell (2009) | Failure boundary đòi hỏi idempotent processing/reconcile | Consumer match event theo `eventId` |
| Fong & Siahaan (2011) | Authorization phụ thuộc quan hệ động | DIRECT kiểm tra friend/block qua Social |
| RFC 6455 | WebSocket transport và Origin security model | WSS, Origin allowlist, frame limits |
| RFC 8725 | JWT algorithm/issuer/audience validation | Auth STOMP CONNECT và Principal |
| STOMP 1.2 | User messaging, heartbeat, ACK semantics | `/app/.../send`, `/user/queue/chat`, heartbeat |
| RabbitMQ Reliability | At-least-once có thể redeliver | Processed-events, manual ack, DLQ |
| PostgreSQL indexes/isolation | Constraint DB chống race tốt hơn check Java | Unique DIRECT/MATCH/clientMessageId |

## 2. Ordering: dùng đúng phạm vi, không hứa global order

**Nguồn:** Leslie Lamport, *Time, Clocks, and the Ordering of Events in a
Distributed System*, Communications of the ACM 21(7), 1978,
[Microsoft Research](https://www.microsoft.com/en-us/research/publication/time-clocks-ordering-events-distributed-system/),
DOI `10.1145/359545.359563`.

Paper phân biệt quan hệ happens-before với thứ tự từ đồng hồ vật lý. Hai event độc
lập có thể concurrent; timestamp tường không tự chứng minh causality.

Áp dụng MVP:

- Chat không hứa một global order giữa mọi conversation.
- Trong một conversation, server persist message rồi trả thứ tự ổn định bằng
  `(createdAt, id)` hoặc một sequence do DB cấp.
- Cursor phải chứa đủ hai thành phần ordering; chỉ `createdAt` có thể trùng.
- FE reconcile theo server ID/order, không lấy thời gian máy client làm nguồn thật.

Không suy diễn:

- Không cần cài Lamport clock/vector clock cho một PostgreSQL writer MVP.
- Nếu tương lai multi-region/multi-writer, phải thiết kế contract ordering mới; paper
  không biến timestamp DB hiện tại thành causal order.

## 3. Chống gửi trùng bằng clientMessageId

**Nguồn:** Andrew D. Birrell và Bruce Jay Nelson, *Implementing Remote Procedure
Calls*, ACM TOCS 2(1), 1984,
[paper PDF](https://birrell.org/andrew/papers/ImplementingRPC.pdf).

Thiết kế RPC trong paper nhận diện call bằng định danh caller + sequence để server
phân biệt retry với call mới và loại duplicate.

Áp dụng:

- FE sinh UUID `clientMessageId` một lần trước khi SEND và giữ nguyên khi retry.
- DB unique `(senderId, clientMessageId)`.
- Retry trả message đã lưu thay vì insert dòng mới hoặc fan-out lần hai.
- Server không dùng `content + timestamp` để đoán duplicate.

Điểm cần chốt: SPEC hiện chỉ có `{ messageType, content }`; thêm
`clientMessageId` là additive contract change và phải cập nhật SPEC trước khi bắt buộc.

## 4. End-to-end correctness và event idempotency

### 4.1. End-to-End Arguments

**Nguồn:** J. H. Saltzer, D. P. Reed, D. D. Clark, *End-to-End Arguments in
System Design*, ACM TOCS 2(4), 1984,
[paper PDF](https://web.stanford.edu/class/archive/cs/cs240/cs240.1076/readings/endtoend.pdf),
DOI `10.1145/357401.357402`.

Áp dụng:

- WebSocket/TCP đã gửi frame không chứng minh message đã commit DB.
- Chat persist + deduplicate tại application endpoint rồi mới trả success/fan-out.
- Rabbit ack không thay `processed_events` và unique constraint business.

### 4.2. Building on Quicksand

**Nguồn:** Pat Helland và Dave Campbell, *Building on Quicksand*, CIDR 2009,
[paper](https://arxiv.org/abs/0909.1788).

Áp dụng:

- `match.started`/`match.finished` xử lý idempotent theo `eventId`.
- Transaction cập nhật conversation và ghi processed event phải commit cùng nhau.
- Duplicate/out-of-order là ca test bắt buộc, không coi là “Rabbit sẽ không làm vậy”.
- Reconciliation cần thiết cho khoảng trống Game restart không phát event kết thúc.

Không suy diễn: paper không cung cấp sẵn outbox/consumer schema; crash window vẫn
phải kiểm thử trên PostgreSQL và RabbitMQ thật.

## 5. Relationship-based permission cho DIRECT

**Nguồn:** Philip W. L. Fong và Ida Siahaan, *Relationship-Based Access Control
Policies and Their Policy Languages*, SACMAT 2011,
[DOI 10.1145/1998441.1998450](https://doi.org/10.1145/1998441.1998450).

Áp dụng:

- Identity từ JWT là authentication; friend/block từ Social là authorization.
- DIRECT get-or-create và mỗi lần send đều hỏi quan hệ hiện tại.
- Social timeout/error thì fail closed; Chat không dùng cache cũ vô thời hạn.
- MATCH không dùng friendship; permission lấy từ membership do Game event tạo.

Không cần triển khai một policy engine ReBAC tổng quát; hai boolean trong internal
contract đã đủ cho rule MVP.

## 6. WebSocket/STOMP/JWT security

### 6.1. RFC 6455

**Nguồn:** IETF [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455.html).

Áp dụng:

- Validate Origin allowlist; dùng `wss` khi deploy.
- Giới hạn frame/message, UTF-8 hợp lệ, idle/heartbeat và close code.
- WebSocket chỉ cung cấp transport; authorization destination/member do Chat làm.

### 6.2. RFC 8725

**Nguồn:** IETF [RFC 8725 — JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html).

Áp dụng:

- Allowlist `RS256`, verify JWKS, issuer, audience, expiry và subject.
- Principal lấy từ token CONNECT; không tin senderId/accountId trong body/path.
- Phân biệt token dùng cho đúng audience; token hợp chữ ký nhưng sai audience vẫn từ chối.

### 6.3. STOMP 1.2 và Spring

**Nguồn:** [STOMP 1.2 Specification](https://stomp.github.io/stomp-specification-1.2.html),
[Spring STOMP token authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)
và [Spring message authorization](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authorization.html).

Áp dụng:

- JWT đặt ở native header của `CONNECT`; `ChannelInterceptor` xác thực và set user.
- Interceptor authorization chạy sau authentication.
- Chỉ SEND `/app/conversations/{id}/send`; chỉ SUBSCRIBE user destination được phép.
- Heartbeat phát hiện connection mất, nhưng không thay durable message delivery.

## 7. RabbitMQ lifecycle event

**Nguồn chính thức:** RabbitMQ
[Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms),
[Reliability Guide](https://www.rabbitmq.com/docs/reliability) và
[Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx).

Áp dụng:

- Queue riêng cho `match.started` và `match.finished` để Chat có lifecycle độc lập.
- Manual ack sau transaction; connection chết trước ack có thể redeliver.
- Retry hữu hạn, poison message chuyển DLQ; không tạo vòng requeue vô hạn.
- Prefetch hữu hạn; publisher confirm của Game không đồng nghĩa consumer Chat đã xử lý.
- Mục tiêu là at-least-once + idempotency, không tuyên bố exactly-once.

## 8. PostgreSQL concurrency và constraints

**Nguồn:** PostgreSQL
[Indexes on Expressions](https://www.postgresql.org/docs/current/indexes-expressional.html),
[Partial Indexes](https://www.postgresql.org/docs/current/indexes-partial.html),
[Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html);
tham khảo nền tảng: Berenson et al., *A Critique of ANSI SQL Isolation Levels*,
SIGMOD 1995, [Microsoft Research PDF](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/tr-95-51.pdf).

Áp dụng:

- Unique `directKey` ngăn hai request đồng thời tạo hai DIRECT conversation.
- Unique `(type, refId)` ngăn event duplicate tạo hai MATCH conversation.
- Unique `(conversationId, accountId)` và `(senderId, clientMessageId)`.
- Không dùng check-then-insert ở Java làm bảo vệ duy nhất; bắt conflict DB và trả
  resource đã tồn tại khi phù hợp.
- Concurrency test dùng PostgreSQL thật; H2 không đủ chứng minh isolation/index.

## 9. Những thứ research này không yêu cầu triển khai

- Không cần Kafka/global total order cho MVP.
- Không cần vector clock/CRDT khi chỉ có một PostgreSQL nguồn ghi.
- Không tuyên bố end-to-end encryption; TLS/WSS chỉ bảo vệ transport.
- Không tự thêm read receipt, edit/delete, retention hoặc unread counter ngoài SPEC.
- Không thêm Redis vào Chat chỉ để “cho nhanh” khi chưa có use case đo được.
- Không coi simple broker là multi-instance delivery; scale-out cần broker relay hoặc
  fan-out design và test riêng.

