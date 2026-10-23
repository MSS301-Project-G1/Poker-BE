# Matchmaking Service — quy tắc kiến trúc và cấu trúc code

> Phạm vi: `matchmaking-service` (port `8083`), database `matchmaking_db` và Redis.
> Nguồn sự thật cao nhất vẫn là `docs/SPEC.md`. Nếu thay đổi API/event liên service,
> phải cập nhật SPEC và được các service bị ảnh hưởng review trước khi merge.

Tài liệu đi kèm:

- `IMPLEMENTATION_PLAN.md`: thứ tự triển khai, tiêu chí hoàn thành và tiến độ hiện tại.
- `RESEARCH_REFERENCES.md`: paper/tài liệu kỹ thuật và quyết định áp dụng vào dự án.

## 0. Căn cứ từ SPEC

File này được triển khai trực tiếp từ các phần sau của `docs/SPEC.md`:

| Nội dung | Mục SPEC áp dụng |
|---|---|
| Chủ sở hữu, port, database và Redis | §3.1, §3.2, §9.3 |
| Database per Service và giao tiếp liên service | §3.2, §3.4, §4.1 |
| Xác thực bằng `X-User-Id`, route `/internal/**` | §5.1 |
| UUID, thời gian UTC, JSON, enum và soft delete | §5.2 |
| Lỗi tiền tố `MM_` và idempotency | §5.3, §5.4 |
| Wallet debit/credit/balance | §6.1 — wallet-service |
| Game `POST /internal/tables` | §6.1 — game-service |
| Ranking và Identity restrictions | §6.1 — ranking/identity-service |
| Event `notification.requested` | §6.2 |
| Route `/api/matchmaking/**`, `/api/party/**` | §6.4 |
| Luồng trận Rank | §7.2 |
| Luồng trận Normal | §7.3 |
| Fee, accept timeout và cooldown | §8 |
| Phạm vi của Duy, bảng dữ liệu và dependency mock | §9.3 |
| Definition of Done chung | §12 |

Quy tắc đọc file:

- Nội dung ghi **“bắt buộc theo SPEC”** là hợp đồng hiện tại, không tự ý đổi.
- Nội dung ghi **“đề xuất triển khai”** là cách tổ chức code trong service này; có thể
  thay đổi nếu vẫn giữ nguyên hành vi và hợp đồng của SPEC.
- Endpoint/event mới chưa có trong SPEC chỉ được tích hợp với FE hoặc service khác sau
  khi cập nhật `docs/SPEC.md` theo quy tắc §4.2.
- Khi RULE và SPEC mâu thuẫn, luôn thực hiện theo SPEC rồi cập nhật lại RULE.

## 1. Trách nhiệm của service

`matchmaking-service` là **orchestrator ghép trận**, không sở hữu nghiệp vụ của service khác.

Service này được làm:

- Quản lý hàng chờ `RANK` và `NORMAL`.
- Lấy Elo hiện tại để ghép người chơi Rank.
- Ghép người chơi Rank trong khoảng Elo cố định theo config.
- Tạo proposal, nhận accept/decline và xử lý timeout.
- Thu phí Rank, hoàn phí nếu quy trình thất bại.
- Gọi Game để tạo bàn sau khi proposal hợp lệ.
- Phát thông báo `MATCH_FOUND` và `MATCH_READY`.
- Lưu dấu vết proposal, trạng thái thu/hoàn phí và bàn đã tạo.
- GĐ2: quản lý Party và đưa Party vào hàng chờ Normal.

Service này **không được làm**:

- Không đọc hoặc ghi database của service khác.
- Không tự tính hay lưu Elo nguồn gốc; gọi `ranking-service`.
- Không tự cộng/trừ xu; gọi `wallet-service` với `idempotencyKey`.
- Không chạy poker engine hoặc tự tạo bản ghi bàn; gọi `game-service`.
- Không làm Custom Room hoặc Tournament; đó là `competition-service`.
- Không lưu display name/avatar; frontend lấy qua Profile API.
- Không tự phát WebSocket thông báo riêng; phát `notification.requested` để
  `social-service` đẩy realtime.

### 1.1. Checklist bắt buộc theo SPEC §9.3

Phần thuộc `matchmaking-service` phải được triển khai đúng theo checklist này:

