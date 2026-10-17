# Hoài Anh — kế hoạch triển khai competition-service và FE

Nguồn yêu cầu: `docs/SPEC.md` §2.4, §5, §6, §7.4, §7.7, §8, §9.4, §10, §12.
Chỉ sở hữu `competition-service`, FE `features/custom-room`, `features/tournament`,
`features/admin/tournaments`. Không chạy logic ván bài, không tự cộng/trừ xu,
không đọc DB bạn bè. Elo/BXH nằm trong `ranking-service/IMPLEMENTATION_PLAN.md`.

Cấu trúc code theo `game-service`: package phẳng theo tầng, controller trả DTO,
lỗi theo `ErrorResponse` §5.3 với tiền tố `COMP_`, MapStruct, Lombok, Flyway,
PostgreSQL riêng `competition-db` trong `compose.yml`, outbox như game, test H2.
Quy trình nhóm: toàn bộ phần của Hoài Anh commit vào `hoaianh/ranking-elo`.

## Các đợt và tiêu chí hoàn thành

0. **Khung module**: dependency, cấu hình, `competition-db` trên `competition-net`.
1. **Custom Room** (luồng 7.4): tạo phòng + Room ID, Public/Private/mật khẩu, danh
   sách Public, vào bằng Room ID, rời, sẵn sàng, kick, chuyển chủ phòng, mời bạn
   (`ROOM_INVITE`), `ROOM_UPDATED` mỗi thay đổi, bắt đầu → `CreateTable` CUSTOM →
   `ROOM_STARTED`; nhận `match.finished` CUSTOM → phòng về chờ.
2. **Backend giải đấu** (MVP, làm sớm với game dev): tạo giải (Admin), đăng ký Giải
   Mở (`debit` `TOURNAMENT_ENTRY_FEE`, 32 suất), chia 4 bàn × 8 →
   `CreateTable` TOURNAMENT ×4 → nhận đủ 4 `match.finished` → top 2 mỗi bàn → chung kết.
3. **FE MVP** `features/custom-room`: tạo, tìm, phòng chờ.
4. **GĐ2**: Giải Cao thủ (mời top 32 qua `/internal/ranking/top`, bù người vắng),
   lịch tự động, hủy giải và hoàn phí, prize pool 50/30/20, trao skin
   (`/internal/inventory/grant`), bracket realtime, Admin và các màn FE giải đấu.

## Quyết định đã chốt với Hoài Anh

- `sourceId` của CreateTable là UUID mới cho mỗi lần bắt đầu, lưu ở
  `custom_rooms.current_source_id` (SPEC §7.4 đã cập nhật). Source được lưu trước
  khi gọi game nên bấm lại sau timeout dùng lại đúng bàn; nếu game báo
  `GAME_SOURCE_CONFLICT` thì cấp source mới và thử lại một lần.
- Chỉ tin `X-User-Id` do gateway gắn; gọi game kèm `X-Game-Service-Key` nếu cấu hình.

## Các lựa chọn được áp dụng

- PUBLIC hiện trong danh sách, PRIVATE chỉ vào bằng mã; mật khẩu tùy chọn cho cả hai,
  ai vào (trừ chủ phòng) cũng phải nhập, kể cả người được mời.
- Mã phòng 6 ký tự, bỏ 0/O/1/I. Mỗi người chỉ ở một phòng chưa đóng.
- Thứ tự vào phòng là thứ tự ghế. Chủ phòng không cần bấm sẵn sàng. Phòng đang
  chơi không cho vào/rời/kick/đổi sẵn sàng; hết trận mọi người về chưa sẵn sàng.
- Chủ rời phòng → người vào sớm nhất làm chủ; người cuối rời → phòng `CLOSED`.
- `custom_rooms` chỉ lưu blind; chip khởi điểm 1000 và lượt 20 giây lấy từ
  `competition.room.*` (mặc định §8). Số người tối đa 2–6 theo CUSTOM của game.
- Dev/test: bạn bè giả "ai cũng là bạn"; game gọi thật (game dev không cần key).
- Giải đấu: số bàn vòng loại = ⌈n/8⌉, chia đều sau khi xáo, mỗi bàn lấy top 2 vào
  chung kết (32 người → 4×8 → 8 như §2.4). Tối đa 32 suất; ≤ 8 người thì đấu thẳng
  chung kết; ít hơn `competition.tournament.min-players` (4) thì không bắt đầu được.
- Admin bấm bắt đầu/hủy; GĐ2 thêm lịch tự động. Đăng ký đóng khi tới `startAt`.
- Phí trừ khi đăng ký (key `tournament-fee:{tournamentId}:{accountId}`), không có
  rút đăng ký. Hủy giải hoàn phí qua `wallet_credits` (key
  `tournament-refund:{tournamentId}:{accountId}`), gửi lại tới khi ví nhận.
