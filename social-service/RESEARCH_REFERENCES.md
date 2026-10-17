# Social Service — cơ sở nghiên cứu và cách áp dụng

Tài liệu này không thay thế `docs/SPEC.md` hoặc `RULE.md`. SPEC quyết định **phải
làm gì**; các nguồn dưới đây giải thích **vì sao chọn cơ chế kỹ thuật**. Chỉ ghi
nguồn gốc có thể kiểm tra: paper, RFC, specification và tài liệu chính thức.

## 1. Bảng ánh xạ nguồn → tính năng

| Nguồn | Kết luận dùng được | Áp dụng vào Social |
|---|---|---|
| Fong & Siahaan, ReBAC (2011) | Quyền có thể phụ thuộc quan hệ động giữa các chủ thể | Friendship/block là đầu vào quyền DIRECT chat |
| Chandra & Toueg (1996) | Failure detector trong hệ bất đồng bộ có thể nghi ngờ sai | Presence heartbeat/TTL chỉ là trạng thái gần đúng |
| Saltzer–Reed–Clark (1984) | Tính đúng cuối-cuối phải do application kiểm tra | Consumer tự deduplicate; không tin broker cho exactly-once |
| Helland & Campbell (2009) | Qua failure boundary cần thao tác idempotent/reconcile | `eventId`, processed-events, retry giữ nguyên ID |
| RFC 6455 | WebSocket hai chiều, Origin là một phần security model | Validate Origin, giới hạn frame, dùng WSS khi deploy |
| RFC 8725 | JWT phải khóa thuật toán, validate issuer/audience/subject | Xác thực STOMP CONNECT bằng JWKS/RS256 đúng profile |
| STOMP 1.2 | Heartbeat phát hiện kết nối chết; ACK mode có semantics khác nhau | Heartbeat session; không coi socket mở mãi là online |
| RabbitMQ Reliability/Confirms | Ack/confirm cho at-least-once, vẫn có duplicate | Manual ack sau DB commit; publisher confirm + dedupe |
| PostgreSQL partial/expression indexes | DB có thể enforce uniqueness trên tập con | Một request PENDING cho mỗi cặp unordered |
| PostgreSQL JSONB | JSONB hợp dữ liệu linh hoạt và có thể index | `notifications.data`, không dùng JSON cho friendship |

## 2. Relationship-Based Access Control cho Friends/Blocks

