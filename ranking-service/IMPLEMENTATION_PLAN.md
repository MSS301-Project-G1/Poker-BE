# Hoài Anh — kế hoạch triển khai ranking-service và FE

Nguồn yêu cầu: `docs/SPEC.md` §2.3, §5, §6, §7.1, §7.2, §8, §9.4, §10, §12.
Chỉ sở hữu `ranking-service`, FE `features/ranking`, `features/season`,
`features/tutorial`, `features/admin/seasons`. Không chạy logic ván bài, không tự
cộng xu, không đọc DB bạn bè. Thay đổi file của người khác (root compose, `common`,
router/shared FE) phải hỏi chủ module trước. Custom Room và giải đấu nằm trong
`competition-service/IMPLEMENTATION_PLAN.md`.

Cấu trúc code theo `game-service`: package phẳng theo tầng, controller trả DTO,
lỗi theo `ErrorResponse` §5.3 với tiền tố `RANK_`, MapStruct, Lombok, Flyway,
PostgreSQL riêng trong `compose.yml`, test dùng H2.

## Các đợt và tiêu chí hoàn thành

0. **Khung module**: dependency, cấu hình, `ranking-db` riêng trên `ranking-net`,
   xử lý lỗi chuẩn; `clean verify` qua với context test H2.
1. **Dữ liệu và bậc rank**: migration `seasons`, `player_elo`, `elo_history`,
   seed một mùa ACTIVE; tính bậc theo mốc §8, Elo không âm, Thần bài = top 50
   Đại kiện tướng. Unit test ranh giới các mốc.
2. **Event**: queue `ranking-service.account.registered` → tạo `player_elo` mùa
   hiện tại; queue `ranking-service.match.finished` → chỉ `RANK` cập nhật Elo,
   peak, chuỗi thắng (lọt top 3 liên tiếp)/thua, số trận; `elo_history`
   unique `(accountId, matchId)` chống xử lý trùng. Test event trùng, mode khác RANK.
3. **Thưởng top 3**: `credit` `RANK_REWARD` 3×/2×/1× phí, key
   `rank-reward:{matchId}:{accountId}`; client ví qua interface, bản giả khi
   wallet chưa merge; lỗi ví không làm mất thưởng (thử lại).
4. **API nội bộ**: `GET /internal/ranking/{accountId}` →
   `{ elo, tier, winStreak, lossStreak }`; `GET /internal/ranking/top?limit=32`.
5. **API public**: bảng xếp hạng mùa hiện tại có phân trang, vị trí của mình,
   mùa hiện tại (cho Sảnh chờ/Hồ sơ của Khanh).
6. **FE MVP** `features/ranking`: màn Bảng xếp hạng (loading/empty/error).
7. **GĐ2**: mùa giải (đóng mùa, giữ 50% Elo, quà dồn theo bậc, nhận quà,
   `season_tier_rewards`, `season_reward_claims`, `SEASON_REWARD_AVAILABLE`);
   BXH bạn bè; Tutorial (`POST /api/tutorial/complete`, `TUTORIAL_REWARD`, key
   `tutorial:{accountId}`); Admin mùa giải/quà theo bậc và các màn FE tương ứng.

Mỗi đợt dừng để review trước khi sang đợt sau. Quy trình nhóm: mỗi thành viên có
một nhánh riêng rồi merge vào `develop`; toàn bộ phần của Hoài Anh (ranking và
competition) commit vào `hoaianh/ranking-elo`. Commit theo Conventional Commits với
scope `ranking`/`competition`, không push main.

## Các lựa chọn được áp dụng

- Tham số §8 nằm trong `ranking.*` của `application.properties`, chỉnh không sửa code.
- Mặc định dev giống game: PostgreSQL `127.0.0.1:54331/ranking_db`, RabbitMQ dùng
  chung `docker/compose.infra.yml` (`127.0.0.1:56729`).

## Quyết định đã chốt với Hoài Anh

- Elo và thưởng dùng nguyên bảng §8 theo thứ hạng, kể cả bàn ít hơn 6 người:
  hạng 1..6 nhận +20/+10/+5/−5/−10/−15 (hạng sau 6 dùng giá trị cuối), top 3
  nhận 3×/2×/1× phí. Bàn 2 người: +20/+10, thưởng 3×/2× phí.
- Không thêm khóa ingress riêng: chỉ tin `X-User-Id`/`X-User-Role` do gateway gắn
  (§5.1); `/internal/**` không mở qua gateway.
- Root `docker-compose.yml` (Khanh) được đưa lại về dạng include ngay trên
  `hoaianh/ranking-elo`, kèm `auth-service/compose.yml`; Khanh cần xem khi merge.
- Quà mùa tính theo bậc lúc đóng mùa (không theo peak). Mùa tự đóng khi qua
  `endAt` nếu admin đã tạo mùa kế tiếp; admin vẫn đóng tay được.

## Tiến độ

- [x] Đợt 0: khung module.
- [x] Đợt 1: dữ liệu và bậc rank (V1 bảng, V2 seed "Mùa 1").
- [x] Đợt 2: event (queue riêng, chống trùng theo `elo_history`).
- [x] Đợt 3: thưởng top 3 (`rank_reward_credits` + dispatcher thử lại; ví giả ở dev/test).
- [x] Đợt 4: API nội bộ.
- [x] Đợt 5: API public (`/api/ranking/leaderboard`, `/api/ranking/me`, `/api/seasons/current`).
  22 test H2; chạy thật với PostgreSQL/RabbitMQ Docker: event publish qua
  `poker.events`, event trùng và message hỏng bị bỏ qua, 3 khoản thưởng được gửi.
- [x] Đợt 6: FE Bảng xếp hạng (`Poker-FE/src/features/ranking`, route `/ranking`,
  xem `Poker-FE/docs/ranking-rooms-integration.md`). Lint/build qua; kiểm tra trên
  trình duyệt với ranking-service dev: mùa, vị trí của mình, top 3, dòng "You".
  Đã chuyển sang kiến trúc FE mới của `develop` (`docs/ARCHITECTURE.md`): route trong
  `app/router.jsx` + `paths.js`, API qua `httpClient`, CSS Module + token. Ở profile
  `dev`, service nhận `X-Dev-User` thay `X-User-Id` (như game-service).
- [ ] Đợt 7: GĐ2.
  - [x] Mùa giải BE: V3 (`season_tier_rewards`, `season_reward_claims`,
    `reward_grants`, `ranking_outbox`, `player_elo.final_tier`); Admin tạo mùa/quà,
    đóng mùa tay hoặc tự động khi qua `endAt` và đã có mùa kế tiếp; bậc cuối mùa
    chụp theo BXH (có Thần bài); giữ `resetPercent` Elo; quà dồn theo bậc, nhận một
    lần, xu qua ví và skin qua shop với idempotencyKey; `SEASON_REWARD_AVAILABLE`.
    29 test H2; chạy thật trên PostgreSQL (DB tạm) đóng mùa, nhận quà, gửi 6 khoản.
  - [ ] Tutorial, BXH bạn bè, FE mùa giải/admin.

## Phụ thuộc và review trước khi merge

- Khanh: `account.registered` (đã có trong auth-service), gateway/route
  `/api/ranking/**`, `/api/seasons/**`, `/api/tutorial/**`.
- Bảo: payload `match.finished` (đã có), quy tắc hòa thứ hạng khi bị loại cùng ván.
- Tùng: `credit` (nhánh `feature/wallet-shop-service` chưa merge).
- Duy: người gọi `/internal/ranking/{id}`; `/internal/friends` cho BXH bạn bè (GĐ2).