#### MVP

- [x] Hàng chờ Rank có API vào và hủy hàng chờ.
- [x] Lấy Elo và chuỗi thắng/thua từ `ranking-service`; không tự tính.
- [x] Ghép theo Elo trong khoảng cố định; Elo `0` vẫn được tham gia Rank.
- [x] Số người tối thiểu/tối đa lấy từ Match Settings của Game, không hard-code hoặc giữ bản cấu hình riêng trong Matchmaking.
- [x] Tạo proposal và cho người chơi chấp nhận trong đúng 10 giây.
- [x] Thu phí Rank qua Wallet sau khi tất cả thành viên chấp nhận.
- [x] Debit dùng key `rank-fee:{proposalId}:{accountId}`.
- [x] Một debit thất bại thì hoàn phí những người đã debit thành công.
- [x] Game CreateTable thất bại chắc chắn thì hoàn phí toàn bộ.
- [x] Người hợp lệ được đưa lại hàng chờ khi proposal thất bại/hết hạn.
- [x] Người decline hoặc không accept bị cooldown 2 phút theo config.
- [x] Hàng chờ Normal miễn phí và không gọi Wallet/Ranking.
- [x] Gọi `POST /internal/tables` của Game; không tự mở bàn.
- [ ] Phát `MATCH_FOUND` và `MATCH_READY` bằng `notification.requested` để
  `social-service` đẩy xuống frontend.
- [ ] FE `features/matchmaking` có màn chờ và popup chấp nhận; phần FE được làm sau
  khi API backend ổn định.

#### Giai đoạn 2

- [x] Party: tạo, mời, kick, rời và tự chuyển chủ cho thành viên vào sớm nhất.
- [x] Chỉ chủ Party được đưa Party vào hoặc rời hàng chờ Normal.
- [x] Party được reserve nguyên khối; thiếu người thì ghép thêm người solo hoặc Party khác.

#### Ngoài phạm vi Matchmaking

- Bạn bè, block và `/internal/friends/**` thuộc `social-service`.
- Presence, notification history và `/ws/social` thuộc `social-service`.
- DIRECT/MATCH conversation, messages và `/ws/chat` thuộc `chat-service`.
- `<ChatPanel conversationId>` thuộc FE `shared/chat/` và được triển khai cùng Chat.
- Sticker/sound và kiểm tra cấm chat không được đặt trong Matchmaking.

Không đưa entity, repository hoặc bảng của `social_db`/`chat_db` vào module này.

### 1.2. Dependency và mock trong lúc chờ service khác

Mọi dependency phải đi qua interface để production client và mock/dev client có thể
thay thế nhau bằng profile/config; không viết `if (dev)` trong business service.

| Dependency | Production | Mock/dev theo SPEC |
|---|---|---|
| Game CreateTable | Gọi `POST /internal/tables` | Trả `tableId` ngẫu nhiên; có thể phát `match.finished` giả sau 10 giây trong dev tool |
| Ranking | Gọi `GET /internal/ranking/{accountId}` | Trả Elo `0`, streak `0` |
| Wallet | Gọi debit/credit thật; chưa gọi balance vì service chưa có contract chạy được | Luôn thành công nhưng vẫn giữ idempotency key |
| Identity restrictions | Gọi restrictions thật | Trả không bị cấm |

Mock chỉ được bật bằng profile `dev` hoặc trong test. Profile production không được
fallback âm thầm sang mock khi dependency lỗi.

## 2. Công nghệ và lựa chọn triển khai

Bắt buộc theo SPEC và cấu hình repo hiện tại:

- Java 21, Spring Boot 4.1.1, Maven multi-module.
- PostgreSQL riêng `matchmaking_db` và Redis cho hàng chờ.
- RabbitMQ exchange `poker.events` cho event liên service.
- Lỗi chuẩn dùng tiền tố `MM_`.

Đề xuất triển khai để đồng nhất với các service đã hoàn thiện:

- Spring MVC cho REST.
- Spring Data JPA + Flyway cho dữ liệu bền vững.
- Redis cho queue, reservation, distributed lock và TTL của proposal.
- Transactional outbox khi phát `notification.requested`.
- Bean Validation cho request.
- Test nghiệp vụ bằng JUnit 5; integration test dùng Testcontainers cho PostgreSQL
  và Redis. H2 chỉ dùng cho test đơn giản, không dùng để chứng minh Redis/concurrency.

