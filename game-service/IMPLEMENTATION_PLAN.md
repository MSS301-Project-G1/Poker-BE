# Bảo — kế hoạch triển khai game-service và FE

Nguồn yêu cầu: `docs/SPEC.md` §5, §6, §8, §9.2, §10, §12 và `AGENTS.md`.
Chỉ sở hữu `game-service`, FE `features/game-table`, `features/admin/match-settings`.
Không thu phí, tính Elo, cộng xu, xây chat hoặc ghép trận. Các thay đổi router/shared
FE cần Khanh/Tùng review; không merge vào main khi chưa có review.

## Các đợt và tiêu chí hoàn thành

1. **Core**: đọc và rà soát Deck, HandEvaluator, PotDistributor; kiểm thử các
   loại bài, kicker, wheel, hai bộ ba, hòa board, side pot, refund, chip lẻ.
   Đã chạy 17 test thành công; commit `fb13249` đã push lên nhánh riêng.
2. **Engine**: trạng thái PREFLOP/FLOP/TURN/RIVER/HAND_FINISHED/FINISHED;
   blind heads-up, raise tối thiểu, short all-in, trả cược dư, xếp hạng.
   Kiểm chứng bằng test ca biên và bot nhiều nghìn ván, bảo toàn chip.
3. **Application + persistence**: CreateTable idempotent theo mode/sourceId,
   không cho một tài khoản ở hai bàn; UUID v7; Match Settings; lịch sử có phân
   trang; lưu lúc bắt đầu/kết thúc và outbox match.started/match.finished.
   Mặc định PostgreSQL; H2 chỉ dev/test. Không sửa DB service khác.
4. **Realtime**: STOMP đúng §6.3; JWT khi CONNECT; kiểm tra membership cho
   SEND/SUBSCRIBE; snapshot cá nhân không lộ bài; một khóa mỗi bàn;
   timeout kiểm tra sequence, AFK, reconnect và dev tool/bot chỉ profile dev.
5. **FE**: giữ thiết kế violet hiện có; feature routes; component theo props
   để Tutorial dùng lại; skin/chat/profiles qua integration adapter khi chủ
   sở hữu chưa cung cấp; loading/empty/error/disconnected; action dock theo
   quyền server; bảng kết quả không tự tính Elo/xu; Admin Match Settings.
6. **Tích hợp**: build/lint FE, clean verify BE, kiểm thử STOMP thật và tải
   nhiều bàn; cập nhật hướng dẫn chạy và ghi rõ phụ thuộc còn thiếu.

Mỗi đợt commit Conventional Commits và push lên nhánh `bao/...`; không push main.
Giữ nguyên thay đổi `.gitignore` có sẵn ngoài phạm vi.

## Các lựa chọn được áp dụng

- Dùng mặc định SPEC: blind 10/20, chip 1000, turn 20s, min 2/max 6;
  TOURNAMENT max 8. Config có thể thay đổi qua Admin.
- Trận kéo dài đến khi còn một người. Sau mỗi ván có khoảng nghỉ cấu hình
  để FE xem showdown, sau đó bắt đầu ván mới. Chip là long, không phải xu.
- Đăng nhập giả, seed có thể tái hiện và bot chỉ bật bằng profile dev.
- Backend sản xuất giữ bài trong RAM đúng SPEC; restart hủy trận RUNNING
  còn dang dở, không tuyên bố có thể khôi phục bài khi chưa persist engine.
- Gateway scaffold chưa có JWT, chưa có route §6.4/WebSocket; tích hợp thật
  cần Khanh cập nhật. Không tin header client trên service public.
- common chưa có BaseEntity/ErrorResponse/event. Dùng DTO trong game-service
  theo JSON contract để không sửa module người khác; chuyển sang common khi
  Khanh cung cấp. Dùng MapStruct cho domain/entity/DTO và Lombok cho boilerplate.
- Skin/chat/useProfiles chưa có trong FE; cung cấp điểm gắn qua props/context,
  không tự xây tính năng thuộc người khác.

## Tiến độ

- [x] Đợt 1: core, tests, commit và push.
- [x] Đợt 2: engine và mô phỏng (7 test engine, hơn 3.000 ván bot).
- [ ] Đợt 3: API/persistence/event.
- [ ] Đợt 4: realtime/timer/dev tool.
- [ ] Đợt 5: FE/Admin.
- [ ] Đợt 6: kiểm chứng tích hợp và tài liệu.
