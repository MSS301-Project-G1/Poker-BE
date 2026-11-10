# Duy — kế hoạch triển khai matchmaking-service

Nguồn yêu cầu: `docs/SPEC.md` §3.1, §3.2, §5, §6.1, §6.2, §6.4,
§7.2, §7.3, §8, §9.3 và §12. Quy tắc chi tiết nằm tại
`matchmaking-service/RULE.md`; khi hai file mâu thuẫn, `docs/SPEC.md` có độ ưu tiên
cao nhất.

Phạm vi file này chỉ là `matchmaking-service` và FE `features/matchmaking`.
`social-service`, `chat-service` và `<ChatPanel>` là các đợt riêng, không đưa entity,
repository hoặc bảng của chúng vào Matchmaking.

## 1. Kết quả cuối cần đạt

### MVP

- Hàng chờ `NORMAL`: vào/hủy hàng chờ, ghép theo thời gian chờ, miễn phí, không gọi
  Ranking/Wallet và không có popup accept theo đề xuất hiện tại của SPEC.
- Hàng chờ `RANK`: lấy Elo/streak từ Ranking, chấp nhận Elo `0`, ghép trong cửa sổ
  Elo cố định và lấy `minPlayers`/`maxPlayers` từ Game Match Settings.
- Proposal Rank có hạn 10 giây; mọi người accept mới bắt đầu thu phí.
- Thu phí/hoàn phí qua Wallet bằng idempotency key; không tự sửa số dư.
- Tạo bàn qua Game `POST /internal/tables`; không tự tạo bàn hoặc chạy poker engine.
- Phát `notification.requested` loại `MATCH_FOUND`/`MATCH_READY` qua RabbitMQ để
  Social đẩy realtime.
- FE có màn chờ và popup chấp nhận trận.

### Giai đoạn 2

- Party: tạo, mời, kick, rời/chuyển chủ và chỉ chủ party được tìm trận Normal.
- Ghép party thiếu người với người lạ.

Không triển khai Custom Room, Tournament, Elo, ví, bạn bè hoặc chat trong module này.

## 2. Chuẩn đặt tên phải giữ thống nhất

| Thành phần | Quy ước | Ví dụ |
|---|---|---|
| Java package | chữ thường, theo tầng hiện có | `com.msspoker.matchmakingservice.service` |
| Java class | PascalCase, tên nói rõ vai trò | `RankMatchmakingService`, `ProposalExpiryWorker` |
| Method/field | camelCase, động từ trước | `joinQueue`, `reserveCandidates`, `acceptProposal` |
| REST | route theo mode và hành động, dưới `/api/matchmaking` | `/api/matchmaking/normal/join` |
| DB table/column | `snake_case` | `match_proposal_players`, `accepted_at` |
| Enum | `UPPER_SNAKE_CASE` | `WAITING_ACCEPTANCE`, `REFUND_PENDING` |
| Error code | tiền tố `MM_` | `MM_ALREADY_QUEUED`, `MM_PROPOSAL_EXPIRED` |
| Redis key | `mm:v1:<loại>:<định danh>` | `mm:v1:queue:rank`, `mm:v1:cooldown:{accountId}` |
| Wallet idempotency | theo đúng SPEC | `rank-fee:{proposalId}:{accountId}` |
| Event | tên event chung của SPEC | exchange `poker.events`, type `notification.requested` |

Không đưa display name vào queue/proposal bền vững. Matchmaking chỉ lưu `accountId`;
tên và avatar thuộc Profile API. Không đổi tên endpoint/event liên service chỉ để đẹp
hơn nếu chưa cập nhật SPEC và được service liên quan review.

## 3. Thuật toán cơ sở cho MVP

### Rank

1. Khi join, kiểm tra cooldown, kiểm tra không có queue/proposal đang hoạt động và gọi
   Ranking lấy `{elo, winStreak, lossStreak}`.
2. Lưu payload tạm trong Redis Hash; đưa `accountId` vào Sorted Set với score là
   `joinedAt` epoch milliseconds.
3. Worker đọc `minPlayers`/`maxPlayers` hiện tại từ Game Match Settings rồi chọn người
   chờ lâu nhất làm anchor. Mặc định Game cung cấp `2–6`; Admin đổi thì dùng giá trị mới.
