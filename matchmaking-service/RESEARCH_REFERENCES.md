# Nghiên cứu ghép trận Normal, Party và Rank

Tài liệu này chỉ dùng để định hướng thiết kế thuật toán và cách đo lường. Quy tắc nghiệp vụ chính
vẫn được lấy từ `docs/SPEC.md` và cấu hình của dự án.

## 1. Normal matchmaking

Nguồn: [Xu, Yu và Wu — Rule Designs for Optimal Online Game Matchmaking](https://arxiv.org/abs/1911.11852)

- Người chơi đi vào hàng chờ theo thời gian và hệ thống phải cân bằng giữa thời gian chờ với việc tạo trận sớm.
- Ưu tiên người có `joinedAt` cũ để tránh một người phải chờ vô hạn.
- Khi trả người chơi về hàng chờ, phải giữ `joinedAt` ban đầu.
- Các mốc tối thiểu `2` người, tối đa `6` người và countdown `10 giây` là quy tắc của dự án, không phải kết luận của paper.

### Phần paper được áp dụng

#### Mục II-B — k-player Games

Paper mô hình hóa việc người chơi lần lượt vào hàng chờ và trận được tạo khi hàng chờ có đủ người.
Dự án chỉ áp dụng mô hình tổng quát này. Công thức thời gian chờ cho một trận có đúng `k` người
không được dùng trực tiếp vì Normal cho phép bắt đầu với số người biến thiên từ `2` đến `6`.

Code liên quan:

- [`NormalMatchWorker.find()`](src/main/java/com/msspoker/matchmakingservice/service/impl/worker/NormalMatchWorker.java): đọc số người đang chờ, kiểm tra `minPlayers`, `maxPlayers` và quyết định thời điểm tạo trận.
- [`MatchmakingProperties.Normal`](src/main/java/com/msspoker/matchmakingservice/config/MatchmakingProperties.java): cấu hình số người tối thiểu, tối đa và thời gian chờ lấp bàn.

#### Mục III-A — Central Queue or Two Queues?

Paper so sánh việc tách người chơi đơn và nhóm có sẵn thành hai queue với việc cho họ chờ chung.
Normal áp dụng một queue chung: solo và toàn bộ thành viên Party đều nằm trong queue `NORMAL`, sau đó
worker chọn các đơn vị phù hợp để lấp bàn. Cách này cho phép Party thiếu người được ghép thêm solo hoặc Party khác.

Code liên quan:

- [`RedisQueueStore`](src/main/java/com/msspoker/matchmakingservice/service/impl/RedisQueueStore.java): lưu solo và thành viên Party trong cùng Redis Sorted Set của mode `NORMAL`.
- [`NormalMatchWorker.queueUnits()`](src/main/java/com/msspoker/matchmakingservice/service/impl/worker/NormalMatchWorker.java): chuyển queue chung thành các đơn vị solo hoặc Party trước khi chọn bàn.

#### Mục III-B — Service Order for Central Queue

Paper phân tích ba thứ tự phục vụ: FIFO, Packing và LIFO. Dự án áp dụng FIFO kết hợp Packing:

- FIFO: đọc người có `joinedAt` cũ trước.
- Packing: thêm lần lượt các đơn vị solo/Party nếu tổng số người không vượt quá `maxPlayers`.
- Party là một đơn vị không thể tách; chỉ chọn Party khi tìm thấy đầy đủ thành viên của Party trong queue.

Code liên quan:

- [`RedisQueueStore.oldest()`](src/main/java/com/msspoker/matchmakingservice/service/impl/RedisQueueStore.java): đọc Redis Sorted Set từ score nhỏ đến lớn, tương ứng `joinedAt` cũ đến mới.
- [`NormalMatchWorker.selectPack()`](src/main/java/com/msspoker/matchmakingservice/service/impl/worker/NormalMatchWorker.java): lấp bàn nhưng không vượt quá `maxPlayers`.
- [`NormalMatchWorker.queueUnits()`](src/main/java/com/msspoker/matchmakingservice/service/impl/worker/NormalMatchWorker.java): giữ Party nguyên khối và không đưa Party thiếu thành viên vào trận.

#### Mục III-C — Performance Assessment and Rule Design

Paper kết luận central queue với FIFO/Packing phù hợp cho trận có số người nhỏ. Dự án dùng kết luận này
làm cơ sở cho thứ tự chọn người, nhưng không sử dụng các công thức của paper để tính countdown.

Mục II-D của paper gợi ý đo thời gian chờ trung bình và độ phân tán thời gian chờ. Đây là hướng đo lường;
telemetry riêng cho các chỉ số này chưa được triển khai trong Normal hiện tại.

### Phần paper không áp dụng

- Mục IV về chọn phe không phù hợp vì poker không chia người chơi vào hai phe trước trận.
- Mục V về nhiều vùng trình độ không dùng cho Normal và không dùng để mở rộng Elo window.
- Appendix VII, gồm các công thức kỳ vọng thời gian chờ trong hình minh họa, không được đưa vào code.
- Paper nghiên cứu chủ yếu trận cố định `k-player` và `2v2`; không định nghĩa rule `2–6 người` hoặc countdown `10 giây`.

Luồng Normal của dự án:

```text
0–1 người: tiếp tục chờ
→ đủ 2 người: bắt đầu countdown 10 giây
→ trong countdown: tiếp tục nhận thêm người
→ đủ 6 người: tạo trận ngay
→ hết countdown và có 2–5 người: tạo trận
→ tụt xuống dưới 2 người: hủy countdown
```

## 2. Party matchmaking

Nguồn: [Pal — Cinder: A Fast and Fair Matchmaking System](https://arxiv.org/abs/2602.17015)

Đây là preprint dùng để tham khảo cách xử lý lobby tạo sẵn, không áp dụng toàn bộ thuật toán của paper.

- Party được xem là một đơn vị ghép trận, không tách thành viên sang nhiều trận.
- Party có thể được ghép với người chơi đơn hoặc Party khác nếu tổng số người không vượt quá sức chứa bàn.
- Nếu sau này cho Party chơi Rank, chênh lệch trình độ của các thành viên phải được đánh giá theo phân bố kỹ năng, không chỉ dựa vào Elo trung bình.
- Normal hiện chỉ cần giữ Party nguyên khối và lấp chỗ trống; chưa cần các phép đo khoảng cách phức tạp của Cinder.

### Phần paper được áp dụng

#### Mục 1 — Introduction

Paper mô tả `lobby` là nhóm bạn đã lập sẵn trước khi vào matchmaking. Dự án áp dụng khái niệm này:
Party là một đơn vị không thể tách khi đưa vào queue và khi chọn người cho bàn Normal.

Code quản lý Party:

- [`PartyController`](src/main/java/com/msspoker/matchmakingservice/controller/PartyController.java): API tạo, mời, tham gia, kick và rời Party.
- [`PartyServiceImpl`](src/main/java/com/msspoker/matchmakingservice/service/impl/PartyServiceImpl.java): kiểm tra leader, thành viên, giới hạn số người và trạng thái đang tìm trận.
- [`RedisPartyStore`](src/main/java/com/msspoker/matchmakingservice/service/impl/RedisPartyStore.java): lưu Party, leader, mã mời và thành viên trong Redis.
- [`redis/party-create.lua`](src/main/resources/redis/party-create.lua), [`party-invite.lua`](src/main/resources/redis/party-invite.lua), [`party-join.lua`](src/main/resources/redis/party-join.lua), [`party-kick.lua`](src/main/resources/redis/party-kick.lua) và [`party-leave.lua`](src/main/resources/redis/party-leave.lua): thay đổi Party nguyên tử.

Code tích hợp Party vào Normal:

- [`NormalMatchmakingServiceImpl.join()`](src/main/java/com/msspoker/matchmakingservice/service/impl/NormalMatchmakingServiceImpl.java): chỉ leader được đưa toàn bộ Party vào Normal queue.
- [`NormalMatchmakingServiceImpl.leave()`](src/main/java/com/msspoker/matchmakingservice/service/impl/NormalMatchmakingServiceImpl.java): chỉ leader được hủy tìm trận cho toàn bộ Party.
- [`RedisQueueStore.enqueueParty()`](src/main/java/com/msspoker/matchmakingservice/service/impl/RedisQueueStore.java) và [`redis/enqueue-party.lua`](src/main/resources/redis/enqueue-party.lua): kiểm tra đúng toàn bộ thành viên rồi enqueue nguyên tử.
- [`RedisQueueStore.cancelParty()`](src/main/java/com/msspoker/matchmakingservice/service/impl/RedisQueueStore.java) và [`redis/cancel-party.lua`](src/main/resources/redis/cancel-party.lua): xóa toàn bộ Party khỏi queue nguyên tử và không cho xóa một phần khi đã reserve.
- [`NormalMatchWorker.queueUnits()`](src/main/java/com/msspoker/matchmakingservice/service/impl/worker/NormalMatchWorker.java): tạo một unit chứa toàn bộ thành viên Party; Party thiếu thành viên trong queue sẽ không được chọn.
- [`NormalMatchWorker.selectPack()`](src/main/java/com/msspoker/matchmakingservice/service/impl/worker/NormalMatchWorker.java): ghép Party với solo hoặc Party khác nếu tổng số người không vượt quá `maxPlayers`.

### Phần paper chưa áp dụng

- Mục 2 và 2.1 dùng khoảng rating và Ruzicka similarity để lọc hai lobby; Normal không đọc Elo nên không áp dụng.
- Mục 3 chia rating thành các bucket phi tuyến; dự án chưa triển khai.
- Mục 4 tính Kantorovich/Wasserstein distance và Sanction Score giữa hai lobby; dự án chưa triển khai.
- Paper nghiên cứu độ công bằng giữa các lobby cạnh tranh, không định nghĩa API create/invite/join/kick/leave, quyền leader, Redis atomicity hoặc cách lấp một bàn poker `2–6` người.
- Party hiện chỉ được chơi Normal. Nếu sau này hỗ trợ Party Rank thì phải thiết kế và duyệt rule riêng trước khi áp dụng các mục 2–4.

Vì vậy, Cinder chỉ là cơ sở cho khái niệm **pre-made Party được giữ nguyên khối**. Các rule leader,
invite code, tối đa `6` thành viên và ghép thêm người lạ là yêu cầu nghiệp vụ của dự án, không phải kết luận của paper.

Mô hình áp dụng:

```text
Solo  = một đơn vị có kích thước 1
Party = một đơn vị có kích thước bằng số thành viên
Bàn   = sức chứa tối đa 6
```

## 3. Rank matchmaking theo Elo

Nguồn: [Véron, Marin và Monnet — Matchmaking in Multi-player On-line Games](https://pages.lip6.fr/Olivier.Marin/Publis/nossdav14.pdf)

- Elo là tiêu chí chính để chọn những người chơi có trình độ gần nhau.
- Chỉ chọn người có độ chênh lệch Elo nằm trong ngưỡng cấu hình.
- Ngưỡng thử nghiệm ban đầu của dự án là `100 Elo`.
- Không tự mở rộng Elo window theo thời gian chờ.
- Elo `0` vẫn hợp lệ để tham gia Rank; Elo là tiêu chí ghép trận, không phải điều kiện mở khóa.
- Ngưỡng `100 Elo` là cấu hình thử nghiệm, cần được điều chỉnh bằng dữ liệu thực tế.

Số người của Rank không lấy từ paper. Game Match Settings là nguồn sự thật, mặc định `2–6`;
khi Admin thay đổi `minPlayers`/`maxPlayers`, worker dùng giá trị mới cho lượt ghép tiếp theo.

Snapshot dùng khi vào Rank queue:

```text
accountId, elo, winStreak, lossStreak, joinedAt
```

Thứ tự ưu tiên:

```text
Elo gần người chơi gốc
→ chuỗi thắng/thua làm tiêu chí phụ
→ joinedAt cũ hơn để phá hòa
```

Chuỗi thắng/thua không phải tiêu chí chính và không được làm người chơi chờ vô hạn.

## 4. Redis queue và reservation nguyên tử

Tài liệu kỹ thuật: [Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/) ·
[Redis Lua scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/)

Đây là tài liệu kỹ thuật triển khai, không phải paper về thuật toán matchmaking.

- Dùng Redis Sorted Set để giữ thứ tự người chơi theo `joinedAt`.
- Mỗi tài khoản chỉ có một entry trong hàng chờ.
- Dùng Lua để reserve cả nhóm trong một thao tác nguyên tử.
- Hai worker chạy đồng thời không được reserve trùng một người chơi.
- Party phải được enqueue, cancel, reserve và requeue như một đơn vị nguyên tử.
- Nếu reserve thất bại thì không tạo proposal và thử lại ở lượt sau.

## Kết luận áp dụng

### Normal

```text
Ưu tiên người chờ lâu
→ gom đủ 2–6 người
→ countdown 10 giây hoặc tạo ngay khi đủ 6
→ reserve nguyên tử
→ tạo bàn
```

### Party

```text
Xem Party là một đơn vị không thể tách
→ ghép thêm Solo hoặc Party khác nếu còn chỗ
→ reserve toàn bộ thành viên nguyên tử
→ tạo bàn
```

### Rank

```text
Lấy Elo và streak từ ranking-service
→ chọn người trong ngưỡng Elo cố định
→ dùng streak làm tiêu chí phụ
→ ưu tiên joinedAt cũ hơn
→ reserve nguyên tử
→ tạo proposal trận
```