Không thêm Eureka hoặc Config Server trong MVP.

### 2.1. Quy tắc Docker Compose của nhóm

- Matchmaking chỉ sửa `matchmaking-service/compose.yml` do service mình sở hữu.
- Giữ nguyên `docker-compose.yml` ở thư mục gốc; file này đã include toàn bộ service
  và được dùng để chạy cả hệ thống.
- RabbitMQ dùng chung duy nhất nằm trong `docker/compose.infra.yml` với service name
  `rabbitmq`. Không tạo RabbitMQ riêng trong compose của Matchmaking.
- Khi chạy trong Docker, kết nối broker bằng `rabbitmq:5672`; cổng
  `127.0.0.1:56729` chỉ dành cho chương trình chạy trực tiếp từ máy host.
- `matchmaking-service/compose.yml` tự khai báo PostgreSQL và Redis của Matchmaking.
- Container Matchmaking nối cả `default` network để gọi RabbitMQ/service khác và
  `matchmaking-net` để truy cập database/Redis riêng.
- `matchmaking-db` và `matchmaking-redis` chỉ nối `matchmaking-net`; service khác
  không được dùng trực tiếp hai datastore này.
- Tên container DNS phải dùng service name: `matchmaking-db`, `matchmaking-redis`,
  `rabbitmq`, `game-service`, `ranking-service`, `wallet-service`, `identity-service`.
- Không hard-code `localhost` trong cấu hình container.

Lệnh chạy:

```powershell
# Chạy toàn hệ thống từ file gốc
docker compose up --build

# Chạy riêng Matchmaking cùng RabbitMQ chung để phát triển
docker compose -f docker/compose.infra.yml -f matchmaking-service/compose.yml up --build
```

## 3. Cấu trúc thư mục chuẩn

Giữ package gốc `com.msspoker.matchmakingservice` và cấu trúc phẳng theo các module hiện có:

```text
matchmaking-service/
├── RULE.md
├── README.md
├── pom.xml
├── compose.yml
└── src/
    ├── main/
    │   ├── java/com/msspoker/matchmakingservice/
    │   │   ├── MatchmakingServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── MatchmakingProperties.java
    │   │   │   └── TimeConfig.java
    │   │   ├── constant/
    │   │   │   └── MatchmakingConstants.java
    │   │   ├── controller/
    │   │   │   └── NormalMatchmakingController.java
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   ├── response/
    │   │   │   └── event/
    │   │   ├── entity/
    │   │   │   ├── MatchProposalEntity.java
    │   │   │   ├── MatchProposalPlayerEntity.java
    │   │   │   ├── WalletOperationEntity.java
    │   │   │   └── OutboxEntity.java
    │   │   ├── enums/
    │   │   │   ├── MatchMode.java
    │   │   │   ├── ProposalStatus.java
    │   │   │   ├── PlayerProposalStatus.java
    │   │   │   ├── WalletOperationStatus.java
    │   │   │   └── MatchmakingError.java
    │   │   ├── exception/
    │   │   │   ├── MatchmakingApiException.java
    │   │   │   ├── MatchmakingExceptionHandler.java
    │   │   │   └── MatchmakingExceptions.java
    │   │   ├── mapper/
    │   │   │   └── QueueStatusMapper.java
    │   │   ├── model/
    │   │   │   └── QueueEntry.java
    │   │   ├── repository/
    │   │   │   ├── MatchProposalRepository.java
    │   │   │   ├── MatchProposalPlayerRepository.java
    │   │   │   ├── WalletOperationRepository.java
    │   │   │   └── OutboxRepository.java
    │   │   ├── service/
    │   │   │   ├── normal/       # interface + impl/ + worker/
    │   │   │   ├── rank/         # interface + impl/ + worker/
    │   │   │   ├── party/        # interface + impl/
    │   │   │   ├── proposal/     # interface + impl/
    │   │   │   ├── store/        # interface + redis/
    │   │   │   └── client/       # interface + http/ + stub/
    │   │   └── util/MatchmakingIds.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           ├── V1__create_matchmaking_schema.sql
    │           └── V2__create_outbox.sql
    └── test/
        ├── java/com/msspoker/matchmakingservice/
        │   ├── unit/
        │   ├── integration/
        │   └── contract/
        └── resources/application-test.properties
```