4. Lọc ứng viên trong cửa sổ Elo cố định của anchor. Elo `0` hợp lệ. Elo là tiêu chí chính; streak chỉ là
   tiêu chí phụ để sắp ứng viên có Elo tương đương, không tự biến đổi Elo.
5. Đủ `minPlayers` thì bắt đầu fill countdown; đủ `maxPlayers` thì tạo nhóm ngay, còn
   hết countdown thì dùng nhóm hiện có. Reserve nguyên tử để chỉ một worker được tạo proposal.
6. Ghi proposal và players vào PostgreSQL, đặt accept deadline 10 giây, phát `MATCH_FOUND`.
7. Tất cả accept: debit từng người, gọi Game, lưu `tableId`/`matchId`, phát
   `MATCH_READY`.
8. Decline/timeout/lỗi: thực hiện compensation, cooldown đúng đối tượng và requeue
   người hợp lệ với `joinedAt` cũ để không làm mất thứ tự chờ.

### Normal

Ownership đã chốt cho Đợt 2: Redis sở hữu queue/countdown/reservation trước khi proposal commit;
PostgreSQL sở hữu workflow từ `CREATING_TABLE`; Game xác nhận bàn `IN_GAME`. `READY/FAILED` là
audit terminal, không được trả như trạng thái hiện tại vô thời hạn.

1. Lưu queue bằng Redis, ưu tiên `joinedAt`.
2. Có ít hơn `minPlayers` thì tiếp tục chờ và chưa tạo countdown.
3. Đạt `minPlayers` thì tạo `matchStartDeadline` sau 10 giây và tiếp tục gom người.
4. Đạt `maxPlayers` trước deadline thì reserve nguyên tử và tạo trận ngay.
5. Hết deadline với từ `minPlayers` đến `maxPlayers - 1` thì reserve nguyên tử số người hiện có.
6. Tụt xuống dưới `minPlayers` trước deadline thì hủy/reset countdown.
7. Gọi Game ngay sau khi reserve, không Ranking, không Wallet, không proposal accept.
8. Game lỗi thì release reservation và requeue giữ thời điểm chờ cũ.

`minPlayers` và `maxPlayers` thuộc Game Match Settings. `normalFillTimeout`, `rankFillTimeout`,
Elo window cố định, `acceptTimeout` và `cooldown` thuộc `MatchmakingProperties`; không hard-code
trong service/worker. Không giữ bản sao `minPlayers`/`maxPlayers` làm nguồn sự thật trong Matchmaking.

## 4. Các đợt triển khai

Mỗi đợt phải có test và `mvn -pl matchmaking-service -am clean verify` xanh trước khi
sang đợt tiếp theo.

### Đợt 0 — nền module

- Dependency JPA/PostgreSQL, Flyway, Redis, RabbitMQ, Validation và H2 cho test
  context/JPA đơn giản. Redis/concurrency phải test bằng Testcontainers.
- `application.properties` đọc biến Compose; RabbitMQ dùng broker chung.
- `compose.yml` có PostgreSQL/Redis riêng, không tạo RabbitMQ riêng.
- Migration V1 tạo `match_proposals`, `match_proposal_players`,
  `wallet_operations`, `matchmaking_outbox`.
- Chuẩn lỗi `MatchmakingError`, `MatchmakingApiException`,
  `MatchmakingExceptionHandler`.

Tiêu chí: context test chạy và Flyway validate schema thành công.

### Đợt 1 — domain và persistence

- Tạo enum: `MatchMode`, `ProposalStatus`, `ProposalPlayerStatus`,
  `WalletOperationType`, `WalletOperationStatus`.
- Tạo entity/repository đúng V1; dùng optimistic locking qua `version`.
- Tạo DTO mapper riêng; controller không trả entity JPA.
- Bổ sung repository query tìm proposal hết hạn và wallet operation cần retry.

Tiêu chí: repository test chứng minh unique player/proposal, optimistic lock và các
check constraint quan trọng.

### Đợt 2 — queue core và Normal end-to-end

