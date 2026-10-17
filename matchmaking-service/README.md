# matchmaking-service

Service ghép trận tại port `8083`. Trạng thái hiện tại: `NORMAL` hỗ trợ người chơi solo và Party;
`RANK` đã có queue, ghép Elo cố định, accept/decline, expiry, cooldown, Wallet saga và tạo bàn.

## Swagger / OpenAPI

Khi service đang chạy và `MATCHMAKING_SWAGGER_ENABLED=true` (mặc định ở local):

- Swagger UI: `http://localhost:8083/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8083/v3/api-docs`

Swagger test trực tiếp service nên phải nhập `X-User-Id`. Contract production yêu cầu Gateway lấy
identity từ JWT rồi ghi đè header này, nhưng `api-gateway` hiện chưa có JWT filter của Auth; không
được xem header client tự gửi là bảo mật production. Swagger hiện mô tả bốn API `NORMAL`, bốn API queue/reconnect `RANK`, hai API
proposal và năm API quản lý Party. Nó chưa bao phủ notification/outbox hoặc
kiểm thử E2E qua Gateway.

`Custom room` thuộc `competition-service`, không thuộc Swagger của Matchmaking.

## API Party

API giữ cách đặt tên hiện tại của module:

```http
POST   /api/party/create
POST   /api/party/invite
POST   /api/party/join
POST   /api/party/kick
DELETE /api/party/leave
```

Party được lưu trong Redis. Chỉ chủ party được mời hoặc kick; người được mời vào bằng mã gồm
8 ký tự; tối đa mặc định 6 thành viên. Khi chủ rời, quyền chủ chuyển cho thành viên vào sớm nhất;
người cuối cùng rời sẽ giải tán party.

Chủ Party dùng chính `POST /api/matchmaking/normal/join` để đưa toàn bộ nhóm vào queue và dùng
`DELETE /api/matchmaking/normal/leave` để hủy cho cả nhóm. Thành viên thường không được tự gọi
hai thao tác này. Worker coi mỗi Party là một khối, không tách thành viên sang hai trận và dùng
người chơi solo hoặc Party khác để lấp các ghế còn trống mà không vượt quá `maxPlayers`.

## API Normal

Danh tính lấy từ header `X-User-Id`; production chỉ được tin header sau khi Gateway JWT filter hoàn tất.

```http
POST /api/matchmaking/normal/join
X-User-Id: <account UUID>
```

```http
GET /api/matchmaking/normal/status
X-User-Id: <account UUID>
```

```http
DELETE /api/matchmaking/normal/leave
X-User-Id: <account UUID>
```

```http
GET /api/matchmaking/normal/reconnect
X-User-Id: <account UUID>
```

Join bị từ chối nếu account đã ở Redis queue, có proposal `CREATING_TABLE` trong PostgreSQL hoặc
Game service báo vẫn đang ở một bàn active. Leave xóa account khỏi Redis queue và reset countdown
nếu số người còn lại thấp hơn `minPlayers`.

Khi tạo bàn thành công, Matchmaking lưu `IN_GAME`, `proposalId`, `tableId`, `matchId`, host,
port và WebSocket path vào Redis theo từng account. Reconnect chỉ trả
`reconnectable=true` khi Redis có `IN_GAME` và Game service vẫn xác nhận đúng bàn đó. Record
Redis bị stale sẽ được xóa và API trả `reconnectable=false`.

Trạng thái hiện tại có thể là `NONE`, `WAITING`, `COUNTDOWN`, `MATCHING`, `CREATING_TABLE`
hoặc `READY`. `FAILED` chỉ được giữ làm audit, không trả vô thời hạn từ API status.
Normal không gọi Ranking/Wallet và không có popup accept.

## API Rank

```http
POST   /api/matchmaking/rank/join
GET    /api/matchmaking/rank/status
GET    /api/matchmaking/rank/reconnect
DELETE /api/matchmaking/rank/leave
POST   /api/matchmaking/proposals/{proposalId}/accept
POST   /api/matchmaking/proposals/{proposalId}/decline
X-User-Id: <account UUID>
```

Rank chỉ nhận người chơi solo; account đang thuộc Party sẽ bị từ chối. Join lấy snapshot
`elo`, `winStreak`, `lossStreak` từ Ranking; Elo `0` hợp lệ. Worker dùng người chờ lâu nhất làm
anchor, chỉ chọn account trong cửa sổ Elo cố định, sau đó ưu tiên độ lệch Elo, streak và
`joinedAt`. Không mở rộng cửa sổ Elo theo thời gian.