Giống `game-service`, các package theo tầng nằm trực tiếp dưới package gốc. Chỉ tạo
`security`, `websocket` hoặc package tích hợp khác khi Matchmaking thật sự có trách
nhiệm đó; Matchmaking không sở hữu WebSocket theo SPEC nên không tạo package rỗng.

Quy tắc phụ thuộc giữa các package:

```text
controller -> service interface -> service implementation
service implementation -> repository / QueueStore / external client / outbox
external client -> HTTP DTO của đúng service đích
entity và Redis model không được trả thẳng ra controller
```

Controller chỉ nhận header/request, gọi một use case và trả response. Không đặt logic
ghép trận, gọi nhiều service hoặc xử lý hoàn phí trong controller.

## 4. Sở hữu dữ liệu

### PostgreSQL — nguồn bền vững

Tối thiểu có các bảng:

1. `match_proposals`
   - `id`, `mode`, `status`, `entry_fee`, `elo_min`, `elo_max`.
   - `accept_deadline`, `table_id`, `match_id`, `failure_code`.
   - `created_at`, `updated_at`, `version`.
2. `match_proposal_players`
   - `proposal_id`, `account_id`, `elo_snapshot`, `queue_joined_at`.
   - `status`, `accepted_at`, `fee_charged`, `fee_refunded`.
   - Unique `(proposal_id, account_id)`.
3. `wallet_operations`
   - `proposal_id`, `account_id`, `operation_type`, `idempotency_key`, `status`.
   - Unique `idempotency_key` để retry an toàn.
4. `matchmaking_outbox`
   - Event envelope, payload JSON, trạng thái publish và số lần retry.

PostgreSQL là nguồn sự thật cho workflow có tiền và audit. Không chỉ lưu proposal ở Redis.

### Redis — dữ liệu tạm thời

Key phải có prefix service và version:

```text
mm:v1:queue:RANK
mm:v1:queue:NORMAL
mm:v1:entry:{accountId}
mm:v1:reservation:{accountId}
mm:v1:proposal:{proposalId}
mm:v1:in-game:{accountId}
mm:v1:lock:finder:{mode}
mm:v1:cooldown:{accountId}
```

- Queue Rank dùng Sorted Set; score ưu tiên theo thời điểm vào hàng.
- Metadata entry lưu accountId, Elo snapshot, win/loss streak và joinedAt.
- `in-game` lưu `IN_GAME`, mode, proposalId, tableId, matchId và địa chỉ Game public để reconnect.
- Chỉ trả thông tin reconnect khi Game vẫn xác nhận đúng tableId/matchId; record stale phải bị xóa.
- Mọi key tạm phải có TTL hợp lý; lock phải có lease time.
- Redis mất dữ liệu không được làm mất thông tin thu/hoàn phí đã ghi PostgreSQL.
- Không dùng `KEYS`; dùng key trực tiếp hoặc `SCAN` khi thật sự cần.
- Đợt 2 chỉ hỗ trợ **Redis standalone**. Các Lua operation chạm nhiều account/key nên
  chưa tương thích Redis Cluster; không được bật Cluster cho tới khi key được thiết kế
  lại với hash tag và có test CROSSSLOT.
- Lua script phải nằm trong `src/main/resources/redis/*.lua`, không nhúng Java text block.
- `enqueue`, `cancel`, `reserve`, `complete`, `requeue` và compare-delete lock phải nguyên tử.
- Finder lock chỉ bảo vệ chọn nhóm và reserve. Phải thả lock ngay sau reserve, trước mọi
  thao tác PostgreSQL hoặc HTTP tới Game.

## 5. API public dự kiến

Các route đi qua Gateway và lấy danh tính từ `X-User-Id`:

| Method | Endpoint | Ý nghĩa |
|---|---|---|
| `POST` | `/api/matchmaking/normal/join` | Vào hàng chờ Normal |
| `DELETE` | `/api/matchmaking/normal/leave` | Rời hàng chờ Normal |
| `GET` | `/api/matchmaking/normal/status` | Trạng thái queue/proposal/table Normal hiện tại |
| `GET` | `/api/matchmaking/normal/reconnect` | Lấy bàn Normal đang hoạt động để client nối lại |
| `GET` | `/api/matchmaking/proposals/{id}` | Đọc proposal của thành viên |
| `POST` | `/api/matchmaking/proposals/{id}/accept` | Chấp nhận trong thời hạn |
| `POST` | `/api/matchmaking/proposals/{id}/decline` | Từ chối proposal |

Không nhận `accountId` từ body cho thao tác của người dùng. Luôn dùng `X-User-Id` do
Gateway gắn. Client tự gửi header này phải bị Gateway loại bỏ.

`/internal/**` không được route công khai qua Gateway.

### 5.1. Quy tắc đồng bộ tên API, Swagger và code

Tên public API là hợp đồng chính thức và phải phản ánh đúng mode cùng hành động:

```text
/api/matchmaking/{mode}/{action}
```

- `{mode}` viết thường: `normal`, `rank`.
- `{action}` dùng đúng nghiệp vụ: `join`, `leave`, `status`, `reconnect`; không dùng route chung
  mơ hồ như `/queue` rồi truyền `mode` trong request body.
- Mỗi mode có controller và service riêng. Ví dụ Normal phải dùng
  `NormalMatchmakingController`, `NormalMatchmakingService` và
  `NormalMatchmakingServiceImpl`.
- Method trong controller/service dùng tên hành động giống API: `join`, `leave`,
  `status`, `reconnect`. Tên kỹ thuật tầng hạ tầng như `QueueStore` hoặc `RedisQueueStore` được
  giữ theo trách nhiệm nội bộ, không phải đổi theo route public.
- Swagger `operationId` phải duy nhất trên toàn service và có mode trong tên:
  `joinNormal`, `leaveNormal`, `getNormalStatus`, `reconnectNormal`; Rank tương ứng dùng
  `joinRank`, `leaveRank`, `getRankStatus`, `reconnectRank`.
- Swagger chỉ công bố endpoint đã implement. Không tạo endpoint giả cho Rank hoặc
  Custom chỉ để hiện nhóm trên giao diện.
- Custom Room thuộc `competition-service`, không đặt route Custom trong
  `matchmaking-service`.
- Khi đổi public API, phải cập nhật đồng thời controller, Swagger annotation,
  test, `README.md`, tài liệu spec và client gọi API trong cùng thay đổi.
- Không nhận `accountId` hay `mode` từ body của Normal join/leave/status;
  `accountId` lấy từ `X-User-Id`, còn mode được xác định bởi route `/normal/`.

## 6. Hợp đồng outbound bắt buộc

### Ranking

```http
GET /internal/ranking/{accountId}
```

Chỉ lấy `{ elo, tier, winStreak, lossStreak }`. Matchmaking không tự tính Elo.

### Identity/Auth restrictions

```http
GET /internal/accounts/{accountId}/restrictions
```

Rank bị từ chối nếu `banned = true` hoặc `rankBannedUntil` còn hiệu lực.

### Wallet

```http
GET  /internal/wallet/{accountId}/balance
POST /internal/wallet/debit
POST /internal/wallet/credit
```

- Debit: `reason = RANK_ENTRY_FEE`.
- Debit key: `rank-fee:{proposalId}:{accountId}`.
- Refund: `reason = RANK_ENTRY_REFUND`.
- Refund key: `rank-refund:{proposalId}:{accountId}`.
- Timeout mạng không đồng nghĩa thất bại; retry cùng idempotency key rồi đối soát.

### Game

```http
GET  /internal/match-settings/{mode}
POST /internal/tables
```

- Game Match Settings là nguồn sự thật cho `minPlayers` và `maxPlayers` của từng mode.
- Bản ghi mặc định của `NORMAL` và `RANK` là `minPlayers = 2`, `maxPlayers = 6`.
- Admin thay đổi Match Settings thì worker phải dùng giá trị mới cho những lượt ghép tiếp theo.
- Không dùng giá trị mặc định cục bộ khi không đọc được Game; giữ người chơi trong queue và trả lỗi dependency rõ ràng để tránh Game từ chối bàn do lệch cấu hình.

```json
{
  "mode": "RANK",
  "sourceId": "proposalId",
  "playerIds": ["uuid"],
  "settings": null,
  "entryFee": 100
}
```

