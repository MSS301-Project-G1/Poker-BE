# Phần 8: xử lý đúng lỗi ví và âm thanh

Phần này hoàn tất ba điểm cũ đã ghi trong `WALLET-GATEWAY-REVIEW.md`: shop báo nhầm
lỗi ví thành thiếu tiền, tra giao dịch sai envelope/nuốt lỗi, và game báo mọi 4xx
của shop thành chưa sở hữu âm thanh.

## Nguyên tắc

Mã HTTP chưa đủ để xác định nghiệp vụ: 403 có thể là backend gửi sai key, 404 có
thể là sai route hoặc thực sự không có giao dịch/vật phẩm. Client cần kiểm tra
cả status và mã lỗi theo contract của upstream. Không xác minh được kết quả thì
báo lỗi dịch vụ, không suy diễn thành thiếu tiền, không sở hữu hoặc không có giao dịch.

## Shop gọi wallet

| Kết quả từ wallet | Kết quả shop |
| --- | --- |
| 400 + `WALLET_INSUFFICIENT_BALANCE` khi debit | Giữ lỗi thiếu tiền hiện có |
| Sai key, timeout, 5xx, lỗi validation/conflict, lỗi không đọc được | 503 `WALLET_SERVICE_UNAVAILABLE` |
| Credit lỗi | 503 với thông báo chung, không đưa nội dung lỗi upstream vào thông báo FE |
| GET lookup: 404 + `WALLET_TRANSACTION_NOT_FOUND` | `Optional.empty()` |
| GET lookup: thành công, envelope `data` hợp lệ, đúng idempotency key, amount âm | Trả trị tuyệt đối chính xác của khoản debit |
| GET lookup: sai key, lỗi route, lỗi mạng, payload sai/thiếu hoặc amount không phải debit hợp lệ | 503; không giả thành giao dịch không tồn tại |

Lookup đọc đúng `data`, kiểm tra `code`, idempotency key và số tiền nguyên trong
phạm vi `long`; không nhận credit/amount 0, không cắt số lẻ hoặc làm tràn số khi
đổi dấu. Query key được encode thành một giá trị duy nhất, kể cả chứa `&`, `=` hoặc `+`.
Hàm lookup hiện chưa có caller trong nghiệp vụ shop; không tự thêm luồng hoàn tiền.

Header nội bộ, body credit/debit, currency, reason, refId và idempotency key giữ
nguyên. Không thêm retry hoặc phát sinh giao dịch bù. Lỗi timeout/503 không xác nhận
rằng giao dịch chưa xảy ra; đối soát/retry phải giữ đúng idempotency của luồng hiện có.

## Game gọi shop lấy âm thanh

| Kết quả từ shop | Kết quả game |
| --- | --- |
| 404 + `SHOP_ITEM_NOT_OWNED` | 403: chưa sở hữu âm thanh |
| 400 + `SHOP_SOUND_NOT_PLAYABLE` | 400: vật phẩm không phải âm thanh có thể phát |
| Sai key, lỗi mạng, 5xx, 404 không rõ nguyên nhân hoặc lỗi không đọc được | 503: không tải được âm thanh lúc này |
| 200 nhưng thiếu/blank URL hoặc payload không hợp lệ | 503, không trả URL để phát |

Giữ mã `GAME_TAUNT_UNAVAILABLE` hiện có; phân biệt bằng status/thông báo đúng nguyên
nhân. Các quy tắc ghế chơi, cooldown, nội dung broadcast và URL thành công không đổi.

## Xác minh

- 21 test shop pass: 10 contract client mới, 1 test internal key và 10 test nghiệp vụ
  product hiện có. Có kiểm tra thành công, thiếu tiền, sai key, lỗi mạng, các lỗi
  HTTP khác, lookup không tồn tại, envelope cũ/sai, số tiền sai và query key có ký tự đặc biệt.
- 12 test game pass: 5 test contract lỗi mới, 1 test đọc âm thanh có key và 6 test
  taunt hiện có. Có kiểm tra sở hữu/không playable, sai key, lỗi mạng và payload không hợp lệ.
- Tổng 33 test pass; package shop/game và `git diff --check` thành công.
- Test dùng MockRestServiceServer hoặc HTTP fixture local, service/repository mock.
  Không gọi ví, S3, RabbitMQ, DB hoặc trận thật.

Đã kiểm tra Docker local: stack Poker đang tắt; chỉ có PostgreSQL/Redis của Stellar
chạy. Vì vậy chưa nghiệm thu được các luồng trên stack Poker thật. Agent chưa khởi
động stack, sửa dữ liệu hoặc tác động container/volume của Stellar.

## Chạy sau review

Nếu các phần security trước đã được cập nhật lên stack, từ root BE chỉ cần:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build shop-service game-service
```

Nếu chưa cập nhật các phần security trước, cập nhật đồng thời các service và caller
đã thay đổi để không gặp bản caller cũ thiếu key:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build api-gateway auth-service wallet-service shop-service matchmaking-service ranking-service competition-service game-service
```

Đây là lệnh để chạy sau review, không phải bước agent đã thực thi. Dùng `.env` hiện
có đã bổ sung các key ở những phần trước; không cần xóa volume hoặc thêm migration.