- `MatchmakingProperties` có validate cho toàn bộ tham số.
- `QueueStore` interface và `RedisQueueStore` implementation.
- Join/cancel/status API; một account chỉ ở một queue/proposal hoạt động.
- Join kiểm tra cả proposal `CREATING_TABLE` trong PostgreSQL và active table từ Game; trận lịch sử đã kết thúc không chặn queue mới.
- Leave xóa queue entry khỏi Redis; nếu số người còn lại dưới `minPlayers` thì reset countdown.
- Lua script reserve/release/requeue nguyên tử.
- `NormalMatchWorker` lấy nhóm theo thời gian chờ và gọi `GameClient`.
- `GameClient` production và stub dev/test theo interface.

Tiêu chí: hai worker chạy đồng thời không lấy trùng account; Normal không gọi
Ranking/Wallet; Game lỗi thì người chơi trở lại queue đúng thứ tự.

### Đợt 3 — admission và thuật toán Rank

- `RankingClient` lấy Elo/streak; `RestrictionClient` kiểm tra cấm nếu contract đã sẵn.
- Rank join từ chối khi cooldown, đang queued/proposed hoặc dependency trả lỗi.
- Worker dùng người chờ lâu nhất làm anchor, cửa sổ Elo cố định theo config, streak chỉ tie-break.
- Ghi metric wait time và Elo spread của nhóm được chọn.

Tiêu chí: unit test biên cửa sổ Elo cố định, min players và fairness cho
người chờ lâu; không có class nào tự tính/cập nhật Elo.

### Đợt 4 — proposal accept 10 giây

- Tạo proposal transactionally sau khi reserve.
- API `GET /api/matchmaking/proposals/{id}`,
  `POST .../{id}/accept`, `POST .../{id}/decline`.
- Accept/decline idempotent; account ngoài proposal nhận `403/404` phù hợp.
- `ProposalExpiryWorker` chuyển proposal hết hạn đúng một lần.
- Outbox tạo `MATCH_FOUND` cùng transaction với proposal.

Tiêu chí: race accept-vs-timeout chỉ có một trạng thái cuối; accept sau deadline không
thành công; người timeout/decline bị cooldown theo config.

### Đợt 5 — Wallet saga và Game

- `WalletClient` gọi debit/credit với timeout và retry có giới hạn.
- Ghi `wallet_operations` trước khi gọi dependency; dùng key
  `rank-fee:{proposalId}:{accountId}` và `rank-refund:{proposalId}:{accountId}`.
- Nếu một debit thất bại, hoàn tiền các debit đã thành công.
- Chỉ gọi Game sau khi toàn bộ debit thành công.
- Game lỗi chắc chắn: refund toàn bộ. Game timeout/không rõ kết quả: không refund mù;
  giữ trạng thái `CREATING_TABLE` để retry idempotent cùng `sourceId`.

Tiêu chí: test mọi điểm lỗi sau từng debit, retry không trừ/hoàn hai lần và tiến trình
khôi phục được sau khi restart.

### Đợt 6 — event/outbox và quan sát hệ thống

- `OutboxPublisher` lấy batch pending, publish `notification.requested` lên
  `poker.events`, chỉ đánh dấu `published_at` sau publisher confirm.
- Retry exponential backoff có jitter; event có `eventId`, `occurredAt`, type và data.
- Health check phân biệt PostgreSQL/Redis/RabbitMQ và các dependency outbound.
- Metric tối thiểu: queue size, wait p50/p95, Elo spread p50/p95, proposal accept/
  decline/timeout, refund pending, outbox pending/oldest age, duplicate reservation.

Tiêu chí: restart giữa DB commit và publish không mất event; publish trùng có cùng
`eventId` để consumer deduplicate.

### Đợt 7 — FE và kiểm thử tích hợp

- `features/matchmaking`: API client, query/mutation hooks, màn queue và trạng thái lỗi.
- Popup accept Rank hiển thị countdown theo `acceptDeadline` từ server, không tự lấy
  thời điểm mở popup làm deadline.
- Nhận `MATCH_FOUND`/`MATCH_READY` qua kênh realtime của Social; polling chỉ là
  fallback tạm cho dev.
- Kiểm thử luồng FE: Normal thành công, Rank thành công, decline, timeout, debit lỗi,
  Game lỗi, dependency timeout và reconnect.

Tiêu chí: FE build/lint/test xanh và contract API khớp SPEC.

### Đợt 8 — GĐ2 Party

