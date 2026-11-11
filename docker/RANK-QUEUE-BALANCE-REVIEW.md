# Task 1: kiểm tra COIN trước khi vào hàng chờ Rank

## Thay đổi

Trước đây người thiếu COIN vẫn vào hàng chờ Rank, đến khi đủ người chấp nhận mới
bị từ chối khi trừ phí. Nay `RankMatchmakingServiceImpl.join()` đọc số dư COIN từ
wallet sau khi kiểm tra trạng thái/hạn chế tài khoản và trước khi đọc Elo/enqueue.

- GET `/internal/wallet/{accountId}/balance`, gửi `X-Wallet-Internal-Key` hiện có.
- Chỉ dùng `data.coinBalance`, kiểm tra `code=WALLET_SUCCESS`, đúng account,
  số nguyên không âm trong phạm vi long. Không dùng VIP Coin hoặc đoán số dư
  bằng 0 nếu response thiếu/sai.
- So sánh với cùng cấu hình `matchmaking.rank.entry-fee` mà luồng tạo proposal
  đang dùng; mặc định 100 COIN. Vừa đủ phí được tham gia.
- Nếu phí được cấu hình bằng 0, không cần đọc ví.
- Việc join chỉ đọc số dư, chưa giữ hoặc trừ tiền. Trừ phí khi đủ người chấp
  nhận vẫn là bước quyết định, vì người chơi có thể dùng xu trong lúc chờ.
- Normal/Custom vẫn miễn phí theo SPEC; không thêm điều kiện số dư vào hai mode này.

## Contract cho FE

Giữ nguyên `POST /api/matchmaking/rank/join`:

| Kết quả | HTTP / code | Hành vi |
| --- | --- | --- |
| Đủ COIN | 201, response queue hiện có | Đưa vào hàng chờ |
| Thiếu COIN | 402 `MM_INSUFFICIENT_BALANCE` | Chưa enqueue, thông báo số COIN tối thiểu |
| Wallet lỗi mạng/sai key/HTTP lỗi hoặc response không hợp lệ | 502 `MM_DEPENDENCY_UNAVAILABLE` | Chưa enqueue, đề nghị kiểm tra lại |

FE `useMatchmaking` đã hiển thị `failure.message` khi join thất bại nên task này
không cần sửa FE. Không thay controller response style hoặc error envelope.

## Cấu hình và chạy sau review

Không thêm env, migration hoặc dependency. `MATCHMAKING_WALLET_STUB_ENABLED=false`
phải được dùng khi kiểm tra ví thật; stub bật thì chủ động bỏ qua giới hạn số dư
như các thao tác debit/refund giả lập hiện có. `.env` local hiện đặt false.

Sau review, cập nhật riêng matchmaking từ root BE:

```bash
docker compose -p poker-auth-dev --env-file .env -f docker-compose.yml \
  up -d --build matchmaking-service
```

Agent chưa chạy lệnh trên hoặc thao tác số dư/queue người dùng thật.

## Xác minh

47 test pass, gồm HTTP wallet contract, điều kiện join Rank, controller/error
contract, trừ/hoàn phí khi chấp nhận trận và Normal hiện có. Kiểm tra COIN bằng
0/thiếu/vừa đủ, phí tùy cấu hình và phí 0; không nhầm VIP Coin, dữ liệu thiếu,
sai account, số âm/lẻ/tràn long hoặc lỗi upstream thành số dư hợp lệ.
Test dùng service/repository mock và HTTP fixture local; không truy cập DB,
Redis hay ví thật.

Task 2 (avatar) và task 3 (bỏ Reference trên FE) tách riêng, chưa sửa trong phần này.
