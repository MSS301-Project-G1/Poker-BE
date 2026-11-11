# Local startup: PostgreSQL major version và Redis AOF

## PostgreSQL shop/wallet

Log ngày 2026-11-10 xác nhận hai volume hiện có được tạo bởi PostgreSQL 16,
trong khi container mới dùng PostgreSQL 17. Server khác major version không mở
được data directory này.

Compose shop/wallet đã cho phép chọn image qua `SHOP_POSTGRES_IMAGE` và
`WALLET_POSTGRES_IMAGE`. Mặc định trong Compose và `.env.example` vẫn là
`postgres:17-alpine` cho môi trường mới. `.env` local được đặt theo dữ liệu hiện có:

```dotenv
SHOP_POSTGRES_IMAGE=postgres:16-alpine
WALLET_POSTGRES_IMAGE=postgres:16-alpine
```

Đã recreate riêng hai container DB bằng PostgreSQL 16 với cùng project
`poker-auth-dev` và cùng volume. Cả `shop-postgres` và `wallet-postgres` đã healthy.
Không xóa volume, không đổi credential hoặc kết nối sang PostgreSQL của Stellar.

Muốn đổi major version của DB có dữ liệu cần quy trình backup/migrate riêng;
đổi image đơn thuần không chuyển định dạng dữ liệu.

## Redis matchmaking

Trước phục hồi, container dừng vì `appendonly.aof.1.incr.aof` sai định dạng. Kiểm tra trên bản
sao cho thấy file dài 4,062,345 byte; prefix hợp lệ tới byte 4,062,144, còn 201 byte lỗi.
RDB base có checksum hợp lệ.

Đã từng sao lưu nguyên bản tại đường dẫn dưới đây; bản backup này đã được xóa
theo yêu cầu người dùng, không còn dùng để phục hồi:

```text
.local/recovery/matchmaking-redis-before-repair-20261110/
```

Bản làm việc riêng tại:

```text
.local/recovery/matchmaking-redis-repair-copy-20261110/
```

Đã chạy `redis-check-aof --fix` trên bản làm việc để kiểm chứng. Sau khi người dùng
xác nhận áp dụng phục hồi, đã kiểm tra Redis thật vẫn dừng, file vẫn dài 4,062,345
byte và prefix khớp bản đã kiểm tra. Sau đó truncate đúng 201 byte cuối của AOF thật,
giữ nguyên các file khác và kiểm tra lại: toàn bộ AOF/manifest hợp lệ.

Redis đã khởi động healthy, log xác nhận nạp AOF thành công và sẵn sàng nhận kết nối.
Có thể mất phần trạng thái queue thuộc 201 byte cuối bị loại bỏ. Backup nguyên bản
đã xóa theo yêu cầu trước đó; bản làm việc đã sửa vẫn còn ở đường dẫn trên.

Lỗi AOF là nguyên nhân Redis thoát trong lần này; warning `vm.overcommit_memory`
không phải lỗi định dạng AOF và chưa thay sysctl của máy.

## Xác minh sau khi bật lại stack

Đã chạy `docker compose -p poker-auth-dev --env-file .env up -d --no-build` thành công.
Không dùng `--remove-orphans` và không thay đổi container/volume của Stellar.

- Health của gateway, auth, game, matchmaking, social, chat, ranking, competition,
  shop và wallet: HTTP 200, `UP`; cả 10 service đăng ký `UP` trong Eureka.
- Qua gateway: GET catalog shop, leaderboard và danh sách phòng trả HTTP 200.
- Chưa đăng nhập đọc ví qua gateway: 401 `GATEWAY_UNAUTHENTICATED`.
- Gọi thẳng wallet/shop/matchmaking/ranking/competition với header danh tính giả:
  HTTP 403 trước xử lý nghiệp vụ.
- GET nội bộ wallet/shop/ranking thiếu key: 403. Key đúng: shop/ranking trả 200;
  wallet tra một key ngẫu nhiên không tồn tại trả đúng 404 `WALLET_TRANSACTION_NOT_FOUND`.

Các probe do agent gửi đều là GET; không gửi giao dịch cộng/trừ ví, tham gia/hủy
trận hoặc thao tác admin. Đây là xác minh khởi động và các đường đọc, chưa phải
nghiệm thu đầy đủ login/refresh/FE/STOMP và các giao dịch nghiệp vụ có ghi dữ liệu.