- [x] API quản lý Party: create, invite, join bằng mã, kick và leave/chuyển chủ.
- [x] Dữ liệu Party và membership lưu Redis; thao tác nhiều key dùng Lua nguyên tử.
- [x] Chỉ chủ party đưa cả nhóm vào queue Normal.
- [x] Worker chọn/reserve party như một đơn vị, không tách thành viên sang hai trận.
- [x] Ghép thêm người lạ hoặc Party khác khi nhóm chưa đủ `maxPlayers`.

## 5. Dependency contract và mock

| Dependency | Contract production | Mock dev/test |
|---|---|---|
| Ranking | `GET /internal/ranking/{accountId}` | Elo/streak bằng `0` |
| Wallet | balance/debit/credit | thành công, vẫn deduplicate key |
| Game | `POST /internal/tables` | UUID ngẫu nhiên; tool dev có thể finish sau 10 giây |
| Identity | restrictions/penalty | không bị phạt |

Mock được chọn bằng Spring profile/bean, không viết `if (dev)` trong business logic và
không fallback âm thầm sang mock ở production.

## 6. Tiến độ hiện tại

### Swagger / OpenAPI (bổ sung 2026-10-15)

- [x] Thêm Springdoc và Swagger UI cho bốn API Normal hiện có: join, leave, status và reconnect.
- [x] Mô tả header `X-User-Id`, request `NORMAL`, response status và các HTTP status chính.
- [x] Cho phép tắt bằng `MATCHMAKING_SWAGGER_ENABLED=false`.
- [x] Swagger có API Rank join, leave và status.
- [x] Swagger có proposal accept/decline; expiry chạy bằng worker.
- [x] Wallet debit/refund được triển khai qua HTTP client và idempotency key; Compose dùng stub
  vì `wallet-service` hiện vẫn là scaffold.
- [ ] Swagger không kiểm chứng notification/outbox/RabbitMQ vì Đợt 6 chưa triển khai.
- [ ] Chưa có automated OpenAPI contract test để phát hiện breaking change.

Swagger hiện là công cụ test contract HTTP của flow `NORMAL`, Party và Rank core; các mục
hardening thuộc phạm vi Đợt 2 đã được kiểm chứng riêng bằng bộ test bên dưới.

- [x] Đợt 0: dependency, cấu hình, Compose, Flyway V1 và exception chuẩn.
- [x] Đợt 1 — phạm vi Normal: entity/repository proposal, DTO và MapStruct mapper;
  context JPA/Flyway chạy qua. Domain Wallet/Rank đã được bổ sung ở Đợt 3–5.
- [x] Đợt 2 — Normal core đã hoàn thành trong phạm vi đã chốt:
  - [x] API join/leave/status/reconnect lấy danh tính từ `X-User-Id`.
  - [x] Trạng thái hiện tại `NONE`, `WAITING`, `COUNTDOWN`, `MATCHING`, `CREATING_TABLE`,
    `READY`; reconnect dùng cache `IN_GAME` và xác nhận lại với Game. `FAILED` chỉ là audit.
  - [x] Redis Sorted Set ưu tiên `joinedAt`; lưu payload queue riêng.
  - [x] Lua reserve nguyên tử để nhiều worker không lấy trùng account.
  - [x] `NormalMatchWorker` lấy nhóm đủ `minPlayers` từ config.
  - [x] Lưu proposal và players vào PostgreSQL trước khi gọi Game.
  - [x] `HttpGameClient` gọi Game với `sourceId = proposalId`; có `StubGameClient` cho dev/test.
  - [x] Game từ chối chắc chắn thì đánh dấu `FAILED` và requeue giữ `joinedAt` cũ.
  - [x] Timeout/5xx giữ `CREATING_TABLE`; recovery retry cùng `sourceId`.
  - [x] Unit/context, controller contract và Testcontainers Redis concurrency: 44 test đều pass.
- [x] Đợt 3: Rank join/leave/status, Ranking/restriction snapshot, Elo `0`, cửa sổ Elo cố định,
  Game Match Settings, fill countdown và cooldown Redis.
- [x] Đợt 4: proposal accept/decline/timeout, khóa DB chống double-start và requeue theo kết quả.
- [x] Đợt 5: Wallet debit/refund idempotent, compensation recovery và Game CreateTable Rank.
  Tổng suite Matchmaking hiện có 77 test pass. Wallet/Identity production còn chờ hai service
  scaffold cung cấp API thật; Compose đang dùng stub có ghi rõ.