**Nguồn:** Philip W. L. Fong và Ida Siahaan, *Relationship-Based Access Control
Policies and Their Policy Languages*, SACMAT 2011,
[DOI 10.1145/1998441.1998450](https://doi.org/10.1145/1998441.1998450).

Paper mô hình hóa authorization dựa trên quan hệ trong social graph, thay vì chỉ
dựa trên role tĩnh. Điều này sát với yêu cầu “chỉ bạn bè mới chat DIRECT”.

Áp dụng:

- Social là nguồn sự thật của cạnh `FRIEND` và `BLOCK`.
- Chat hỏi `/internal/friends/{a}/with/{b}` tại thời điểm tạo/gửi DIRECT.
- `BLOCK` được ưu tiên hơn `FRIEND`; API trả quan hệ tổng hợp, không để Chat tự nối
  dữ liệu hoặc cache vô hạn.
- Quyền phải kiểm tra lại khi quan hệ thay đổi, không chỉ lúc tạo conversation.

Không suy diễn:

- Paper không bắt buộc schema hai chiều; đó là quyết định của SPEC.
- Không cần triển khai policy language/graph traversal tổng quát cho MVP.
- ReBAC không thay JWT authentication; nó là authorization sau khi đã biết user.

## 3. Presence là failure detector, không phải sự thật tuyệt đối

**Nguồn:** Tushar D. Chandra và Sam Toueg, *Unreliable Failure Detectors for
Reliable Distributed Systems*, Journal of the ACM 43(2), 1996,
[IBM Research](https://research.ibm.com/publications/unreliable-failure-detectors-for-reliable-distributed-systems).

Trong hệ bất đồng bộ, timeout không phân biệt chắc chắn client chết với mạng chậm.
Vì vậy “offline” suy từ heartbeat timeout luôn có khả năng sai tạm thời.

Áp dụng GĐ2:

- Mỗi WebSocket session có heartbeat/TTL riêng trong Redis.
- Chỉ `OFFLINE` khi account không còn session sống; đóng một tab không làm các tab
  khác offline.
- `IN_GAME` lấy từ event Game, nhưng vẫn là read model tạm thời.
- Presence chỉ phục vụ UI, không dùng để cho phép/từ chối giao dịch hay thay
  `active-table` của Game.

Không suy diễn: không cần cài thuật toán consensus/failure detector trong paper;
Redis TTL chỉ là failure detector thực dụng cho trạng thái hiển thị.

## 4. Idempotency và xử lý event qua failure boundary

### 4.1. End-to-End Arguments

**Nguồn:** J. H. Saltzer, D. P. Reed, D. D. Clark, *End-to-End Arguments in
System Design*, ACM TOCS 2(4), 1984,
[MIT/Stanford PDF](https://web.stanford.edu/class/archive/cs/cs240/cs240.1076/readings/endtoend.pdf),
DOI `10.1145/357401.357402`.

Broker/TCP ack không chứng minh business effect đã commit đúng. Application vẫn
phải kiểm tra cuối-cuối.

Áp dụng:

- `processed_events(event_id UNIQUE)` nằm ở Social.
- Chỉ ack RabbitMQ sau khi transaction business + dedupe commit.
- Notification uniqueness dùng `(eventId, accountId)`, không dựa vào “event chắc
  chỉ gửi một lần”.

### 4.2. Building on Quicksand

**Nguồn:** Pat Helland và Dave Campbell, *Building on Quicksand*, CIDR 2009,
[paper](https://arxiv.org/abs/0909.1788).

Paper nhấn mạnh failure boundary dẫn tới retry, bất định thứ tự và nhu cầu chia
thao tác thành các bước idempotent/reconcile được.

Áp dụng:

- Retry publish giữ nguyên `eventId`, không tạo event mới cho cùng business fact.
- Consumer xử lý trùng không tạo notification thứ hai.
- Outbox tách DB commit khỏi publish nhưng giữ được khả năng retry/reconcile.

Không suy diễn: hai paper trên không tự “chứng minh” một schema outbox cụ thể. Schema
và worker vẫn phải được integration test tại các crash window.

## 5. RabbitMQ: at-least-once, không phải exactly-once

**Nguồn chính thức:** RabbitMQ,
[Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
và [Reliability Guide](https://www.rabbitmq.com/docs/reliability).

RabbitMQ phân biệt publisher confirm và consumer acknowledgement. Khi connection
lỗi, producer có thể publish lại message chưa nhận confirm, tạo duplicate; consumer
cũng có thể nhận lại delivery chưa ack.

Áp dụng:

- Outbox chỉ đánh dấu `publishedAt` sau publisher confirm.
- Consumer manual-ack sau DB commit.
- Retry hữu hạn; poison event đưa DLQ thay vì requeue vô hạn.
- Prefetch có giới hạn để tránh consumer giữ quá nhiều delivery chưa xử lý.
- `eventId` là business identity; delivery tag không thay thế `eventId`.

## 6. WebSocket, STOMP và JWT

### 6.1. WebSocket

**Nguồn:** IETF [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455.html).

Áp dụng:

- Kiểm tra allowlist Origin khi handshake; Origin không thay authentication.
- Deploy dùng `wss`, giới hạn frame/message và đóng kết nối vi phạm.
- WebSocket chỉ là transport; membership và user destination vẫn do application kiểm tra.

### 6.2. JWT

**Nguồn:** IETF [RFC 8725 — JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html).

Áp dụng cho STOMP CONNECT:

- Allowlist thuật toán dự kiến `RS256`, không tin `alg` tùy ý từ token.
- Verify signature qua JWKS; validate `iss`, `aud`, expiry và UUID `sub`.
- Không tin accountId ở SEND/SUBSCRIBE payload; Principal lấy từ token.
- Không log token và không truyền token trong query string nếu tránh được.

### 6.3. STOMP

**Nguồn:** [STOMP Protocol Specification 1.2](https://stomp.github.io/stomp-specification-1.2.html)
và [Spring token authentication for STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html).

Áp dụng:

- JWT gửi trong native header của frame `CONNECT` và được xử lý bằng
  `ChannelInterceptor`.
- Bật heartbeat để phát hiện session mất kết nối; heartbeat không chứng minh user
  chủ động logout.
- Chỉ cho subscribe `/user/queue/notifications` và `/user/queue/presence`.

## 7. Constraint dữ liệu phải nằm trong PostgreSQL

**Nguồn chính thức:** PostgreSQL
[Partial Indexes](https://www.postgresql.org/docs/current/indexes-partial.html),
[Indexes on Expressions](https://www.postgresql.org/docs/current/indexes-expressional.html),
[Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html).

Áp dụng:

- Unique partial/expression index trên cặp chuẩn hóa `least(a,b), greatest(a,b)`
  với `status='PENDING'` để hai request ngược chiều không cùng tồn tại.
- Unique `(account_id, friend_id)` và `(blocker_id, blocked_id)`.
- Business code vẫn bắt duplicate-key và trả lỗi `SOCIAL_*`; check-then-insert ở
  Java không đủ chống race.
- Transaction accept/block cần khóa/constraint và test concurrency thật bằng
  PostgreSQL Testcontainers; H2 không chứng minh semantics PostgreSQL.

`notifications.data` dùng JSONB vì payload khác nhau theo type; friendship/block là
dữ liệu quan hệ ổn định nên dùng cột chuẩn, không nhét JSON.

## 8. Những thứ research này không yêu cầu triển khai

- Không cần graph database: truy vấn MVP chỉ là cạnh trực tiếp một bước.
- Không cần consensus cho presence.
- Không tuyên bố exactly-once; mục tiêu là at-least-once + idempotent consumer.
- Không cần lưu notification MVP nếu SPEC vẫn để history ở GĐ2.
- Không coi WebSocket/JWT là end-to-end encryption; hệ thống hiện là server-readable.