`sourceId` luôn là `proposalId`, vì Game idempotent theo `(mode, sourceId)`. Retry phải
dùng cùng proposalId, không sinh sourceId mới. Với Normal, `entryFee = 0`.

## 7. Event outbound

Phát qua transactional outbox vào exchange `poker.events`:

- `notification.requested` type `MATCH_FOUND`: chứa accountIds, proposalId,
  mode và acceptDeadline.
- `notification.requested` type `MATCH_READY`: chứa accountIds, proposalId,
  tableId và matchId.

Event envelope phải có `eventId`, `eventType`, `occurredAt`, `source` và `payload`.
Publisher retry event chưa gửi; chỉ đánh dấu `PUBLISHED` sau khi broker xác nhận.

## 8. State machine

### Normal CURRENT — ownership đã chốt

| Trạng thái | Nguồn sự thật | Ý nghĩa |
|---|---|---|
| `NONE` | Kết quả đối chiếu Redis + PostgreSQL | Không chờ, không có proposal hiện hành |
| `WAITING` / `COUNTDOWN` | Redis | Entry còn trong sorted set; countdown là dữ liệu tạm |
| `RESERVED` / `MATCHING` | Redis | Nhóm đã được reserve nguyên tử, proposal có thể chưa commit |
| `CREATING_TABLE` | PostgreSQL | Proposal đã commit; từ đây PostgreSQL thắng Redis |
| `READY` | PostgreSQL | Audit tạo bàn thành công; Game xác nhận bàn còn active |
| `FAILED` | PostgreSQL | Audit terminal; không phải trạng thái hiện tại trả vô thời hạn |
| `IN_GAME` | Game; Redis chỉ cache reconnect | Người chơi đang ở bàn active |

Ranh giới ownership là commit `match_proposals`: trước commit, Redis sở hữu queue/reservation;
sau commit, PostgreSQL sở hữu workflow proposal. Redis entry/reservation còn sót sau ranh giới
chỉ là dữ liệu tạm còn sót, không được ghi đè trạng thái PostgreSQL.

`READY` và `FAILED` là lịch sử/audit. `READY` chỉ được xem là hiện tại khi Game còn xác nhận
bàn active; `FAILED` phải dẫn tới `WAITING` hoặc `NONE`, không được trả như status hiện tại mãi.

### Queue entry

```text
NONE -> WAITING -> RESERVED -> MATCHED
          |           |
          v           v
       CANCELLED   WAITING (proposal fail/expire)
```

Một account chỉ có tối đa một queue entry hoặc một proposal chưa kết thúc.

### Proposal

```text
WAITING_ACCEPTANCE
  -> EXPIRED                 (quá 10 giây)
  -> DECLINED                (một người từ chối)
  -> CHARGING                (tất cả chấp nhận, chỉ Rank)
  -> CREATING_TABLE          (Normal đi thẳng bước này)
  -> READY                   (Game trả tableId/matchId)
  -> REFUND_PENDING          (đã debit nhưng không tạo được bàn)
  -> FAILED                  (kết thúc sau khi hoàn phí/an toàn để retry)
```

Mọi chuyển trạng thái phải kiểm tra trạng thái hiện tại và dùng optimistic locking hoặc
atomic update. Request accept lặp lại phải trả kết quả hiện tại, không tạo charge lần hai.

## 9. Luồng Rank

1. Nhận yêu cầu vào queue; kiểm tra cooldown, không thuộc Party và chưa có queue/proposal khác.
2. Gọi restrictions và Ranking. Elo `0` hợp lệ; Elo dùng để ghép, không phải điều kiện mở khóa Rank.
   Nhánh hiện tại chưa có contract balance thật từ `wallet-service`; số dư được xác nhận nguyên tử
   bằng debit sau khi tất cả người chơi accept để tránh lỗi TOCTOU.
