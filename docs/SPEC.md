# MS-Poker — Đặc tả dự án & Phân công

> Tài liệu này là nguồn sự thật cho các quyết định đã chốt và ranh giới sở hữu của nhóm. Các hợp đồng ở mục 6 vẫn là **đề xuất** cho đến khi cả nhóm chốt trong buổi họp tuần 0.
> Mọi thay đổi về **hợp đồng giữa các service** (API nội bộ, event, WebSocket) sau khi chốt phải sửa file này trước, qua Pull Request, và được người dùng API đó đồng ý.
> Các con số có ghi *(đề xuất)* là giá trị mặc định, nằm trong config để chỉnh sau, không cần họp lại.
>
> **Trạng thái repo:** trên nhánh triển khai của Bảo, game đã có PostgreSQL/Flyway, RabbitMQ outbox, REST và STOMP/JWT cùng FE game/Admin; xem `game-service/README.md`. Các phần còn lại vẫn cần chủ module tích hợp. Gateway đang có route mẫu theo tên service và phải đổi route/JWT theo mục 6.4 trước khi tích hợp production.
>
> **Cập nhật 2026-10-15:** nghiệp vụ tài khoản của Khanh nằm ở `auth-service` (port 8090); `identity-service` chỉ còn scaffold. Phần đã làm, phần còn thiếu và API đề xuất cho phần còn thiếu của Khanh, Duy ghi ở mục 9.1 và 9.3. API ghi *(đề xuất)* được phép đổi, nhưng phải sửa file này theo mục 4.2.

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Các quyết định đã chốt](#2-các-quyết-định-đã-chốt)
3. [Kiến trúc](#3-kiến-trúc)
4. [Quy tắc chống chồng chéo](#4-quy-tắc-chống-chồng-chéo)
5. [Quy ước kỹ thuật chung](#5-quy-ước-kỹ-thuật-chung)
6. [Hợp đồng giữa các service](#6-hợp-đồng-giữa-các-service)
7. [Các luồng nghiệp vụ chính](#7-các-luồng-nghiệp-vụ-chính)
8. [Tham số game mặc định](#8-tham-số-game-mặc-định)
9. [Phân công chi tiết từng người](#9-phân-công-chi-tiết-từng-người)
10. [Frontend](#10-frontend)
11. [Lộ trình](#11-lộ-trình)
12. [Definition of Done](#12-definition-of-done)

---

## 1. Tổng quan

Web app chơi **Poker Texas Hold'em** theo kiến trúc microservice. Nhóm 5 người: **Khanh, Bảo, Duy, Hoài Anh, Tùng**. Mỗi người sở hữu trọn vẹn (FE + BE) một nhóm service.

Dự án chia hai giai đoạn:

- 🟢 **MVP** — vòng chơi cốt lõi chạy được từ đầu đến cuối: đăng ký/đăng nhập → vào sảnh → ghép trận Rank/Normal hoặc tạo Custom Room → chơi một trận poker → nhận Elo và xu → điểm danh → chat → mua 1 loại skin (khung avatar) qua PayOS và thấy nó hiển thị.
- 🔵 **Giai đoạn 2 (GĐ2)** — giải đấu, mùa giải và quà, tutorial, đủ các loại skin, sticker/sound, party, chuông thông báo, báo cáo và xử phạt, trang Admin đầy đủ, hướng dẫn/FAQ.

---

## 2. Các quyết định đã chốt

### 2.1. Chế độ chơi

| Chế độ | Phí vào | Tính Elo | Thưởng xu | Ghi chú |
|---|---|---|---|---|
| `RANK` | Có (xu) | Có | Top 3 | Ghép trận tự động theo Elo |
| `NORMAL` | Miễn phí | Không | Không | Cho phép chơi cùng bạn bè, thiếu người thì ghép thêm người lạ |
| `CUSTOM` | Miễn phí | Không | Không | Tạo phòng bằng Room ID, Private/Public |
| `TOURNAMENT` | Tùy loại giải | Không *(đề xuất)* | Theo giải | Xem mục 2.4 |

### 2.2. Kinh tế

| | Xu | Tiền thật (VND) | Chip trong bàn |
|---|---|---|---|
| **Nguồn** | Quà lần đăng nhập đầu, điểm danh, thưởng Rank top 3, thưởng giải, quà mùa, thưởng tutorial | Người chơi thanh toán qua PayOS | Mọi người được phát bằng nhau khi vào bàn |
| **Dùng cho** | Phí vào Rank, phí vào Giải Mở, điểm danh bù | Mua skin, emoji, sound (mọi tier: Thường, VIP, Rất VIP) | Chỉ cược trong trận, hết trận là mất |

Các điều **không làm**: không tặng xu giữa người chơi, không rút xu ra tiền thật, không có mảnh skin. Skin chỉ có được bằng cách mua hoặc thắng giải Competitive.

### 2.3. Elo và xếp hạng

- Thứ hạng cuối trận Rank quyết định Elo: top 1 **+20**, top 2 **+10**, top 3 **+5**, từ hạng 4 trở xuống **bị trừ** (xem mục 8).
- 10 bậc: Tân thủ → Sắt → Đồng → Bạc → Vàng → Bạch kim → Kim cương → Kiện tướng → Đại kiện tướng → Thần bài. **Thần bài = top 50 trong nhóm Đại kiện tướng.**
- Mỗi mùa giải kết thúc: Elo bị trừ theo một tỷ lệ %, không về 0.
- Quà mùa được nhận **dồn**: đạt Kim cương thì nhận quà Kim cương và mọi bậc thấp hơn.

### 2.4. Competitive

| | Giải Cao thủ | Giải Mở |
|---|---|---|
| Ai được chơi | Top 32 bảng xếp hạng | Mọi người chơi |
| Cách vào | Hệ thống tự mời theo BXH | Đăng ký, trả phí xu, ai trước vào trước (32 suất) |
| Lịch | Cố định, Admin cấu hình | Cố định, Admin cấu hình |
| Thể thức | 4 bàn × 8 người → mỗi bàn lấy top 2 → chung kết 8 người 1 bàn | Như Giải Cao thủ |
| Thưởng | Skin độc quyền + xu | Prize pool từ phí vào, chia cho top 3 chung kết |

### 2.5. Skin

- Skin **chỉ người sở hữu nhìn thấy** (bàn, lá bài, khung, người chia bài, hiệu ứng chiến thắng...).
- **Ngoại lệ:** sticker và sound gửi trong trận thì cả bàn đều thấy/nghe.
- Mọi phần tử có thể đổi skin đều vẽ qua component trong `shared/skin/` của FE (Tùng sở hữu).

### 2.6. Khác

- **Thanh toán:** PayOS.
- **Chặn IP Việt Nam:** chỉ làm khi deploy, bằng Cloudflare. Nhóm test qua VPN. Không nằm trong code.
- **Chat:** chat trong trận và chat bạn bè gộp chung **một** `chat-service`, một DB (yêu cầu của thầy).
- **Trang Admin:** có làm. Ai sở hữu module nào thì làm trang Admin của module đó.

---

## 3. Kiến trúc

### 3.1. Danh sách service

| Service | Chủ sở hữu | Chức năng | Port | Database |
|---|---|---|---|---|
| `api-gateway` | Khanh | Định tuyến, xác thực JWT, route WebSocket | 8080 | — |
| `auth-service` | Khanh | Tài khoản, hồ sơ, điểm danh, báo cáo, xử phạt | 8090 | `auth_db` |
| `identity-service` | Khanh | Scaffold giữ nguyên, không nhận nghiệp vụ | 8081 | — |
| `game-service` | Bảo | Engine poker, bàn chơi, lịch sử trận, cấu hình trận | 8082 | `game_db` |
| `matchmaking-service` | Duy | Hàng chờ Rank/Normal, party | 8083 | `matchmaking_db` + Redis |
| `social-service` | Duy | Bạn bè, chặn, trạng thái online, thông báo realtime | 8084 | `social_db` + Redis |
| `chat-service` | Duy | Chat bạn bè + chat trong trận | 8085 | `chat_db` |
| `ranking-service` | Hoài Anh | Elo, bậc rank, BXH, mùa giải, quà mùa | 8086 | `ranking_db` |
| `competition-service` | Hoài Anh | Custom Room, giải đấu | 8087 | `competition_db` |
| `shop-service` | Tùng | Sản phẩm, đơn hàng PayOS, túi đồ | 8088 | `shop_db` |
| `wallet-service` | Tùng | Ví xu, sổ cái giao dịch xu | 8089 | `wallet_db` |

### 3.2. Công nghệ

- **Backend:** Java 21, Spring Boot (bản ổn định mới nhất trên start.spring.io), Maven multi-module, một repo `MS-Poker-BE`.
- **Frontend:** một repo `MS-Poker-FE` riêng.
- **Hạ tầng local (Docker Compose):**
  - Database — mỗi service sở hữu DB riêng (*Database per Service*) và tự chọn công nghệ (PostgreSQL, MySQL...). DB khai báo trong `<service>/compose.yml`, nằm trong mạng riêng `<service>-net` nên service khác không kết nối được; cần dữ liệu của nhau thì gọi API nội bộ hoặc nhận event. Hiện có `game-db` (PostgreSQL).
  - RabbitMQ — event giữa các service; một broker dùng chung, khai báo trong `docker/compose.infra.yml`.
  - Redis — hàng chờ ghép trận, presence, cache (dự kiến, chưa có trong Compose).
  - Mailpit — hộp thư giả để test OTP (dự kiến, chưa có trong Compose).
- **Chưa dùng** Eureka / Config Server ở MVP. Service gọi nhau bằng tên container, vd `http://wallet-service:8089`.

### 3.3. Cấu trúc repo backend

```
MS-Poker-BE/
├── pom.xml                  ← parent pom (chỉ sửa qua PR có review)
├── common/                  ← CHỈ chứa: class event, BaseEntity, format lỗi chuẩn
├── api-gateway/
├── identity-service/
├── game-service/
├── matchmaking-service/
├── social-service/
├── chat-service/
├── ranking-service/
├── competition-service/
├── shop-service/
├── wallet-service/
├── docker/
│   └── compose.infra.yml    ← hạ tầng dùng chung (RabbitMQ)
├── docker-compose.yml       ← chỉ include docker/compose.infra.yml và <service>/compose.yml (service + DB riêng)
├── docs/
│   └── SPEC.md              ← file này
└── README.md
```

Package gốc hiện có: `com.msspoker.<tên-service-bỏ-dấu-gạch>` (vd `com.msspoker.gameservice`, `com.msspoker.walletservice`).

### 3.4. Sơ đồ giao tiếp

```
                ┌──────────────────────── FE (MS-Poker-FE) ────────────────────────┐
                │   REST /api/**         WS /ws/game    WS /ws/chat    WS /ws/social│
                └──────────────┬───────────────────────────────────────────────────┘
                               ▼
                        ┌─────────────┐   xác thực JWT, gắn X-User-Id
                        │ api-gateway │
                        └──────┬──────┘
     ┌──────────┬──────────┬───┴──────┬───────────┬───────────┬──────────┐
 identity    game     matchmaking  social/chat  ranking   competition  shop/wallet
     │          │          │          │           │           │          │
     └──────────┴──── REST /internal/** (gọi trực tiếp, không qua gateway) ┘
     └──────────┴──── RabbitMQ exchange `poker.events` (event bất đồng bộ) ┘
```

---

## 4. Quy tắc chống chồng chéo

Đây là phần quan trọng nhất. Ai cũng phải đọc.

### 4.1. Quyền sở hữu

1. **Mỗi thứ có đúng một chủ:** mỗi module BE, mỗi database, mỗi bảng, mỗi route API, mỗi thư mục FE đều có một người chịu trách nhiệm (xem mục 9 và 10).
2. **Chỉ sửa code trong module của mình.** Thấy bug trong module người khác → tạo issue gắn tên chủ module, không tự sửa. Trường hợp gấp thì sửa qua PR và bắt buộc chủ module review.
3. **Không truy cập DB của service khác.** Không JOIN chéo, không dùng chung bảng, không kết nối thẳng vào database người khác.
4. **Cần dữ liệu của người khác** → gọi API nội bộ `/internal/**` hoặc nghe event. Được phép cache/lưu bản sao để đọc (read model), nhưng không bao giờ là nguồn gốc của dữ liệu đó.
5. **Không tự tạo lại tính năng người khác đã sở hữu**, dù chỉ là "bản nhỏ cho tiện". Ví dụ: không ai ngoài Tùng được cộng/trừ xu trực tiếp; không ai ngoài Hoài Anh được tính Elo.

### 4.2. Thay đổi hợp đồng

1. API nội bộ, event, kênh WebSocket trong mục 6 là **hợp đồng**. Muốn đổi → sửa `SPEC.md` trong PR, tag những người đang dùng.
2. **Chỉ thêm, không phá:** được thêm field mới (bên nhận phải bỏ qua field lạ), không được đổi tên hay xóa field đang dùng. Muốn phá hợp đồng thì tạo endpoint/event phiên bản mới (`v2`) và giữ bản cũ đến khi mọi người chuyển xong.
3. **API mình cần chưa có** → tạo issue cho chủ sở hữu, trong lúc chờ thì tự viết mock theo đúng hợp đồng trong file này.

### 4.3. File dùng chung

| File / thư mục | Chủ | Quy tắc |
|---|---|---|
| `pom.xml` gốc | Khanh | Mọi thay đổi qua PR, ít nhất 1 người review |
| `common/` | Khanh | Chỉ chứa event, `BaseEntity`, format lỗi. Thêm event mới thì người phát event tự thêm, chủ `common` review |
| `docker-compose.yml`, `docker/` | Khanh | Ai cần thêm hạ tầng thì PR, Khanh review |
| `<service>/compose.yml` | Chủ service | Tự khai báo service và DB riêng; đặt tên theo mục "Database per service" trong `README.md` |
| `docs/SPEC.md` | Cả nhóm | PR, người bị ảnh hưởng phải approve |
| FE `shared/` (layout, UI kit, API client) | Khanh | PR, Khanh review |
| FE `shared/skin/` | Tùng | PR, Tùng review |
| FE `shared/chat/` (`<ChatPanel>`) | Duy | PR, Duy review |

### 4.4. Git

- Nhánh `main` phải được bảo vệ trên GitHub: không push thẳng, mọi thay đổi qua Pull Request, cần ít nhất **1 approve**.
- Đặt tên nhánh: `<người>/<module>-<việc>`, vd `bao/game-hand-evaluator`, `tung/wallet-ledger`.
- Commit theo Conventional Commits, có scope là tên service: `feat(wallet): add debit api`, `fix(game): side pot when all-in`.
- Một PR chỉ động vào **một module** (trừ PR sửa hợp đồng). PR nhỏ, merge thường xuyên, không để nhánh sống quá 1 tuần.
- Không commit: `.idea/`, `*.iml`, `target/`, `node_modules/`, file `.env` có secret. PayOS key để trong biến môi trường.

---

## 5. Quy ước kỹ thuật chung

### 5.1. Xác thực

- FE không tự gửi header `Authorization` cho REST. Sau `POST /api/auth/login`, auth đặt cookie HttpOnly `accessToken` và `refreshToken`; FE gọi API qua gateway với `credentials: "include"`, request ghi gửi kèm header `X-XSRF-TOKEN` lấy từ `GET /api/auth/csrf`. Gateway đọc cookie `accessToken` để xác thực.
- Đăng ký, xác thực email, đăng nhập, refresh, đăng xuất, quên/đặt lại mật khẩu trong `/api/auth/**` và webhook PayOS không yêu cầu access token. Allowlist hiện nằm trong `GatewaySecurityConfig` (nhánh `feat/api-gateway`); thêm endpoint public mới thì phải bổ sung vào đó.
- WebSocket: FE gọi `GET /api/auth/ws-token` để lấy JWT, chỉ giữ trong bộ nhớ (không lưu localStorage) và gửi trong frame STOMP `CONNECT` (xem 6.3).
- Gateway loại bỏ mọi header `X-User-Id`, `X-User-Role` do client tự gửi, kiểm tra JWT rồi gắn header tin cậy xuống service:
  - `X-User-Id` — UUID tài khoản
  - `X-User-Role` — `USER` hoặc `ADMIN`
  - `X-Request-Id` — id để lần log xuyên service
- Service chỉ tin header này khi chỉ gateway được phép truy cập route `/api/**`; service không tự parse JWT (trừ WebSocket, xem 6.3). Compose scaffold hiện publish cả port của service, nên **chưa đáp ứng** ranh giới tin cậy này; phải bỏ publish port nội bộ hoặc bổ sung xác thực giữa gateway và service trước khi dùng JWT thật.
- Route `/internal/**` **không được mở qua gateway**. Chỉ các service trong mạng docker gọi nhau.
- Route `/api/admin/**` yêu cầu `X-User-Role = ADMIN`.

### 5.2. Định dạng dữ liệu

| Loại | Quy ước |
|---|---|
| ID | UUID v7 (theo `BaseEntity`) |
| Xu, chip, giá tiền | `long` / `BIGINT`, không dùng số thực |
| Thời gian | Lưu UTC (`Instant` / `timestamptz`). Hiển thị theo `Asia/Ho_Chi_Minh` |
| Ranh giới "một ngày" (điểm danh, reset) | 00:00 giờ `Asia/Ho_Chi_Minh` |
| JSON | camelCase |
| Enum | UPPER_SNAKE_CASE, vd `RANK`, `DAILY_CHECKIN` |
| Xóa | Soft delete bằng `isDeleted` |

> Khi tạo `BaseEntity` trong `common`, dùng `Instant` ngay từ đầu để tránh lệch múi giờ giữa các container.

### 5.3. Định dạng lỗi chuẩn

Mọi service trả lỗi theo cùng một dạng (class nằm trong `common`):

```json
{
  "code": "WALLET_INSUFFICIENT_BALANCE",
  "message": "Số dư xu không đủ",
  "timestamp": "2026-10-01T08:00:00Z",
  "requestId": "..."
}
```

`code` bắt đầu bằng tiền tố service: `AUTH_`, `GAME_`, `MM_`, `SOCIAL_`, `CHAT_`, `RANK_`, `COMP_`, `SHOP_`, `WALLET_`.

### 5.4. Idempotency

Mọi thao tác **cộng/trừ xu** và **cấp vật phẩm** bắt buộc có `idempotencyKey`. Gọi lại cùng key thì trả kết quả cũ, không thực hiện lần hai. Mọi consumer event phải bỏ qua event có `eventId` đã xử lý.

Quy ước key: `<lý-do>:<id-nguồn>:<accountId>`, vd `rank-fee:{proposalId}:{accountId}`, `rank-reward:{matchId}:{accountId}`.

---

## 6. Hợp đồng giữa các service

> Tất cả endpoint dưới đây là **đề xuất**, được chốt trong buổi họp hợp đồng ở tuần 0. Sau khi chốt, thay đổi theo quy tắc mục 4.2.

### 6.1. API nội bộ (`/internal/**`)

#### wallet-service (Tùng) — làm **đầu tiên**, cả nhóm cần

| Method | Endpoint | Người gọi | Mô tả |
|---|---|---|---|
| POST | `/internal/wallet/credit` | Khanh, Duy (hoàn phí), Hoài Anh | Cộng xu |
| POST | `/internal/wallet/debit` | Khanh, Duy, Hoài Anh | Trừ xu, lỗi `WALLET_INSUFFICIENT_BALANCE` nếu không đủ |
| GET | `/internal/wallet/{accountId}/balance` | Duy, Hoài Anh | Xem số dư |

Body của `credit` / `debit`:

```json
{
  "accountId": "uuid",
  "amount": 100,
  "reason": "RANK_ENTRY_FEE",
  "refId": "id-trận/phòng/giải liên quan",
  "idempotencyKey": "rank-fee:{proposalId}:{accountId}"
}
```

Danh sách `reason`: `FIRST_LOGIN_BONUS`, `DAILY_CHECKIN`, `CHECKIN_MAKEUP`, `RANK_ENTRY_FEE`, `RANK_ENTRY_REFUND`, `RANK_REWARD`, `TOURNAMENT_ENTRY_FEE`, `TOURNAMENT_REFUND`, `TOURNAMENT_PRIZE`, `SEASON_REWARD`, `TUTORIAL_REWARD`, `ADMIN_ADJUST`.

#### game-service (Bảo)

| Method | Endpoint | Người gọi | Mô tả |
|---|---|---|---|
| POST | `/internal/tables` | Duy, Hoài Anh | **CreateTable** — mở bàn mới |
| GET | `/internal/matches?accountId=&page=` | Dự phòng cho service khác | Lịch sử trận; màn Hồ sơ của Khanh dùng `GET /api/game/matches` (xem 6.3) |
| GET | `/internal/players/{accountId}/active-table` | Duy | Kiểm tra người chơi đang ở bàn nào trước khi vào queue |
| GET | `/internal/matches?accountId=&page=` | Khanh | Lịch sử trận cho màn Hồ sơ |


Body `CreateTable`:

```json
{
  "mode": "RANK",
  "sourceId": "proposalId | roomId | tournamentTableId",
  "playerIds": ["uuid", "uuid"],
  "settings": {
    "smallBlind": 10,
    "bigBlind": 20,
    "startingChips": 1000,
    "turnTimeSeconds": 20
  },
  "entryFee": 100
}
```

Trả về: `{ "tableId": "uuid", "matchId": "uuid" }`. `settings` bỏ trống thì dùng Match Settings mặc định của `mode` đó. `entryFee` là bản chụp phí do matchmaking/competition cung cấp (0 cho trận miễn phí) để game ghi lịch sử và phát event; game không quyết định hoặc thu phí.

#### ranking-service (Hoài Anh)

| Method | Endpoint | Người gọi | Mô tả |
|---|---|---|---|
| GET | `/internal/ranking/{accountId}` | Duy | `{ elo, tier, winStreak, lossStreak }` |
| GET | `/internal/ranking/top?limit=32` | Hoài Anh (competition) | Top N của mùa hiện tại |

#### social-service (Duy)

| Method | Endpoint | Người gọi | Mô tả |
|---|---|---|---|
| GET | `/internal/friends/{accountId}` | Hoài Anh, Duy (chat) | Danh sách id bạn bè |
| GET | `/internal/friends/{a}/with/{b}` | Hoài Anh, Duy (chat) | `{ isFriend, isBlocked }` |

- Base URL trong Docker: `http://social-service:8084`. Cả hai endpoint trả **JSON thô**, không bọc envelope.
- `GET /internal/friends/{accountId}` trả mảng id `["uuid", ...]` *(đề xuất)*.
- `GET /internal/friends/{a}/with/{b}` trả `{ "isFriend": true, "isBlocked": false }`; `isBlocked = true` khi một trong hai bên chặn bên kia *(đề xuất)*. `competition-service` đã gọi endpoint này khi mời vào Custom Room và từ chối nếu `!isFriend || isBlocked`; ngoài profile dev, thiếu endpoint thì chức năng mời phòng lỗi.

#### shop-service (Tùng)

| Method | Endpoint | Người gọi | Mô tả |
|---|---|---|---|
| GET | `/internal/inventory/{accountId}/owns/{productId}` | Duy (sticker/sound) | `{ owned: true/false }` |
| POST | `/internal/inventory/grant` | Hoài Anh | Trao skin thưởng, có `idempotencyKey` |

#### auth-service (Khanh) — trước đây ghi `identity-service`

| Method | Endpoint | Người gọi | Mô tả |
|---|---|---|---|
| GET | `/internal/accounts/{accountId}/restrictions` | Duy, Bảo | `{ banned, chatBannedUntil, rankBannedUntil }` |

- Base URL trong Docker: `http://auth-service:8090`. Chưa triển khai (2026-10-15).
- Trả **JSON thô** đúng 3 field, không bọc trong envelope `{ code, message, result }` như API public của auth. Bên gọi bỏ qua field lạ, nên nếu bọc thì `banned` luôn đọc ra `false` và lệnh cấm bị bỏ qua mà không báo lỗi.
- `banned`: boolean; `chatBannedUntil`, `rankBannedUntil`: thời điểm UTC dạng ISO-8601 hoặc `null`. MVP trả `false` / `null` / `null`. Account không tồn tại trả 404 *(đề xuất)*.
- Gọi không cần cookie/JWT. Route không mở qua gateway (gateway đã chặn `/internal/**`). `SecurityConfig` của auth đang `denyAll` mọi path ngoài danh sách cho phép, nên phải mở riêng `/internal/**`.
- Game gọi endpoint này mỗi lần CreateTable (ngoài profile dev); gọi không được thì từ chối mở bàn với 503 `GAME_DEPENDENCY_UNAVAILABLE`.

> **Tên hiển thị và avatar:** các service chỉ trả `accountId`. FE tự lấy tên/avatar qua `GET /api/profiles/batch?ids=...` của Khanh và cache lại. Không service nào lưu bản sao tên người chơi.

### 6.2. Event (RabbitMQ)

- Exchange: `poker.events` (topic). Routing key = tên event.
- Mỗi consumer tạo queue riêng tên `<service>.<event>`, vd `ranking-service.match.finished`.
- Vỏ bọc chung (class trong `common`):

```json
{
  "eventId": "uuid",
  "eventType": "match.finished",
  "occurredAt": "2026-10-01T08:00:00Z",
  "payload": { }
}
```

| Event | Bên phát | Bên nhận | Payload chính | GĐ |
|---|---|---|---|---|
| `account.registered` | `auth-service` (Khanh) | `wallet-service` (Tùng), `ranking-service` (Hoài Anh) | `accountId` | 🟢 |
| `account.penalized` | `auth-service` (Khanh) | `matchmaking-service`, `chat-service` (Duy), `game-service` (Bảo) | `accountId`, `type`, `until` | 🔵 |
| `match.started` | `game-service` (Bảo) | `chat-service` (Duy); `social-service` (presence, GĐ2) | `matchId`, `tableId`, `mode`, `sourceId`, `playerIds` | 🟢 |
| `match.finished` | `game-service` (Bảo) | `ranking-service` (Elo/thưởng Rank), `competition-service` (Custom/giải), `chat-service` (đóng phòng); `social-service` (presence, GĐ2) | xem dưới | 🟢 |
| `notification.requested` | Mọi service | `social-service` (Duy, đẩy realtime qua `/ws/social`) | `accountIds`, `type`, `title`, `body`, `data` | 🟢 |
| `product.purchased` | `shop-service` (Tùng) | — (dự phòng cho thống kê) | `accountId`, `productId`, `orderId` | 🔵 |

Payload `match.finished`:

```json
{
  "matchId": "uuid",
  "tableId": "uuid",
  "mode": "RANK",
  "sourceId": "proposalId | roomId | tournamentTableId",
  "entryFee": 100,
  "placements": [
    { "accountId": "uuid", "place": 1, "finalChips": 6000 },
    { "accountId": "uuid", "place": 2, "finalChips": 0 }
  ],
  "startedAt": "...",
  "finishedAt": "..."
}
```

Các `type` của `notification.requested`: `FRIEND_REQUEST`, `FRIEND_ACCEPTED`, `MATCH_FOUND`, `MATCH_READY`, `ROOM_INVITE`, `ROOM_UPDATED`, `ROOM_STARTED`, `TOURNAMENT_INVITE`, `TOURNAMENT_TABLE_READY`, `SEASON_REWARD_AVAILABLE`, `REPORT_RESOLVED`, `PAYMENT_SUCCEEDED`.

> Nhờ `notification.requested`, chỉ Duy phải làm WebSocket cho thông báo. Service khác muốn đẩy gì xuống người chơi thì phát event này, không tự mở WebSocket riêng.

### 6.3. WebSocket (STOMP)

- FE mở kết nối qua gateway, gửi JWT trong header `Authorization` của frame STOMP `CONNECT`. Service sở hữu WebSocket tự xác thực JWT ở bước này.

| Endpoint | Chủ | Client gửi (SEND) | Client nghe (SUBSCRIBE) |
|---|---|---|---|
| `/ws/game` | Bảo | `/app/tables/{tableId}/action` `{ type, amount }` · `/app/tables/{tableId}/sync` | `/user/queue/tables/{tableId}` — snapshot riêng từng người (đã ẩn bài người khác) |
| `/ws/chat` | Duy | `/app/conversations/{id}/send` `{ messageType, content }` | `/user/queue/chat` |
| `/ws/social` | Duy | — | `/user/queue/notifications` · `/user/queue/presence` |

`type` hành động trong bàn: `FOLD`, `CHECK`, `CALL`, `RAISE` (kèm `amount` = tổng cược muốn đạt tới), `ALL_IN`.

#### Chi tiết tích hợp game đã triển khai trên nhánh Bảo

Các chi tiết bổ sung sau cần bên gọi/gateway review trước khi merge:

- Action có thể thêm `actionSequence` từ snapshot để chống gửi trùng hoặc action
  đến muộn; FE của Bảo luôn gửi trường này. Các hành động ngoài RAISE không gửi
  `amount`. Business error gửi riêng qua `/user/queue/game-errors`.
- Snapshot gồm `tableId`, `matchId`, `mode`, `accountId`, `street`, `handNumber`,
  `actionSequence`, các chỉ số ghế dealer/blind/actor, `board`, `pot`, `currentBet`,
  `deadline`, `serverTime`, `seats`, `legalActions`, `callAmount`, `minRaiseTo`,
  `maxRaiseTo`, `placements`, `distribution`. Card dùng enum `{ rank, suit }`.
  `seats[].cards` chỉ có bài của người nhận hoặc bài showdown chưa fold.
- `GET /api/game/me/active-table` trả `{ tableId, matchId }` hoặc `null`;
  `GET /api/game/tables/{tableId}` trả snapshot cá nhân;
  `GET /api/game/tables/{tableId}/result` trả kết quả đã lưu. Hai endpoint theo
  tableId bắt buộc membership. Lịch sử internal dùng `PageResponse`, hỗ trợ
  `page` từ 0 và `size` 1–100, mặc định 20.
- *(Bảo bổ sung, chưa có trong code)* `GET /api/game/matches?accountId=&page=&size=`
  qua gateway cho màn Hồ sơ của Khanh, trả cùng `PageResponse` như
  `/internal/matches`; mỗi phần tử `{ id, tableId, mode, sourceId, entryFee,
  status, startedAt, finishedAt, placements[] }`. FE gọi thẳng, auth không cần
  làm proxy.
- Admin: GET `/api/admin/match-settings`; PUT `/api/admin/match-settings/{mode}`
  nhận `{ settings: { smallBlind, bigBlind, startingChips, turnTimeSeconds },
  minPlayers, maxPlayers }`. Thay đổi chỉ áp dụng bàn mở sau đó.
- CreateTable idempotent theo `(mode, sourceId)` và request giống nhau; request
  khác cho cùng khóa trả conflict. Thứ tự `playerIds` là thứ tự ghế. Một account
  chỉ ở một bàn đang chạy. NORMAL/CUSTOM phải có `entryFee = 0`.
- Xếp hạng khi bị loại cùng ván: chip đầu ván lớn hơn đứng trên; nếu bằng nhau,
  seatIndex nhỏ hơn đứng trên, bảo đảm placements duy nhất (Hoài Anh review).
  AFK đủ N lượt fold tự động các ván sau, vẫn đóng blind để bảo toàn chip.
- REST ngoài dev yêu cầu header server `X-Game-Service-Key` khớp cấu hình
  `GAME_SERVICE_KEY`; gateway phải loại bỏ header giả từ client, xác thực JWT và
  cấp `X-User-*`. WebSocket tự kiểm tra RS256/JWKS, issuer, audience, hạn token
  và UUID subject. Không đưa service key vào FE.
- Dev chạy PostgreSQL riêng của game (`game-db`, khai báo trong
  `game-service/compose.yml`) và RabbitMQ dùng chung bằng Docker Compose ở root;
  `game-service/compose.dev.yml` chỉ ghi đè cấu hình profile `dev`. H2 chỉ dành
  cho test. Xem `game-service/README.md` để chạy và cấu hình. Restart service hủy
  trận RAM đang dở; không phát match.finished để trao thưởng cho trận bị hủy.

Đây là phần triển khai của Bảo, không xác nhận gateway/auth/chat/skin/Elo/wallet
đã tích hợp. Consumer GĐ2 `account.penalized` còn phụ thuộc hợp đồng loại xử phạt
của identity; hiện game kiểm tra restrictions khi tạo bàn.

### 6.4. Bảng route public qua gateway

Đây là bảng route mục tiêu. Gateway trên `develop` vẫn là route mẫu; nhánh `feat/api-gateway` đã có JWT/CSRF nhưng route còn sai và chưa có WebSocket, xem việc của Khanh ở mục 9.1.

| Tiền tố | Service |
|---|---|
| `/api/auth/**`, `/api/profiles/**`, `/api/checkin/**`, `/api/reports/**`, `/api/admin/users/**`, `/api/admin/reports/**` | auth |
| `/api/game/**`, `/api/admin/match-settings/**` | game |
| `/api/matchmaking/**`, `/api/party/**` | matchmaking |
| `/api/friends/**`, `/api/blocks/**`, `/api/notifications/**` | social |
| `/api/chat/**` | chat |
| `/api/ranking/**`, `/api/seasons/**`, `/api/tutorial/**`, `/api/admin/seasons/**` | ranking |
| `/api/rooms/**`, `/api/tournaments/**`, `/api/admin/tournaments/**` | competition |
| `/api/shop/**`, `/api/inventory/**`, `/api/payments/**`, `/api/admin/products/**` | shop |
| `/api/wallet/**` | wallet |
| `/ws/game`, `/ws/chat`, `/ws/social` | game, chat, social |

`/api/payments/payos/webhook` và các endpoint xác thực công khai nêu ở mục 5.1 không cần access token. Webhook PayOS phải kiểm tra chữ ký.

---

## 7. Các luồng nghiệp vụ chính

Đây là các luồng đi qua nhiều service. Mỗi bước ghi rõ ai làm.

### 7.1. Đăng ký và nhận xu lần đầu 🟢

1. **Khanh** — người chơi đăng ký, xác thực OTP email. Phát `account.registered`.
2. **Tùng** — nhận event, tạo ví số dư 0.
3. **Hoài Anh** — nhận event, tạo bản ghi Elo mùa hiện tại (Tân thủ).
4. **Khanh** — lần đăng nhập đầu tiên: gọi `credit` với `FIRST_LOGIN_BONUS`, key `first-login:{accountId}`.

### 7.2. Trận Rank 🟢

1. **Duy** — người chơi vào hàng chờ. Kiểm tra: không bị cấm Rank (hỏi Khanh), đủ xu (hỏi Tùng), không đang trong trận khác.
2. **Duy** — ghép đủ người theo Elo, phát `notification.requested` loại `MATCH_FOUND` kèm `proposalId`.
3. **FE** — hiện popup, người chơi bấm Chấp nhận trong 10 giây (`POST /api/matchmaking/proposals/{id}/accept`).
4. **Duy** — đủ người chấp nhận → gọi `debit` phí vào cho từng người (key `rank-fee:{proposalId}:{accountId}`). Nếu một người trừ thất bại → hoàn phí người đã trừ (`RANK_ENTRY_REFUND`), đưa những người còn lại về hàng chờ.
5. **Duy** — gọi `CreateTable` của Bảo với `mode = RANK`, `entryFee`. Nếu lỗi → hoàn phí toàn bộ.
6. **Duy** — phát `MATCH_READY` kèm `tableId`. FE chuyển sang màn bàn chơi.
7. **Bảo** — phát `match.started`. **Duy** mở phòng chat trận.
8. **Bảo** — chơi đến khi còn 1 người, phát `match.finished`.
9. **Hoài Anh** — cập nhật Elo theo thứ hạng, gọi `credit` thưởng cho top 3 (key `rank-reward:{matchId}:{accountId}`).
10. **Duy** — đóng phòng chat trận, đổi presence về online.

### 7.3. Trận Normal 🟢

Giống 7.2 nhưng: không kiểm tra/trừ xu, không có popup chấp nhận *(đề xuất)*, bỏ qua Elo. Hoài Anh nhận `match.finished` với `mode = NORMAL` thì bỏ qua.

API Normal: `POST /api/matchmaking/normal/join`, `DELETE /api/matchmaking/normal/leave`,
`GET /api/matchmaking/normal/status` và `GET /api/matchmaking/normal/reconnect`. Khi tạo bàn,
Matchmaking lưu trạng thái `IN_GAME`, `proposalId`, `tableId`, `matchId`, host, port và WebSocket
path trong Redis theo account. Reconnect chỉ trả địa chỉ trận cũ khi Redis có `IN_GAME` và Game
service vẫn xác nhận đúng bàn đó; record stale phải bị xóa.
Mode được xác định bởi URL nên request join không cần body `mode`.

Quy tắc gom người trước khi mở bàn:

1. Có `0–1` người thì tiếp tục chờ, chưa bắt đầu đếm ngược.
2. Khi đạt `minPlayers` (demo mặc định là 2), backend tạo `matchStartDeadline` sau **10 giây**.
3. Trong 10 giây tiếp tục nhận thêm người vào nhóm.
4. Nếu đạt `maxPlayers` (mặc định là 6), tạo bàn ngay, không chờ hết thời gian.
5. Khi hết 10 giây và nhóm vẫn có từ `minPlayers` đến `maxPlayers - 1`, tạo bàn với số người hiện có.
6. Nếu số người tụt xuống dưới `minPlayers` trước deadline, hủy/reset countdown.

Countdown gom người này độc lập với thời gian 10 giây chấp nhận proposal của trận Rank. Deadline do
backend sở hữu và trả về frontend; frontend chỉ hiển thị thời gian còn lại.

### 7.4. Custom Room 🟢

1. **Hoài Anh** — người chơi tạo phòng, nhận Room ID. Mời bạn: kiểm tra quan hệ bạn bè (hỏi Duy), phát `ROOM_INVITE`.
2. **Hoài Anh** — mỗi khi phòng thay đổi (vào, rời, sẵn sàng, kick) → phát `ROOM_UPDATED` cho các thành viên.
3. **Hoài Anh** — chủ phòng bấm bắt đầu → gọi `CreateTable` với `mode = CUSTOM`, `sourceId` = UUID mới cho mỗi lần bắt đầu (lưu ở `custom_rooms.current_source_id`; game chặn trùng `(mode, sourceId)` nên không dùng lại `roomId` cho ván sau) → phát `ROOM_STARTED` kèm `tableId`.
4. Sau trận, **Hoài Anh** nhận `match.finished` và đưa phòng về trạng thái chờ để chơi tiếp.

### 7.5. Mua skin 🟢

1. **Tùng** — `POST /api/payments/orders { productId }`: kiểm tra chưa sở hữu, tạo đơn `PENDING`, gọi PayOS tạo link thanh toán, trả về link/QR.
2. Người chơi thanh toán trên trang PayOS.
3. **Tùng** — nhận webhook, kiểm tra chữ ký, đơn đang `PENDING` thì chuyển sang `PAID` và thêm vật phẩm vào túi đồ trong **cùng một transaction**. Webhook trùng thì bỏ qua.
4. **Tùng** — phát `PAYMENT_SUCCEEDED` qua `notification.requested`.
5. Job định kỳ hỏi PayOS trạng thái các đơn `PENDING` quá 15 phút *(đề xuất)*.

### 7.6. Điểm danh 🟢

1. **Khanh** — `POST /api/checkin`: kiểm tra hôm nay (theo giờ Việt Nam) chưa điểm danh → lưu → `credit` với `DAILY_CHECKIN`, key `checkin:{yyyy-MM-dd}:{accountId}`.
2. Điểm danh bù: `debit` phí `CHECKIN_MAKEUP` trước, thành công mới lưu và cộng thưởng ngày đó.

### 7.7. Giải đấu 🔵

1. **Hoài Anh** — Admin tạo giải (loại, lịch, phí, thưởng).
2. Giải Cao thủ: trước giờ đấu 30 phút *(đề xuất)*, chốt top 32 (gọi `/internal/ranking/top`), phát `TOURNAMENT_INVITE`. Người không xác nhận trước 10 phút thì lấy hạng 33, 34... bù vào.
3. Giải Mở: người chơi đăng ký, `debit` phí `TOURNAMENT_ENTRY_FEE`. Không đủ người lúc bắt đầu → hủy giải, hoàn phí.
4. **Hoài Anh** — chia 4 bàn, gọi `CreateTable` 4 lần với `mode = TOURNAMENT`, phát `TOURNAMENT_TABLE_READY`.
5. **Hoài Anh** — nhận đủ 4 `match.finished` → lấy top 2 mỗi bàn → mở bàn chung kết.
6. **Hoài Anh** — trao thưởng: xu (`TOURNAMENT_PRIZE`) và skin (`/internal/inventory/grant`).
7. **Bảo** — đảm bảo engine chạy ổn định nhiều bàn cùng lúc.

---

## 8. Tham số game mặc định

Tất cả nằm trong config / bảng Admin, chỉnh không cần sửa code.

| Tham số | Giá trị | Chủ |
|---|---|---|
| Blind | 10 / 20 | Bảo |
| Chip khởi điểm | 1.000 | Bảo |
| Thời gian mỗi lượt | 20 giây | Bảo |
| Số người bàn Rank / Normal | 6 (tối thiểu để mở bàn: config, demo để 2) | Bảo, Duy |
| Thời gian gom thêm người Normal sau khi đủ tối thiểu | 10 giây; đủ 6 thì mở bàn ngay | Duy |
| Elo theo thứ hạng (bàn 6) | +20, +10, +5, −5, −10, −15 | Hoài Anh |
| Elo tối thiểu | 0 (không âm) *(đề xuất)* | Hoài Anh |
| Phí vào Rank | 100 xu *(đề xuất)* | Duy |
| Thưởng xu Rank | Top 1: 3× phí · Top 2: 2× phí · Top 3: 1× phí | Hoài Anh |
| Xu lần đăng nhập đầu | 500 *(đề xuất)* | Khanh |
| Xu điểm danh mỗi ngày | 50 *(đề xuất)* | Khanh |
| Phí điểm danh bù | 30 xu *(đề xuất)* | Khanh |
| Thời gian chấp nhận trận | 10 giây | Duy |
| Phạt từ chối/không chấp nhận | Không được vào hàng chờ 2 phút *(đề xuất)* | Duy |
| Chuỗi thắng | Số trận liên tiếp lọt top 3 *(đề xuất)* | Hoài Anh |
| Mốc Elo các bậc | Tân thủ 0 · Sắt 100 · Đồng 200 · Bạc 350 · Vàng 500 · Bạch kim 700 · Kim cương 950 · Kiện tướng 1.250 · Đại kiện tướng 1.600 · Thần bài = top 50 Đại kiện tướng *(đề xuất)* | Hoài Anh |
| Reset mùa | Giữ lại 50% Elo *(đề xuất)* | Hoài Anh |
| Chia prize pool Giải Mở | 50% / 30% / 20% cho top 3 chung kết *(đề xuất)* | Hoài Anh |
| Thưởng hoàn thành Tutorial | 200 xu, mỗi tài khoản nhận một lần | Hoài Anh |

Mã enum bậc rank: `NEWBIE`, `IRON`, `BRONZE`, `SILVER`, `GOLD`, `PLATINUM`, `DIAMOND`, `CHAMPION`, `GRAND_CHAMPION`, `CARD_GOD`.

---

## 9. Phân công chi tiết từng người

Mỗi mục gồm: phạm vi sở hữu, việc không được làm, bảng dữ liệu, danh sách việc, những gì cung cấp cho người khác, những gì phụ thuộc và cách mock khi chờ.

### Khối lượng ước tính

Điểm tương đối để so sánh, không phải số giờ.

| Người | MVP | GĐ2 | Tổng |
|---|---|---|---|
| Khanh | 12 | 4 | 16 |
| Bảo | 15 | 1 | 16 |
| Duy | 10 | 6 | 16 |
| Hoài Anh | 11 | 7 | 18 |
| Tùng | 12 | 4 | 16 |

---

### 9.1. 👤 Khanh — `auth-service`, `api-gateway`, hạ tầng, khung FE

**Sở hữu**
- BE: `api-gateway`, `auth-service` (nghiệp vụ tài khoản; `identity-service` giữ scaffold), `common`, `pom.xml` gốc, `docker-compose.yml`, `docker/`.
- DB: `auth_db`.
- Route: `/api/auth/**`, `/api/profiles/**`, `/api/checkin/**`, `/api/reports/**`, `/api/admin/users/**`, `/api/admin/reports/**`.
- FE: `shared/` (layout, UI kit, API client, lưu token, router), `features/auth`, `features/lobby`, `features/checkin`, `features/profile`, `features/report`, `features/admin/users`.

**Không được làm**
- Không tự cộng/trừ xu vào DB → luôn gọi API ví của Tùng.
- Không tự xây logic ghép trận trong Sảnh chờ → nút "Tìm trận" chỉ gọi API của Duy.
- Không lưu Elo/bậc rank trong profile → lấy từ Hoài Anh.

**Bảng dữ liệu (`auth_db`)**

| Bảng | Nội dung chính |
|---|---|
| `accounts` | email, passwordHash, accountStatus, isEmailVerified, role, firstLoginRewarded |
| `profiles` | accountId, displayName, avatarUrl, gender, birthdate |
| `email_otps` | accountId, code (hash), purpose (VERIFY / RESET), expiresAt, usedAt |
| `refresh_tokens` | accountId, tokenHash, expiresAt, revokedAt |
| `checkins` | accountId, checkinDate, isMakeup — unique (accountId, checkinDate) |
| `reports` 🔵 | reporterId, targetId, matchId, reason, description, status |
| `penalties` 🔵 | accountId, type (BAN / CHAT_BAN / RANK_BAN), until, reportId |

> Hiện có (migration V1–V4): `accounts`, `profiles` (khóa chính `account_id`, thêm fullName, bio, countryCode, city), `registration_otps` (thay `email_otps` cho mục đích VERIFY), `account_registration_events` (outbox của `account.registered`), `refresh_tokens`. Chưa có: `checkins`, OTP cho đặt lại mật khẩu, `reports`, `penalties`.

**Việc cần làm**

🟢 MVP — trạng thái ngày 2026-10-15 (`develop` và nhánh `feat/api-gateway`)
- [x] **Tuần 0:** init repo, parent pom, các ứng dụng chạy được `/actuator/health` và 1 module thư viện `common`, `docker-compose.yml`, `.gitignore`, README. *(Chưa kiểm tra bảo vệ nhánh `main`.)*
- [ ] **Tuần 0:** module `common` — đã có `BaseEntity`, `ErrorResponse`, `EventEnvelope`, `AccountRegisteredEvent`. Còn thiếu class cho `match.started`, `match.finished`, `notification.requested`, `account.penalized`; game, ranking, competition đang tự khai báo bản sao.
- [ ] **Sửa `docker compose up` ở root** — đang lỗi `services.rabbitmq conflicts with imported resource` vì `auth-service/compose.yml` khai báo lại service `rabbitmq` (user `poker`). Bỏ khai báo đó khỏi file auth, đặt user RabbitMQ chung trong `docker/compose.infra.yml`, cho mọi service dùng cùng `RABBITMQ_USER`/`RABBITMQ_PASSWORD` (game, ranking, competition đang mặc định `guest`).
- [ ] Gateway — nhánh `feat/api-gateway` (chưa merge) đã có xác thực JWT từ cookie, CSRF, gắn header `X-User-*`, chặn `/internal/**`, route auth và game. Còn thiếu:
  - Bỏ `StripPrefix=2` ở mọi route nghiệp vụ: controller các service giữ nguyên tiền tố `/api/...`, hiện `/api/ranking/leaderboard` bị đổi thành `/leaderboard` và trả 404.
  - Đủ tiền tố theo bảng 6.4. Đang thiếu `/api/seasons/**`, `/api/tutorial/**`, `/api/admin/seasons/**` (ranking); `/api/rooms/**`, `/api/tournaments/**`, `/api/admin/tournaments/**` (competition, hiện dùng `/api/competition/**` không tồn tại); `/api/inventory/**`, `/api/payments/**`, `/api/admin/products/**` (shop); `/api/party/**` (matchmaking); `/api/friends/**`, `/api/blocks/**`, `/api/notifications/**` (social, hiện dùng `/api/social/**`).
  - Route WebSocket `/ws/game`, `/ws/chat`, `/ws/social`. Hiện `anyExchange().denyAll()` chặn mọi path ngoài `/api/**`. Xác thực WebSocket nằm ở frame STOMP `CONNECT` (6.3).
  - Thêm `POST /api/payments/payos/webhook` vào allowlist public.
- [x] Đăng ký, xác thực OTP email, gửi lại OTP, đăng nhập, refresh token, đăng xuất (cookie HttpOnly + CSRF, JWT RS256/JWKS, `GET /api/auth/me`, `GET /api/auth/ws-token`).
- [ ] Quên/đặt lại mật khẩu (API đề xuất bên dưới).
- [x] Phát `account.registered` (outbox; ranking đã nhận).
- [ ] Tặng xu lần đăng nhập đầu (7.1) — mới có cột `first_login_rewarded`, chưa gọi wallet.
- [ ] Điểm danh, điểm danh bù, lịch điểm danh theo tháng (7.6).
- [ ] Hồ sơ: xem/sửa, upload avatar, `GET /api/profiles/batch?ids=` (FE mọi người cần để hiện tên, avatar). Hiện chỉ có `GET /api/auth/me`.
- [ ] Màn Hồ sơ hiển thị lịch sử trận (`GET /api/game/matches` của Bảo, xem 6.3) và Elo/bậc (`GET /api/ranking/me` của Hoài Anh).
- [ ] **Tuần 0–1:** khung FE — router, layout, UI kit cơ bản, API client tự gắn token và tự refresh.
- [ ] Màn: Đăng nhập, Đăng ký, Xác thực email, Quên mật khẩu, Sảnh chờ (khung + các nút lối tắt), Điểm danh, Hồ sơ.
- [ ] `GET /internal/accounts/{id}/restrictions` (MVP trả tất cả là false, hợp đồng ở 6.1). **Đang chặn game:** ngoài profile dev, mọi lệnh CreateTable gọi endpoint này.

🔵 GĐ2
- [ ] Báo cáo: gửi báo cáo, xem trạng thái báo cáo của mình.
- [ ] Xử phạt: ban, cấm chat, cấm Rank có thời hạn. Phát `account.penalized`.
- [ ] Admin: danh sách người dùng, ban/unban, duyệt báo cáo.
- [ ] *(đề xuất)* Đăng nhập Google, khóa tạm khi sai mật khẩu nhiều lần, onboarding lần đầu.

**API đề xuất cho phần còn thiếu** *(đề xuất — Khanh được đổi, nhưng phải sửa SPEC)*

| Method | Endpoint | Body / Query | Ghi chú |
|---|---|---|---|
| GET | `/internal/accounts/{id}/restrictions` | — | Hợp đồng ở 6.1. JSON thô, không bọc envelope |
| GET | `/api/profiles/batch?ids=a,b,c` | Tối đa 50 id | Trả `[{ accountId, displayName, avatarUrl }]`; id không tồn tại hoặc đã xóa thì bỏ qua, không trả lỗi |
| GET | `/api/profiles/me` | — | Hồ sơ đầy đủ của mình cho màn sửa hồ sơ |
| PATCH | `/api/profiles/me` | `{ displayName, fullName, gender, birthDate, bio, countryCode, city }` | Field không gửi thì giữ nguyên |
| POST | `/api/profiles/me/avatar` | multipart `file` | Giới hạn loại file và dung lượng; nơi lưu ảnh do Khanh chọn |
| POST | `/api/checkin` | — | Luồng 7.6. Hôm nay (giờ Việt Nam) đã điểm danh thì trả 409 |
| POST | `/api/checkin/makeup` | `{ date: "yyyy-MM-dd" }` | `debit` 30 xu `CHECKIN_MAKEUP`, key `checkin-makeup:{date}:{accountId}`; thành công mới lưu và `credit` `DAILY_CHECKIN`, key `checkin:{date}:{accountId}`. Khanh chốt phạm vi ngày được bù |
| GET | `/api/checkin/calendar?month=yyyy-MM` | — | `{ month, days: [{ date, isMakeup }] }` |
| POST | `/api/auth/password/forgot` | `{ email }` | Gửi OTP đặt lại mật khẩu. Luôn trả 200 dù email có tồn tại hay không, để không lộ email đã đăng ký |
| POST | `/api/auth/password/reset` | `{ email, otp, password, confirmPassword }` | Thành công thì thu hồi mọi refresh token của account |

- **Xu lần đăng nhập đầu:** login thành công mà `first_login_rewarded = false` → `credit` 500 xu `FIRST_LOGIN_BONUS`, key `first-login:{accountId}` → đặt cờ `true`. Wallet lỗi thì login vẫn thành công và thử lại sau (job hoặc lần login kế tiếp); key idempotent nên gọi lại không cộng hai lần *(đề xuất)*.
- Wallet nằm ở nhánh `feature/wallet-shop-service` của Tùng, chưa merge: response thành công bọc `{ code, message, data, timestamp }`, không đủ xu trả HTTP 400 với `code = WALLET_INSUFFICIENT_BALANCE`. Xác nhận lại với Tùng khi merge.
- Các endpoint public mới phải thêm vào allowlist gateway nếu không cần đăng nhập (`/api/auth/password/**`).

**Cung cấp cho người khác:** gateway và khung FE (cả nhóm cần ngay tuần 0–1), `account.registered`, `account.penalized`, `/internal/accounts/{id}/restrictions`, `/api/profiles/batch`.

**Phụ thuộc và cách mock**

| Cần gì | Của ai | Mock trong lúc chờ |
|---|---|---|
| API `credit`/`debit` | Tùng | Service giả trả `200 OK` và log ra console |
| Lịch sử trận (`GET /api/game/matches`, chưa có) | Bảo | Trả danh sách rỗng |
| Elo/bậc | Hoài Anh | Hiển thị cứng "Tân thủ – 0" |

---

### 9.2. 👤 Bảo — `game-service`

**Sở hữu**
- BE: `game-service`. DB: `game_db`.
- Route: `/api/game/**`, `/api/admin/match-settings/**`, `/internal/tables`, `/internal/matches`, WebSocket `/ws/game`.
- FE: `features/game-table` (bàn chơi, kết thúc trận), `features/admin/match-settings`. Các component bàn chơi phải tách để Hoài Anh tái sử dụng cho Tutorial.

**Không được làm**
- Không tính Elo, không cộng xu thưởng → chỉ phát `match.finished`, Hoài Anh xử lý.
- Không làm chat trong trận → gắn `<ChatPanel conversationId={matchId}>` của Duy.
- Không tự vẽ skin → dùng component trong `shared/skin/` của Tùng.
- Không tự ghép người vào bàn → chỉ mở bàn khi có người gọi `CreateTable`.

**Bảng dữ liệu (`game_db`)**

| Bảng | Nội dung chính |
|---|---|
| `match_settings` | mode, smallBlind, bigBlind, startingChips, turnTimeSeconds, maxPlayers, minPlayers — không chứa phí vào Rank/Giải do Duy/Hoài Anh sở hữu |
| `matches` | tableId, mode, sourceId, status (RUNNING / FINISHED / CANCELLED), startedAt, finishedAt |
| `match_players` | matchId, accountId, seatIndex, place, finalChips, leftEarly |
| `hand_histories` *(tùy chọn)* | matchId, handNumber, board, actions (JSON) — phục vụ debug |

Trạng thái bàn đang chơi giữ **trong bộ nhớ**. Chỉ ghi DB khi bắt đầu và kết thúc trận.

**Việc cần làm**

🟢 MVP
- [ ] **Tuần 1:** lá bài, bộ bài (xáo theo seed để tái hiện bug), bộ so bài 7 lá, tính pot và Side pot.
- [ ] **Tuần 1–2:** máy trạng thái bàn: bắt đầu ván, blind, các vòng cược, `FOLD/CHECK/CALL/RAISE/ALL_IN`, showdown, chia pot, loại người hết chip, xếp hạng cuối trận.
- [ ] **Tuần 2:** bài mô phỏng: bot tự chơi hàng nghìn ván, kiểm tra tổng chip không đổi và không có exception.
- [ ] `POST /internal/tables` (CreateTable), đọc Match Settings mặc định theo `mode`.
- [ ] WebSocket `/ws/game`: nhận hành động, gửi snapshot riêng từng người, mỗi bàn một khóa riêng.
- [ ] Timer lượt: hết giờ thì tự Check nếu được, không thì Fold. Timer phải kiểm tra số thứ tự hành động để không xử lý nhầm lượt cũ.
- [ ] Reconnect: `GET /api/game/me/active-table` để FE biết người chơi đang ở bàn nào sau khi tải lại trang. AFK quá N lượt thì coi như rời bàn.
- [ ] Phát `match.started` và `match.finished` (payload ở mục 6.2).
- [ ] `GET /internal/matches` cho màn Hồ sơ.
- [ ] Dev tool (chỉ bật ở profile `dev`): `POST /dev/tables?humans=...&bots=...` và đăng nhập giả `?devUser=` để tự test không cần người khác.
- [ ] Màn: Bàn chơi (ghế, bài tẩy, bài chung, pot, nút Dealer/SB/BB, timer, thanh raise, popup bảng thứ hạng tay bài), Kết thúc trận (thứ hạng, Elo thay đổi, xu thưởng).
- [ ] Admin: cấu hình Match Settings.

🔵 GĐ2
- [ ] Hỗ trợ tích hợp giải đấu: chạy ổn định nhiều bàn song song, test tải.
- [ ] Sửa bug phát sinh khi tích hợp với matchmaking, Custom Room, giải đấu.

**Các ca luật bắt buộc có unit test**
- Heads-up: Dealer đặt Small Blind và hành động trước ở preflop, sau ở các vòng tiếp theo.
- Big Blind không đủ chip: người khác vẫn call đủ mức Big Blind, phần dư trả lại qua Side pot.
- All-in chưa đủ mức raise tối thiểu: không mở lại quyền raise cho người đã hành động.
- Mọi người all-in: tự lật hết bài chung.
- Tiền cược không ai theo được trả lại.
- Hòa: chia đều pot, chip lẻ cho người gần bên trái Dealer nhất.
- Nhiều người hết chip cùng một ván: ai có nhiều chip hơn lúc đầu ván xếp hạng cao hơn.
- Sảnh A-2-3-4-5, so kicker, Thùng phá sảnh lớn.

**Cung cấp cho người khác:** `CreateTable` (Duy, Hoài Anh cần), `match.started`, `match.finished`, `/internal/matches`, component bàn chơi (Hoài Anh cần cho Tutorial).

**Phụ thuộc và cách mock**

| Cần gì | Của ai | Mock trong lúc chờ |
|---|---|---|
| Người gọi CreateTable | Duy, Hoài Anh | Dev tool `/dev/tables` + bot |
| JWT | Khanh | Đăng nhập giả `?devUser=` ở profile `dev` |
| `<ChatPanel>` | Duy | Để trống vị trí trên màn hình |
| Component skin | Tùng | Dùng hình mặc định |

> ⚠️ **Bảo là critical path.** Hợp đồng `CreateTable` và `match.finished` phải chốt trong tuần 0 để Duy và Hoài Anh làm song song được.

---

### 9.3. 👤 Duy — `matchmaking-service`, `social-service`, `chat-service`

**Sở hữu**
- BE: `matchmaking-service`, `social-service`, `chat-service`. DB: `matchmaking_db`, `social_db`, `chat_db` + Redis.
- Route: `/api/matchmaking/**`, `/api/party/**`, `/api/friends/**`, `/api/blocks/**`, `/api/notifications/**`, `/api/chat/**`, WebSocket `/ws/chat`, `/ws/social`.
- FE: `features/matchmaking`, `features/friends`, `features/notifications`, `shared/chat/` (`<ChatPanel>`).

**Không được làm**
- Không tự trừ xu → gọi `debit` của Tùng.
- Không tự đọc/tính Elo → gọi `/internal/ranking/{id}` của Hoài Anh.
- Không tự mở bàn → gọi `CreateTable` của Bảo.
- Không làm Custom Room → của Hoài Anh (dù cũng là "gom người rồi mở bàn").

**Bảng dữ liệu**

| DB | Bảng | Nội dung chính |
|---|---|---|
| Redis | hàng chờ, proposal, party, presence | dữ liệu tạm |
| `matchmaking_db` | `match_proposals` | id, mode, playerIds, status, feeCharged, tableId — để hoàn phí và truy vết |
| `social_db` | `friend_requests` | requesterId, addresseeId, status (PENDING / ACCEPTED / DECLINED / CANCELLED) |
| `social_db` | `friendships` | accountId, friendId — lưu 2 chiều |
| `social_db` | `blocks` | blockerId, blockedId |
| `social_db` | `notifications` 🔵 | accountId, type, title, body, data, readAt |
| `chat_db` | `conversations` | id, type (DIRECT / MATCH), refId (matchId nếu MATCH), closedAt |
| `chat_db` | `conversation_members` | conversationId, accountId |
| `chat_db` | `messages` | conversationId, senderId, messageType (TEXT / STICKER / SOUND), content, createdAt |

> Hai bảng `Matches Chat` và `Friend_chats` trong DB cũ được thay bằng 3 bảng `conversations`, `conversation_members`, `messages`. Mỗi tin nhắn là **một dòng**, không lưu cả cuộc trò chuyện trong một cột JSON.

**Việc cần làm**

> **Trạng thái 2026-10-15:** `matchmaking-service`, `social-service`, `chat-service` mới là scaffold (chỉ có class `Application`), chưa có nhánh nào trên remote. Đang chặn:
> - Luồng Rank/Normal 7.2, 7.3: không ai gọi CreateTable cho `RANK`/`NORMAL`.
> - Thông báo realtime: competition và ranking đã phát `notification.requested` (`ROOM_INVITE`, `ROOM_UPDATED`, `ROOM_STARTED`, `TOURNAMENT_INVITE`, `TOURNAMENT_TABLE_READY`, `SEASON_REWARD_AVAILABLE`) nhưng chưa có ai đẩy xuống người chơi.
> - Mời vào Custom Room: ngoài profile dev, competition gọi `/internal/friends/{a}/with/{b}` và lỗi vì endpoint chưa có.
>
> Thứ tự ưu tiên: hàng chờ Rank/Normal → `/internal/friends/**` + consumer `notification.requested` + `/ws/social` → bạn bè → chat.

🟢 MVP
- [ ] Hàng chờ Rank: vào/hủy hàng chờ, ghép theo Elo và chuỗi thắng, nới dần khoảng Elo khi chờ lâu, số người tối thiểu lấy từ config.
- [ ] Proposal và popup chấp nhận 10 giây, thu phí, hoàn phí khi lỗi (luồng 7.2).
- [ ] Hàng chờ Normal (miễn phí).
- [ ] Bạn bè: gửi/nhận/đồng ý/từ chối/hủy lời mời, xóa bạn, chặn/bỏ chặn.
- [ ] `/internal/friends/**` cho Hoài Anh và chat.
- [ ] `chat-service`: chat DIRECT giữa bạn bè và chat MATCH trong trận (TEXT). Mở/đóng phòng chat trận theo `match.started` / `match.finished`.
- [ ] Component `<ChatPanel conversationId>` dùng chung cho màn Bạn bè và màn Bàn chơi.
- [ ] WebSocket `/ws/social` + consumer `notification.requested` → đẩy realtime (cần cho `MATCH_FOUND`, `ROOM_UPDATED`...).
- [ ] Màn: Chờ ghép trận, popup chấp nhận trận, Bạn bè (danh sách, lời mời, đã chặn), Chat.

🔵 GĐ2
- [ ] Sticker và sound trong chat (kiểm tra sở hữu qua `/internal/inventory/.../owns/...`), cooldown chống spam.
- [ ] Kiểm tra cấm chat (`restrictions` / `account.penalized`).
- [ ] Party: tạo, mời, kick, rời, chủ party bấm tìm trận Normal, ghép party với người lạ.
- [ ] Presence: online / trong trận / offline.
- [ ] Chuông thông báo: lưu lịch sử, đánh dấu đã đọc.

**API đề xuất** *(đề xuất — Duy được đổi, nhưng phải sửa SPEC)*

`matchmaking-service`

| Method | Endpoint | Body | Ghi chú |
|---|---|---|---|
| POST | `/api/matchmaking/queue` | `{ mode: "RANK" \| "NORMAL" }` | Vào hàng chờ. Đã ở hàng chờ thì 409; bị cấm Rank hoặc không đủ xu thì trả lỗi tương ứng (`MM_...`) |
| DELETE | `/api/matchmaking/queue` | — | Rời hàng chờ |
| GET | `/api/matchmaking/me` | — | `{ state: IDLE \| QUEUED \| PROPOSED \| MATCHED, mode, proposalId, tableId, queuedAt }` để FE khôi phục sau khi tải lại trang |
| POST | `/api/matchmaking/proposals/{id}/accept` | — | Đã có ở 7.2 |
| POST | `/api/matchmaking/proposals/{id}/decline` | — | Từ chối; bị phạt không vào hàng chờ 2 phút (mục 8) |

`social-service`

| Method | Endpoint | Body | Ghi chú |
|---|---|---|---|
| GET | `/api/friends` | — | Danh sách id bạn bè (FE lấy tên qua `/api/profiles/batch`) |
| GET | `/api/friends/requests?direction=INCOMING\|OUTGOING` | — | Lời mời đang chờ |
| POST | `/api/friends/requests` | `{ addresseeId }` | Gửi lời mời, phát `FRIEND_REQUEST` |
| POST | `/api/friends/requests/{id}/accept` | — | Lưu `friendships` 2 chiều, phát `FRIEND_ACCEPTED` |
| POST | `/api/friends/requests/{id}/decline` | — | Người nhận từ chối |
| DELETE | `/api/friends/requests/{id}` | — | Người gửi hủy lời mời |
| DELETE | `/api/friends/{friendId}` | — | Xóa bạn (cả 2 chiều) |
| GET | `/api/blocks` | — | Danh sách đã chặn |
| PUT | `/api/blocks/{accountId}` | — | Chặn |
| DELETE | `/api/blocks/{accountId}` | — | Bỏ chặn |

Ca biên cần xử lý: gửi lời mời cho chính mình, cho người đã là bạn, giữa hai người có chặn nhau → từ chối. Hai người gửi lời mời cho nhau → *(đề xuất)* tự thành bạn. Chặn người đang là bạn → *(đề xuất)* xóa quan hệ bạn và lời mời đang chờ. Cách tìm người để kết bạn (theo id, email hay tên) cần Duy và Khanh chốt; tìm theo tên thì Khanh phải có API tìm kiếm hồ sơ.

`chat-service`

| Method | Endpoint | Body | Ghi chú |
|---|---|---|---|
| GET | `/api/chat/conversations` | — | Các hội thoại DIRECT của mình, kèm tin nhắn cuối |
| POST | `/api/chat/conversations/direct` | `{ friendId }` | Lấy hoặc tạo hội thoại DIRECT; chỉ khi là bạn và không chặn nhau (hỏi social) |
| GET | `/api/chat/conversations/{id}/messages?before=&size=` | — | Lịch sử tin nhắn, phân trang lùi theo thời gian; chỉ thành viên được xem |

- Hội thoại MATCH dùng luôn `matchId` làm `id` để khớp `<ChatPanel conversationId={matchId}>` ở mục 9.2 *(đề xuất)*. Tạo khi nhận `match.started` (thành viên = `playerIds`), đặt `closedAt` khi nhận `match.finished`; đã đóng thì không gửi thêm được.
- `/ws/social` đẩy xuống `/user/queue/notifications` nội dung `{ type, title, body, data, occurredAt }` lấy từ event *(đề xuất)*. MVP chưa lưu lịch sử thông báo (bảng `notifications` là GĐ2), người đang offline sẽ không nhận được.

**Hợp đồng các bên đã triển khai** (đọc code ngày 2026-10-15, để gọi đúng)

- **CreateTable** — `POST http://game-service:8082/internal/tables`, body ở 6.1:
  - Ngoài profile dev phải gửi header `X-Game-Service-Key` bằng biến `GAME_SERVICE_KEY`. Game chạy profile `dev` thì không cần key và bỏ qua kiểm tra restrictions, dùng được để test thật.
  - `sourceId` = `proposalId`. Game idempotent theo `(mode, sourceId)`, nên gọi lại cùng body sau timeout là an toàn.
  - `playerIds` không trùng, số người trong khoảng `minPlayers`–`maxPlayers` của Match Settings theo mode (mặc định 2–6). `NORMAL` phải có `entryFee = 0`.
  - Lỗi trả `{ code, message, timestamp, requestId }`: 409 `GAME_PLAYER_BUSY` (có người đang ở bàn khác), 409 `GAME_SOURCE_CONFLICT` (`sourceId` đã dùng với body khác), 403 `GAME_FORBIDDEN` (có người bị cấm), 400 `GAME_INVALID_REQUEST`, 503 `GAME_DEPENDENCY_UNAVAILABLE` (game không gọi được auth). Mọi lỗi: hoàn phí toàn bộ theo 7.2 bước 5; riêng 400 và `GAME_SOURCE_CONFLICT` là lỗi code, không retry.
  - Game chưa có API nội bộ kiểm tra "đang trong trận" (7.2 bước 1); MVP dựa vào lỗi 409 `GAME_PLAYER_BUSY` (không cho biết người nào bận).
- **Ranking** — `GET http://ranking-service:8086/internal/ranking/{accountId}` trả JSON thô `{ accountId, elo, tier, winStreak, lossStreak }`.
- **Wallet** — nhánh `feature/wallet-shop-service` của Tùng, chưa merge: `POST /internal/wallet/debit` và `/credit` body như 6.1; response thành công bọc `{ code, message, data, timestamp }`; `GET /internal/wallet/{accountId}/balance` có số dư xu ở `data.balance`; không đủ xu trả HTTP 400 với `code = WALLET_INSUFFICIENT_BALANCE`. Xác nhận lại với Tùng khi merge.
- **Restrictions** — `GET http://auth-service:8090/internal/accounts/{id}/restrictions`, JSON thô (6.1). Khanh chưa làm; trong lúc chờ mock trả không bị phạt.
- **Event** — envelope `{ eventId, eventType, occurredAt, payload }` (class `EventEnvelope` trong `common`). Queue: `social-service.notification.requested`, `chat-service.match.started`, `chat-service.match.finished`. Payload `notification.requested` các service đang phát: `{ accountIds, type, title, body, data }`. Bỏ qua `type` và field lạ, chống trùng bằng `eventId`.
- **Xác thực** — REST qua gateway: đọc `X-User-Id`, `X-User-Role`, không tự parse JWT. WebSocket `/ws/chat`, `/ws/social`: FE gửi `Authorization: Bearer <jwt>` trong STOMP `CONNECT` (JWT lấy từ `GET /api/auth/ws-token`); service kiểm tra RS256 qua JWKS `http://auth-service:8090/.well-known/jwks.json`, `iss` = `JWT_ISSUER` (mặc định `http://localhost:8090`), `aud` chứa `poker`, `sub` là UUID tài khoản. Tham khảo `GameIdentity` và `GameChannelInterceptor` trong `game-service`.

**Cung cấp cho người khác:** `<ChatPanel>` (Bảo cần), `/internal/friends/**` (Hoài Anh cần), kênh `notification.requested` → realtime (cả nhóm cần).

**Phụ thuộc và cách mock**

| Cần gì | Của ai | Mock trong lúc chờ |
|---|---|---|
| `CreateTable` | Bảo | Đã có: chạy game ở profile `dev` (không cần key). Hoặc trả `tableId` ngẫu nhiên, 10 giây sau tự phát `match.finished` giả |
| Elo | Hoài Anh | Đã có trên `develop`; mock mọi người Elo 0 nếu không chạy ranking |
| `debit`/`credit` | Tùng | Service giả luôn thành công (wallet chưa merge) |
| `restrictions` | Khanh | Luôn trả không bị phạt |

---

### 9.4. 👤 Hoài Anh — `ranking-service`, `competition-service`

**Sở hữu**
- BE: `ranking-service`, `competition-service`. DB: `ranking_db`, `competition_db`.
- Route: `/api/ranking/**`, `/api/seasons/**`, `/api/tutorial/**`, `/api/rooms/**`, `/api/tournaments/**`, `/api/admin/seasons/**`, `/api/admin/tournaments/**`.
- FE: `features/ranking`, `features/season`, `features/custom-room`, `features/tournament`, `features/tutorial`, `features/admin/seasons`, `features/admin/tournaments`.

**Không được làm**
- Không chạy logic ván bài (kể cả trong Tutorial phía BE) → Tutorial là kịch bản chạy ở FE, dùng lại component bàn chơi của Bảo.
- Không tự cộng xu hay trao skin → gọi API của Tùng.
- Không đọc DB bạn bè → gọi `/internal/friends` của Duy.

**Bảng dữ liệu**

| DB | Bảng | Nội dung chính |
|---|---|---|
| `ranking_db` | `seasons` | name, startAt, endAt, status (UPCOMING / ACTIVE / ENDED), resetPercent |
| `ranking_db` | `season_tier_rewards` | seasonId, tier, rewardType (COIN / PRODUCT), amount, productId |
| `ranking_db` | `player_elo` | seasonId, accountId, currentElo, peakElo, tier, winStreak, lossStreak, matchesPlayed, lastMatchAt |
| `ranking_db` | `elo_history` | accountId, matchId, delta, eloAfter — unique (accountId, matchId) để chống xử lý trùng |
| `ranking_db` | `season_reward_claims` 🔵 | seasonId, accountId, tier, claimedAt |
| `competition_db` | `custom_rooms` | roomCode, ownerId, maxPlayers, smallBlind, bigBlind, visibility, passwordHash, status |
| `competition_db` | `room_members` | roomId, accountId, isReady, joinedAt |
| `competition_db` | `tournaments` 🔵 | type (ELITE / OPEN), startAt, entryFee, status, rewards |
| `competition_db` | `tournament_entries` 🔵 | tournamentId, accountId, status (INVITED / CONFIRMED / REGISTERED / ELIMINATED), finalPlace |
| `competition_db` | `tournament_tables` 🔵 | tournamentId, round (QUALIFIER / FINAL), tableId, matchId, status |

**Việc cần làm**

🟢 MVP
- [ ] Nhận `account.registered` → tạo `player_elo` ở mùa đang ACTIVE (seed sẵn 1 mùa bằng SQL).
- [ ] Nhận `match.finished` (`RANK`) → cập nhật Elo theo bảng mục 8, bậc rank, chuỗi thắng/thua, gọi `credit` thưởng top 3.
- [ ] Tính Thần bài = top 50 trong nhóm Đại kiện tướng.
- [ ] `/internal/ranking/{id}`, `/internal/ranking/top`.
- [ ] Bảng xếp hạng toàn server, có phân trang và vị trí của chính mình.
- [ ] Custom Room: tạo, sinh Room ID, Private/Public/mật khẩu, danh sách phòng Public, vào bằng Room ID, sẵn sàng, kick, chuyển quyền chủ phòng, mời bạn, bắt đầu (luồng 7.4).
- [ ] **Backend giải đấu** (làm sớm với mock `CreateTable`): tạo giải, đăng ký, logic chia bàn và lấy top 2 vào chung kết.
- [ ] Màn: Bảng xếp hạng, Custom Room (tạo, tìm, phòng chờ).

🔵 GĐ2
- [ ] Giải Cao thủ và Giải Mở chạy thật (luồng 7.7), bracket cập nhật realtime, bù người vắng, prize pool, trao thưởng.
- [ ] Mùa giải: đóng mùa, reset Elo theo %, quà dồn theo bậc, nhận quà.
- [ ] BXH bạn bè: `GET /api/ranking/friends` trả người gọi và bạn bè (lấy qua `/internal/friends/{accountId}`, tối đa 200 người), mỗi dòng `{ rank, position, accountId, elo, tier, winStreak, matchesPlayed, you }`. `position` là vị trí trên BXH toàn server; bạn chưa đánh Rank trong mùa xếp cuối với Elo khởi điểm và `position = null`.
- [ ] Tutorial: kịch bản ván bài dựng sẵn ở FE, hướng dẫn từng nút, hoàn thành thì FE gọi `POST /api/tutorial/complete`, ranking-service gọi `credit` `TUTORIAL_REWARD` 200 xu (mục 8) với key `tutorial:{accountId}` (chỉ thưởng một lần). `GET /api/tutorial/me` trả `{ completed, completedAt, rewardCoins }` để FE biết người chơi đã hoàn thành chưa.
- [ ] Admin: quản lý mùa giải, quà theo bậc, lịch và cấu hình giải.

**Cung cấp cho người khác:** `/internal/ranking/**` (Duy cần), dữ liệu Elo/bậc cho màn Hồ sơ và Sảnh chờ (Khanh cần).

**Phụ thuộc và cách mock**

| Cần gì | Của ai | Mock trong lúc chờ |
|---|---|---|
| `CreateTable` + `match.finished` | Bảo | Gửi `match.finished` giả bằng tay qua RabbitMQ UI hoặc một test endpoint |
| `/internal/friends` | Duy | Coi như ai cũng là bạn |
| `credit`, `inventory/grant` | Tùng | Service giả luôn thành công |
| Component bàn chơi (Tutorial) | Bảo | Làm Tutorial sau cùng, khi component đã ổn định |

---

### 9.5. 👤 Tùng — `shop-service`, `wallet-service`, skin

**Sở hữu**
- BE: `shop-service`, `wallet-service`. DB: `shop_db`, `wallet_db`.
- Route: `/api/shop/**`, `/api/inventory/**`, `/api/payments/**`, `/api/wallet/**`, `/api/admin/products/**`.
- FE: `features/shop`, `features/inventory`, `features/wallet`, `features/guide`, `features/admin/products`, `shared/skin/`.

**Không được làm**
- Không để service khác ghi vào ví → mọi thay đổi số dư chỉ đi qua `credit`/`debit`.
- Không vẽ bàn chơi → chỉ cung cấp component skin, Bảo gắn vào.

**Bảng dữ liệu**

| DB | Bảng | Nội dung chính |
|---|---|---|
| `wallet_db` | `wallets` | accountId (unique), balance, version (optimistic lock) |
| `wallet_db` | `wallet_transactions` | accountId, amount (+/−), reason, refId, idempotencyKey (**unique**), balanceAfter, createdAt |
| `shop_db` | `categories` | code (CARD_BACK / TABLE / FRAME / DEALER / VICTORY_EFFECT / COIN_SKIN / EMOJI / SOUND), name |
| `shop_db` | `products` | categoryId, name, description, tier (NORMAL / VIP / SUPER_VIP), priceVnd, assetUrl, isExclusive (chỉ trao qua giải) |
| `shop_db` | `orders` | accountId, productId, amountVnd, payosOrderCode (**số**, unique), status (PENDING / PAID / CANCELLED / EXPIRED), paidAt |
| `shop_db` | `inventory_items` | accountId, productId, source (PURCHASE / REWARD), isEquipped, acquiredAt — unique (accountId, productId) |

> Số dư ví luôn phải bằng tổng `amount` trong `wallet_transactions`. Trừ xu dùng khóa (optimistic lock hoặc `SELECT ... FOR UPDATE`) để hai request cùng lúc không làm số dư âm.

**Việc cần làm**

🟢 MVP
- [ ] **Tuần 1 (ưu tiên số 1):** `wallet-service` — `credit`, `debit`, `balance`, idempotency, khóa chống âm số dư. Nhận `account.registered` để tạo ví.
- [ ] `GET /api/wallet/me`, `GET /api/wallet/me/transactions`.
- [ ] Shop: danh sách sản phẩm theo danh mục/tier, chi tiết, xem trước, chặn mua món đã có. Seed sản phẩm bằng SQL.
- [ ] Thanh toán PayOS (luồng 7.5): tạo đơn, tạo link/QR, webhook có kiểm tra chữ ký, trang thành công/hủy, job kiểm tra đơn treo. Test webhook ở local qua ngrok hoặc Cloudflare Tunnel.
- [ ] Túi đồ: danh sách đồ, trang bị/gỡ (mỗi danh mục tối đa 1 món được trang bị).
- [ ] `/internal/inventory/.../owns/...`, `/internal/inventory/grant`.
- [ ] `shared/skin/`: tạo đủ các component (`<CardBack>`, `<TableTheme>`, `<AvatarFrame>`, `<Dealer>`, `<VictoryEffect>`) hiển thị hình mặc định. **Riêng `<AvatarFrame>` đọc skin đang trang bị** để demo trọn luồng mua → thấy.
- [ ] Màn: Shop, Thanh toán (kết quả), Túi đồ, Ví.

🔵 GĐ2
- [ ] Các component skin còn lại đọc skin đang trang bị.
- [ ] Hướng dẫn: luật chơi, bảng thứ hạng tay bài, từ điển thuật ngữ, hướng dẫn hệ thống, luật xử phạt, FAQ (nội dung nhờ Bảo/Hoài Anh duyệt vì liên quan luật poker).
- [ ] Admin: CRUD sản phẩm/danh mục, xem đơn hàng và doanh thu.

**Cung cấp cho người khác:** `credit`/`debit`/`balance` (Khanh, Duy, Hoài Anh cần ngay tuần 1), `/internal/inventory/**` (Duy, Hoài Anh), `shared/skin/` (Bảo, Khanh).

**Phụ thuộc và cách mock**

| Cần gì | Của ai | Mock trong lúc chờ |
|---|---|---|
| `account.registered` | Khanh | Tạo ví tự động ở lần gọi đầu tiên nếu chưa có |
| Tài khoản PayOS | — | Đăng ký sớm ngay tuần 0 để có key |

---

## 10. Frontend

Một repo `MS-Poker-FE`. Mỗi người chỉ làm trong thư mục của mình.

```
src/
├── shared/                 ← Khanh: layout, UI kit, router, API client, lưu token
│   ├── skin/               ← Tùng: <CardBack> <TableTheme> <AvatarFrame> <Dealer> <VictoryEffect>
│   └── chat/               ← Duy: <ChatPanel conversationId>
└── features/
    ├── auth/               ← Khanh
    ├── lobby/              ← Khanh
    ├── checkin/            ← Khanh
    ├── profile/            ← Khanh
    ├── report/             ← Khanh
    ├── game-table/         ← Bảo
    ├── matchmaking/        ← Duy
    ├── friends/            ← Duy
    ├── notifications/      ← Duy
    ├── ranking/            ← Hoài Anh
    ├── season/             ← Hoài Anh
    ├── custom-room/        ← Hoài Anh
    ├── tournament/         ← Hoài Anh
    ├── tutorial/           ← Hoài Anh
    ├── shop/               ← Tùng
    ├── inventory/          ← Tùng
    ├── wallet/             ← Tùng
    ├── guide/              ← Tùng
    └── admin/
        ├── users/          ← Khanh
        ├── match-settings/ ← Bảo
        ├── seasons/        ← Hoài Anh
        ├── tournaments/    ← Hoài Anh
        └── products/       ← Tùng
```

**Quy tắc FE**

- Mỗi feature tự khai báo route của mình trong file `routes` riêng. Router gốc trong `shared/` chỉ gom lại, không ai sửa route của người khác.
- Tiền tố URL theo feature: `/lobby`, `/table/:tableId`, `/matchmaking`, `/friends`, `/ranking`, `/rooms`, `/tournaments`, `/tutorial`, `/shop`, `/inventory`, `/wallet`, `/guide`, `/admin/...`.
- Feature không import code nội bộ của feature khác. Cần dùng chung thì chuyển vào `shared/` qua PR.
- Mỗi WebSocket (`/ws/game`, `/ws/chat`, `/ws/social`) do chủ sở hữu tự quản lý trong feature của mình. Khanh chỉ cung cấp hàm lấy token.
- Mọi hiển thị tên/avatar người chơi dùng một hook chung `useProfiles(ids)` trong `shared/` (gọi `/api/profiles/batch`, có cache).
- Mọi hiển thị bài, bàn, khung, người chia bài, hiệu ứng chiến thắng **bắt buộc** qua `shared/skin/`.
- Các component bàn chơi của Bảo (ghế, lá bài, bài chung, nút hành động) tách thành component độc lập, nhận dữ liệu qua props, để Tutorial của Hoài Anh dùng lại.

---

## 11. Lộ trình

Gợi ý theo tuần, điều chỉnh theo deadline môn học.

| Tuần | Mục tiêu | Ai |
|---|---|---|
| **0** | Init repo BE/FE, docker-compose, `common`. **Họp chốt hợp đồng mục 6.** Đăng ký tài khoản PayOS | Khanh init, cả nhóm họp |
| **1** | Gateway + đăng ký/đăng nhập · Ví xu · Bộ so bài + Side pot · Bạn bè · Custom Room BE · Khung FE | Mỗi người phần của mình |
| **2** | Máy trạng thái bàn + mô phỏng bot · Hàng chờ Rank/Normal · Elo + BXH · Shop + PayOS · Điểm danh + Hồ sơ | |
| **3** | WebSocket bàn chơi + timer · Chat service + `<ChatPanel>` · Túi đồ + skin khung · Backend giải đấu (mock) | |
| **4** | **Tích hợp MVP:** chạy trọn luồng 7.1 → 7.6 với 5 người thật. Sửa bug | Cả nhóm |
| **5–6** | GĐ2: giải đấu, mùa giải, tutorial, skin, sticker/sound, party, thông báo, báo cáo, admin, hướng dẫn | Mỗi người phần GĐ2 của mình |
| **7** | Test tổng, sửa bug, chuẩn bị demo, deploy (+ Cloudflare nếu cần) | Cả nhóm |

**Mốc kiểm tra cuối tuần 4 (MVP xong khi):** 5 thành viên đăng ký tài khoản mới, nhận xu lần đầu, điểm danh, kết bạn, chat, cùng vào một trận Rank, chơi hết trận, thấy Elo và xu thay đổi đúng, sau đó mua một khung avatar qua PayOS và thấy khung hiển thị.

---

## 12. Definition of Done

Một tính năng chỉ được coi là **xong** khi:

- [ ] Code nằm đúng module/thư mục của người sở hữu, không sửa file của người khác ngoài quy trình mục 4.
- [ ] API và event đúng hợp đồng trong file này. Nếu có thay đổi thì `SPEC.md` đã được cập nhật trong cùng PR.
- [ ] Có unit test cho logic nghiệp vụ chính. Riêng engine poker, ví xu và tính Elo phải test các ca biên.
- [ ] Mọi thao tác cộng/trừ xu, cấp vật phẩm có `idempotencyKey`. Consumer event bỏ qua event trùng.
- [ ] Trả lỗi theo định dạng chuẩn mục 5.3.
- [ ] Chạy được bằng `docker compose up` cùng các service khác.
- [ ] Màn hình FE có trạng thái đang tải, trạng thái rỗng và thông báo lỗi dễ hiểu.
- [ ] PR được ít nhất 1 người review và approve.