- Bàn giải dùng Match Settings TOURNAMENT của game (`settings = null`), `entryFee`
  gửi kèm là phí giải. `sourceId` = id bàn trong `tournament_tables`; bàn chưa mở
  được ở trạng thái PENDING và được thử lại mỗi 2 giây.
- Thêm bảng `tournament_seats` (ghế và thứ hạng từng bàn) và `wallet_credits`
  ngoài danh sách bảng của SPEC.
- Giải Cao thủ: mời top `slots` lúc T-30 phút, ai được mời trước mốc T-10 phải xác
  nhận trước T-10; tới T-10 lời mời chưa xác nhận thành `EXPIRED` và mời hạng kế
  tiếp vào chỗ trống (một đợt bù, người được bù xác nhận tới giờ đấu). Tới giờ đấu
  lời mời còn lại hết hạn. Giải Cao thủ phải tạo trước giờ đấu ít nhất 30 phút.
- Lịch tự động chạy mỗi 30 giây cho cả hai loại: tới `startAt` đủ 4 người thì bắt
  đầu, thiếu thì hủy và hoàn phí. Admin vẫn bắt đầu sớm/hủy tay được.
- Thưởng: pool = phí × số suất + `rewardCoins` (Cao thủ chỉ có `rewardCoins`), chia
  50/30/20 cho top 3 chung kết, phần dư do làm tròn cho hạng 1; chung kết ít hơn 3
  người thì chia lại theo tỷ lệ các hạng có mặt. Skin (`rewardProductId`) chỉ cho vô
  địch, qua bảng `item_grants` gửi lại tới khi shop nhận.

## Tiến độ

- [x] Đợt 0: khung module.
- [x] Đợt 1: Custom Room. 16 test H2; chạy thật với game-service dev, PostgreSQL và
  RabbitMQ: mở bàn CUSTOM blind 250/500, trận kết thúc, `match.finished` đưa phòng
  về chờ và phát `MATCH_FINISHED`.
- [x] Đợt 2: backend giải đấu Giải Mở (tạo, đăng ký trừ phí, bắt đầu, vòng loại →
  chung kết, hủy và hoàn phí). 26 test H2; chạy thật giải 9 người với game dev:
  2 bàn vòng loại 5 + 4 → chung kết 4 người → FINISHED, 3 `TOURNAMENT_TABLE_READY`.
  Còn lại cho GĐ2: Giải Cao thủ, lịch tự động, chia thưởng, trao skin.
- [x] Đợt 3: FE Custom Room (`Poker-FE/src/features/custom-room`, `/rooms`,
  `/rooms/:code`). Kiểm tra trên trình duyệt với 2 người: tạo phòng Private có
  mật khẩu, sai mật khẩu bị từ chối, sẵn sàng, chủ phòng bắt đầu, cả hai tự sang
  bàn CUSTOM của game. Phòng chờ hỏi lại mỗi 3 giây cho tới khi có `/ws/social`.
  Đã chuyển sang kiến trúc FE mới của `develop` và kiểm tra lại cùng luồng trên.
- [ ] Đợt 4: GĐ2.
  - [x] Backend giải đấu GĐ2: V3 (`invites_sent_at`, `replacements_sent_at`,
    `item_grants`), mời top qua `/internal/ranking/top` + `TOURNAMENT_INVITE`,
    `POST /api/tournaments/{id}/confirm`, bù người vắng, lịch tự động bắt đầu/hủy và
    hoàn phí, prize pool 50/30/20 (`TOURNAMENT_PRIZE`), skin cho vô địch qua
    `/internal/inventory/grant`. 34 test H2.
  - [ ] FE giải đấu, bracket cập nhật bằng hỏi lại định kỳ tới khi có `/ws/social`
    (đợt B của FE GĐ2, xem `ranking-service/IMPLEMENTATION_PLAN.md`).
  - [ ] Admin giải đấu (đợt C).

## Phụ thuộc và review trước khi merge

- Bảo: CreateTable/`match.finished` (đã có). Outbox của game dừng ở event đầu tiên
  không có queue nhận: `match.started` chưa có consumer thì `match.finished` cũng
  không đi. Cần chat-service (Duy) bind queue hoặc Bảo xử lý.
- Bảo: restart game-service hủy trận đang chạy mà không phát event nào, nên phòng
  ở lại `PLAYING` và bàn giải đấu ở lại `PLAYING` mãi. Cần event hủy trận (ví dụ
  `match.cancelled`) hoặc thống nhất cách xử lý trước khi demo.
- Duy: consumer `notification.requested` (chưa có, thông báo phòng nằm chờ trong
  outbox), `/internal/friends/{a}/with/{b}`.
- Khanh: route gateway `/api/rooms/**`, `/api/tournaments/**`, `/api/admin/tournaments/**`.
- Tùng: `debit`/`credit` cho phí và thưởng giải; `POST /internal/inventory/grant`
  `{ accountId, productId, idempotencyKey }` cho skin thưởng (shop chưa merge, dev
  dùng bản giả).