`minPlayers` và `maxPlayers` của cả Normal lẫn Rank được đọc từ Game Match Settings do Admin quản
lý, mặc định `2–6`. Đủ `minPlayers` thì chạy fill countdown 10 giây; đủ `maxPlayers` thì tạo
proposal ngay. Proposal Rank trả `PROPOSED` và `acceptDeadline` 10 giây. Người decline hoặc không
accept bị cooldown 2 phút; người còn lại được requeue giữ `joinedAt` và snapshot Elo/streak.

Khi tất cả accept, Matchmaking debit phí bằng key `rank-fee:{proposalId}:{accountId}`, rồi gọi Game
với `sourceId=proposalId`. Debit hoặc Game thất bại chắc chắn sẽ refund bằng key
`rank-refund:{proposalId}:{accountId}`; timeout dependency được recovery worker retry cùng key.

Contract đã đối chiếu: Ranking và Game có implementation thật. `identity-service` và
`wallet-service` trong nhánh hiện tại vẫn là scaffold, nên Docker Matchmaking mặc định bật stub cho
hai dependency này. Khi hai service merge API thật, đặt `MATCHMAKING_RESTRICTION_STUB_ENABLED=false`
và `MATCHMAKING_WALLET_STUB_ENABLED=false`.

## Cách hoạt động

1. Join ghi entry vào Redis Sorted Set theo `joinedAt`.
2. Worker đọc `minPlayers`/`maxPlayers` hiện tại từ Game Match Settings và lấy nhóm cũ nhất.
3. Lua reserve cả nhóm nguyên tử để hai worker không lấy trùng account.
4. PostgreSQL lưu proposal `CREATING_TABLE` và danh sách player.
5. Gọi `POST game-service/internal/tables` với `mode=NORMAL`, `entryFee=0` và
   `sourceId=proposalId`.
6. Thành công chuyển `READY`; lỗi 4xx chắc chắn thì `FAILED` và requeue.
7. Timeout/5xx giữ `CREATING_TABLE`; recovery retry cùng `sourceId` để không tạo hai bàn.

Ownership trạng thái: Redis là nguồn cho `WAITING/COUNTDOWN/RESERVED`; sau khi proposal commit,
PostgreSQL là nguồn cho `CREATING_TABLE/READY/FAILED`; Game là nguồn xác nhận `IN_GAME`.
Redis hiện chỉ hỗ trợ deployment standalone vì các Lua operation chạm nhiều key.

## Biến môi trường chính

| Biến | Mặc định |
|---|---|
| `SERVER_PORT` | `8083` |
| `MATCHMAKING_NORMAL_FILL_TIMEOUT` | `10s` |
| `MATCHMAKING_RANK_ELO_WINDOW` | `100` |
| `MATCHMAKING_RANK_FILL_TIMEOUT` | `10s` |
| `MATCHMAKING_RANK_ACCEPT_TIMEOUT` | `10s` |
| `MATCHMAKING_RANK_COOLDOWN` | `2m` |
| `MATCHMAKING_RANKING_STUB_ENABLED` | `false` |
| `MATCHMAKING_RESTRICTION_STUB_ENABLED` | `false` (`true` mặc định trong Compose hiện tại) |
| `MATCHMAKING_WALLET_STUB_ENABLED` | `false` (`true` mặc định trong Compose hiện tại) |
| `GAME_PUBLIC_HOST` | `localhost` |
| `GAME_PUBLIC_PORT` | `8082` |
| `GAME_WEBSOCKET_PATH` | `/ws/game` |
| `GAME_SESSION_TTL` | `24h` |
| `MATCHMAKING_FINDER_DELAY_MS` | `1000` |
| `MATCHMAKING_QUEUE_ENTRY_TTL` | `24h` |
| `MATCHMAKING_RESERVATION_TTL` | `2m` |
| `MATCHMAKING_GAME_TIMEOUT` | `3s` |
| `MATCHMAKING_GAME_STUB_ENABLED` | `false` |

Stub Game chỉ dùng khi phát triển độc lập:

```powershell
$env:MATCHMAKING_GAME_STUB_ENABLED='true'
```

## Build/test

Yêu cầu Java 21:

```powershell
.\mvnw.cmd -pl matchmaking-service -am clean verify
```

Nếu Windows đang trỏ `java` về Java 8, đặt `JAVA_HOME` tới JDK/JBR 21 trước khi chạy.

## Docker

```powershell
docker compose -f docker/compose.infra.yml -f matchmaking-service/compose.yml up -d --build
```

Compose dùng PostgreSQL `matchmaking-db`, Redis riêng `matchmaking-redis` và RabbitMQ
chung từ `docker/compose.infra.yml`.