3. Ghi entry vào Redis với snapshot Elo/streak và thời điểm chờ.
4. Match finder đọc `minPlayers`/`maxPlayers` hiện tại từ Game Match Settings rồi chọn người chờ lâu nhất làm anchor.
5. Lọc ứng viên trong Elo window cố định; ưu tiên Elo gần anchor, streak là tiêu chí phụ và `joinedAt` phá hòa.
6. Khi đủ `minPlayers`, bắt đầu fill countdown; tiếp tục nhận người và tạo nhóm ngay khi đủ `maxPlayers`.
7. Hết fill countdown với từ `minPlayers` đến `maxPlayers` thì reserve atomically toàn bộ nhóm. Nếu tụt dưới `minPlayers` thì reset countdown.
8. Tạo proposal PostgreSQL với `acceptDeadline` 10 giây; Redis reservation chỉ là dữ liệu tạm.
9. Phát `MATCH_FOUND`.
10. Khi tất cả accept, đổi proposal sang `CREATING_TABLE`; cờ phí từng player lưu tiến độ saga.
11. Debit từng người bằng idempotency key ổn định và lưu kết quả từng người.
12. Nếu một debit thất bại, refund những người đã debit và đưa người hợp lệ về queue.
13. Nếu debit đủ, gọi Game CreateTable bằng `sourceId = proposalId`.
14. Nếu Game timeout, retry cùng sourceId trước khi quyết định refund.
15. Nếu tạo bàn thành công, lưu tableId/matchId, chuyển `READY`, xóa reservation và
    phát `MATCH_READY`.
16. Nếu chắc chắn tạo bàn thất bại, refund toàn bộ rồi kết thúc proposal.

## 10. Luồng Normal

- Không gọi Ranking, restrictions Rank hay Wallet.
- Không thu phí, `entryFee = 0`.
- Theo SPEC hiện tại không có popup accept; đủ người thì tạo bàn ngay.
- Vẫn phải reserve atomically và gọi Game với `sourceId = proposalId`.
- Nếu tạo bàn lỗi, trả người chơi về queue nếu họ chưa hủy.

## 11. Thuật toán ghép Rank

Thông số phải nằm trong config, không hard-code:

```properties
matchmaking.rank.entry-fee=100
matchmaking.rank.elo-window=100
matchmaking.rank.fill-timeout-seconds=10
matchmaking.proposal.accept-timeout-seconds=10
matchmaking.decline-cooldown-seconds=120
matchmaking.finder.fixed-delay-ms=1000
```

Quy tắc:

- Chọn người chờ lâu nhất làm anchor.
- Elo window là ngưỡng cố định lấy từ config; không mở rộng theo thời gian chờ.
- Elo `0` hợp lệ và không có ngưỡng Elo tối thiểu để vào Rank.
- Ưu tiên Elo gần anchor, sau đó ưu tiên joinedAt cũ hơn.
- `winStreak/lossStreak` chỉ là tiêu chí phụ; không được làm người chơi chờ vô hạn.
- Không ghép một account hai lần; reservation phải atomic.
- Khi chưa đủ người, giữ nguyên queue và chờ vòng tiếp theo.
- Số người tối thiểu/tối đa lấy trực tiếp từ Match Settings của Game. Giá trị mặc định là `2–6`;
  khi Admin cập nhật thì worker áp dụng cho lượt ghép tiếp theo.
- Fill countdown và proposal accept timeout là hai timer khác nhau: timer đầu dùng để lấp bàn,
  timer sau cho người chơi xác nhận proposal.

## 12. Xử lý lỗi và khả năng phục hồi

- Mọi lỗi public theo `ErrorResponse`, ví dụ:
  - `MM_ALREADY_QUEUED`
  - `MM_ACTIVE_PROPOSAL`
  - `MM_RANK_RESTRICTED`
  - `MM_INSUFFICIENT_BALANCE`
  - `MM_PROPOSAL_EXPIRED`
  - `MM_NOT_PROPOSAL_MEMBER`
  - `MM_PROPOSAL_NOT_ACCEPTABLE`
  - `MM_DEPENDENCY_UNAVAILABLE`
- Mang `X-Request-Id` qua mọi outbound request và log.
- Có timeout ngắn cho HTTP client; retry chỉ cho thao tác idempotent.
- Dùng scheduler recovery để xử lý proposal kẹt ở `CHARGING`, `CREATING_TABLE`
  hoặc `REFUND_PENDING` sau khi service restart.
- Không nuốt lỗi hoàn phí. Proposal chỉ kết thúc an toàn khi mọi khoản cần hoàn đã
  thành công hoặc còn ở trạng thái retry được.