- [ ] Đợt 6: outbox/event/observability.
- [ ] Đợt 7: FE.
- [x] Đợt 8: quản lý Party và tích hợp Party với queue Normal.

### Cập nhật 2026-10-14 — MapStruct và cấu trúc package

- [x] Thêm MapStruct `1.6.3` và `mapstruct-processor` vào `pom.xml`, cùng phiên bản với
  `game-service`, `ranking-service` và `competition-service`.
- [x] Chuyển `QueueStatusMapper` từ class `@Component` viết tay thành interface
  `@Mapper(componentModel = SPRING)`. Implementation được sinh khi compile tại
  `target/generated-sources/annotations`; không commit file generated.
- [x] Cập nhật unit test lấy mapper bằng `Mappers.getMapper(QueueStatusMapper.class)`.
- [x] Xóa hai package rỗng còn sót `service/queue` và `service/worker`. Implementation worker
  hiện nằm đúng tại `service/impl/worker`; các interface nằm trực tiếp trong `service`.
- [x] Chạy `mvn -pl matchmaking-service -am clean test`: 10 test, 0 failure, 0 error,
  bao gồm context/Flyway, queue service, coordinator, worker và Redis concurrency.
- [x] Snapshot trên đã được hoàn tất ngày 2026-10-15; xem kết quả hardening hiện tại bên dưới.

### Mục tiêu hardening Normal trước khi phát triển tính năng mới

Normal core Đợt 2 đã hoàn tất trong phạm vi đã chốt. Crash recovery, reconciliation,
PostgreSQL Testcontainers, HTTP integration và Docker E2E không thuộc tiêu chí đóng Đợt 2 hiện tại;
vì vậy kết quả này không được dùng như chứng nhận production-ready.

#### Tính nhất quán Redis ↔ PostgreSQL

- [x] Chốt state machine Normal và nguồn dữ liệu chính cho từng trạng thái. Sau khi proposal
  được tạo, PostgreSQL là nguồn sự thật; Redis chỉ giữ queue/reservation tạm thời.
  Quy tắc ownership đã ghi trong `RULE.md`.
- [x] Không để reservation TTL hết hạn làm API trả `WAITING` trong khi proposal DB vẫn
  `CREATING_TABLE`; status/cancel phải kiểm tra proposal active trong PostgreSQL.
- [x] `join()` từ chối account có proposal `CREATING_TABLE` trong PostgreSQL, kể cả khi Redis
  entry đã mất; đã có unit test cho nhánh này.
- [x] Định nghĩa thời điểm `READY` và `FAILED` không còn được xem là trạng thái hiện tại;
  không trả proposal lịch sử vô thời hạn từ API status.

#### Redis atomicity và khả năng mở rộng

- [x] Chuyển `requeue` thành một Lua operation nguyên tử gồm khôi phục payload, `ZADD` giữ
  `joinedAt` cũ và xóa reservation.
- [x] Rà soát `enqueue`, `cancel`, `reserve`, `complete`, `requeue` để mọi thao tác nhiều key
  quan trọng đều nguyên tử.
- [x] Tách Lua khỏi Java text block sang `src/main/resources/redis/*.lua` để dễ review,
  syntax-check và test độc lập.
- [x] Chốt phạm vi Đợt 2 chỉ hỗ trợ Redis standalone; Redis Cluster chưa được hỗ trợ vì các
  Lua invocation nhiều key chưa có hash tag/cùng hash slot.
- [x] Không giữ finder lock trong lúc gọi Game. Lock chỉ bảo vệ bước chọn/reserve; sau khi
  reserve thành công phải giải phóng để worker khác tiếp tục tạo nhóm mới.

Kiểm chứng hiện tại: Testcontainers Redis chạy Lua thật cho reserve cạnh tranh, complete và
requeue giữ nguyên `joinedAt`; unit test xác nhận finder lock được thả trước coordinator/Game.

#### Dọn code dựng trước — backlog, không chặn đóng Đợt 2

- [ ] Phân loại rõ code `CURRENT` và `FUTURE`: error code Rank/Wallet, URL dependency,
  RabbitMQ, `wallet_operations`, `matchmaking_outbox` và các field Rank chưa có business flow.
- [ ] Loại bỏ abstraction không tạo giá trị hoặc ghi chú rõ lý do giữ lại; ưu tiên giảm class
  chỉ bọc một dòng như `MatchmakingIds` và validator chỉ phục vụ một mode.
- [ ] Không đánh dấu dependency/event/table là đã triển khai chỉ vì đã có cấu hình hoặc migration.

#### Test bắt buộc để hoàn tất Đợt 2

- [x] Test reservation hết TTL khi proposal vẫn `CREATING_TABLE`.
- [x] Test cancel chạy đồng thời với reserve.
- [x] Test controller/API contract cho join, leave, status, reconnect và error response.
- Test RabbitMQ/outbox chỉ thực hiện khi chức năng event thực sự được triển khai; không thuộc
  phạm vi Normal core hiện tại.

Kết quả hiện tại: `mvn -pl matchmaking-service -am test` chạy 44 test, 0 failure, 0 error.

Tiêu chí hoàn tất: API status nhất quán với proposal DB, không ghép một account hai lần và các
thao tác Redis nhiều key quan trọng đều nguyên tử.

## 7. Thứ tự code ngay bây giờ

1. [x] Chốt state machine Normal và quy tắc Redis/PostgreSQL ownership.
2. [x] Sửa reservation TTL và bảo đảm status/leave ưu tiên proposal active trong PostgreSQL.
3. [x] Làm Redis requeue/cleanup nguyên tử, tách Lua ra resource và thu hẹp finder lock.
4. [ ] Dọn hoặc đánh dấu rõ code dành cho tương lai — backlog không chặn Đợt 2.
5. [x] Bổ sung concurrency test và controller/API test.
6. [x] Đóng Đợt 2 Normal core và triển khai Đợt 3 Rank core.

Lý do: Normal kiểm chứng queue, reservation và Game integration mà chưa kéo theo Elo,
deadline và compensation tiền. Khi lõi đó ổn, Rank chỉ bổ sung admission, selection,
proposal và saga lên cùng nền.

## 8. Audit phần thiếu và code dựng trước — 2026-10-16

### THIẾU — đã có khung nhưng chưa triển khai

- [ ] `wallet_operations`: migration đã tạo bảng nhưng chưa có Entity, Repository và Service ghi/đọc
  operation. Không xem bảng này là đã triển khai cho đến khi Wallet saga thực sự sử dụng nó.
- [ ] `matchmaking_outbox`: migration đã tạo bảng nhưng chưa có Entity, Repository,
  `OutboxPublisher`, retry và cơ chế đánh dấu `published_at` sau publisher confirm.
- [ ] RabbitMQ: đã có dependency, cấu hình và kết nối Compose nhưng chưa có publisher/consumer
  thực tế cho `notification.requested`; chưa có kiểm thử RabbitMQ/outbox.

### CẦN DỌN HOẶC GHI RÕ LÝ DO GIỮ

- [ ] `MatchmakingError.MM_FORBIDDEN`: chưa có nhánh nghiệp vụ sử dụng; xóa hoặc triển khai đúng
  trường hợp trả lỗi forbidden.
- [ ] `MatchmakingIds`: hiện chỉ bọc `UUID.randomUUID()`; inline hoặc ghi rõ đây là điểm mở rộng
  cho chính sách tạo ID chung.
- [x] Package rỗng: `service/queue` và `service/worker` đã được xóa; không còn là phần thiếu.

Quy ước trạng thái: có dependency/config/migration chỉ được tính là **khung chuẩn bị**. Một mục chỉ
được đánh dấu hoàn thành khi có business code, persistence/publish path và test tương ứng.

### Cập nhật 2026-10-24 — Chia package service theo chức năng

- Chuyển interface và implementation vào các nhóm `normal`, `rank`, `party`, `proposal`, `store`, `client` dưới `service`.
- Worker nằm trong `normal/worker` hoặc `rank/worker`; HTTP client ở `client/http`, stub ở `client/stub`, Redis implementation ở `store/redis`.
- Di chuyển test theo package tương ứng; cập nhật import, README, RULE và các đường dẫn tham khảo.
- Cấu trúc này thay thế cây `service/impl/worker` trong snapshot ngày 2026-10-14 ở trên.
- Kiểm tra: `mvn -pl matchmaking-service -am clean test` — 95 test, 0 failure, 0 error, 0 skipped.