- Không log JWT, secret, toàn bộ header cookie hoặc dữ liệu nhạy cảm.

## 13. Quy tắc transaction và concurrency

- Không giữ transaction database trong lúc gọi HTTP service khác.
- Ghi trạng thái trước khi gọi dependency; sau response mở transaction mới để cập nhật.
- Dùng `@Version` hoặc conditional update để chống hai worker xử lý cùng proposal.
- Dùng Redis Lua/script hoặc transaction để reserve nhiều account atomically.
- Unique constraint là lớp bảo vệ cuối cùng, không chỉ dựa vào kiểm tra ở Java.
- Không dùng distributed transaction 2PC giữa Matchmaking, Wallet và Game.
  Dùng saga + idempotency + compensation refund.

## 14. Thứ tự triển khai

1. **Khung service:** dependency, config, PostgreSQL, Redis, Flyway, exception chuẩn.
2. **Queue Normal:** join/cancel/status, Redis store và atomic reservation.
3. **CreateTable Normal:** proposal audit, Game client, retry idempotent.
4. **Queue Rank:** Ranking/Restriction/Wallet balance client và Elo window.
5. **Proposal accept:** TTL 10 giây, accept/decline, cooldown 2 phút.
6. **Saga thu phí:** debit, refund, recovery job và audit.
7. **Outbox notification:** `MATCH_FOUND`, `MATCH_READY`.
8. **Integration test:** PostgreSQL + Redis + mock HTTP/RabbitMQ.
9. **FE matchmaking:** queue status, popup accept, điều hướng `/table/{tableId}`.
10. **GĐ2 Party:** chỉ bắt đầu sau khi Rank/Normal ổn định.

Không code Party chung với MVP Rank/Normal trong cùng đợt đầu.

## 15. Test bắt buộc

### Unit test

- Elo window cố định đúng config; Elo `0` vẫn được ghép.
- Chọn đủ người, ưu tiên người chờ lâu và Elo gần.
- Không đủ người thì không tạo proposal.
- Accept trùng không tạo debit trùng.
- Decline/timeout áp cooldown và trả người còn lại về queue.
- Normal không gọi Wallet/Ranking.

### Integration test

- Hai finder chạy đồng thời không chọn trùng account.
- Redis restart không làm mất audit của proposal có tiền.
- Một debit thất bại: các debit trước được refund đúng một lần.
- Game timeout rồi retry: chỉ tạo một bàn nhờ cùng `sourceId`.
- Service restart giữa debit và CreateTable: recovery tiếp tục đúng bước.
- Outbox publish trùng không tạo hai thông báo logic ở consumer idempotent.
- Người ngoài proposal không thể accept.
- Proposal hết hạn không thể chuyển sang READY.

### Contract test

- Payload Wallet debit/credit đúng reason và idempotency key.
- Payload Game CreateTable đúng mode, sourceId, playerIds và entryFee.
- Ranking/Restriction response thiếu hoặc sai field được xử lý thành lỗi dependency rõ ràng.

## 16. Definition of Done

- `mvn -pl matchmaking-service -am clean verify` chạy qua bằng Java 21.
- Migration chạy được trên PostgreSQL sạch và nâng cấp được từ version trước.
- Service chạy bằng Compose với PostgreSQL, Redis và RabbitMQ.
- Rank và Normal chạy end-to-end qua Gateway đến Game.
- Không có truy cập chéo database.
- Không có hard-code URL, fee, timeout, player count hoặc Elo window.
- API/event khớp SPEC; thay đổi hợp đồng đã cập nhật SPEC và có review.
- Có log theo requestId/proposalId/accountId nhưng không lộ secret.
- Có test concurrency, timeout, retry, debit một phần và refund.
- README ghi cách chạy, biến môi trường, endpoint và ví dụ request.

## 17. Quy tắc Git

- Nhánh đề xuất: `duy/matchmaking-queue`.
- Commit Conventional Commits, ví dụ:
  - `feat(matchmaking): add normal queue`
  - `feat(matchmaking): orchestrate rank proposal fees`
  - `test(matchmaking): cover partial debit refund`
- Một PR tập trung vào `matchmaking-service`; thay đổi root POM, root Compose, `common`
  hoặc `docs/SPEC.md` phải được đúng chủ sở hữu review.
