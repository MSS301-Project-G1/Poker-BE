# Payment: đối soát và giao thưởng sau thanh toán

## Hai trạng thái cần phân biệt

PayOS xác nhận nhận tiền và wallet xác nhận cộng xu là hai bước riêng. Không thể
coi việc ghi `PAID` hoặc gửi message vào RabbitMQ là bằng chứng ví đã cộng xu.

Theo [API chính thức của PayOS](https://payos.vn/docs/api/), tra cứu/hủy payment
link trả về trạng thái cùng thông tin đơn và số tiền. Backend kiểm tra kết quả
này; không dùng redirect success/cancel của FE để xác nhận thanh toán.

Response đơn hàng giữ `status` hiện có và thêm:

| Field | Ý nghĩa |
| --- | --- |
| `fulfillmentStatus=PENDING` | Chưa xác nhận giao thưởng. Chỉ xét việc retry giao thưởng khi `status=PAID`. |
| `fulfillmentStatus=FULFILLED` | Ví đã phản hồi cộng xu thành công, hoặc inventory đã được cấp trong transaction của shop. |
| `fulfillmentStatus=REVIEW_REQUIRED` | Cần đối soát thủ công, không tự động cộng lại. |
| `fulfilledAt` | Thời điểm xác nhận giao thưởng xong; chưa xong thì `null`. |

## Lỗi cũ và cách sửa

1. Trước đây, cả HTTP gọi ví và publish RabbitMQ đều có thể lỗi nhưng đơn vẫn
   `PAID`; worker chỉ quét `PENDING`, nên đơn này không được xử lý tiếp.
   Nay đơn `PAID + fulfillmentStatus=PENDING` là công việc còn tồn trong DB.
   Scheduler hiện có sẽ quét lại, kể cả sau khi shop restart.
2. Trước đây, lỗi tra PayOS có thể khiến đơn quá hạn bị đánh `EXPIRED`, ngừng đối
   soát. Nay lỗi kết nối giữ `PENDING`; chỉ đóng khi PayOS xác nhận hủy/hết hạn.
   Khi đơn quá hạn nhưng PayOS vẫn `PENDING`, shop yêu cầu hủy và chờ xác nhận.
   Nếu PayOS trả `PAID` trong lúc hủy, shop xử lý thanh toán thay vì ghi hủy.

Luồng gói nạp:

1. Webhook đã xác minh chữ ký, hoặc API tra cứu xác nhận đúng mã đơn/số tiền,
   chuyển đơn sang `PAID`.
2. Shop gọi wallet với snapshot số xu lúc mua và key `ORDER:<orderId>`.
3. HTTP thành công: đánh `FULFILLED`. HTTP lỗi/timeout: giữ `PENDING` để retry.
   RabbitMQ vẫn hỗ trợ giao sự kiện, nhưng publish thành công chưa đủ để đánh
   `FULFILLED`. Nếu message đã cộng tiền trước, HTTP retry cùng key sẽ được ví
   xử lý idempotent và xác nhận lại.
4. Webhook lặp, worker và thao tác sync/detail đều khóa cùng dòng order. Đơn đã
   `FULFILLED` không được giao thưởng lại. Nếu wallet đã commit nhưng shop
   rollback, lần retry vẫn dùng key cũ.

Đơn vật phẩm ghi inventory và `FULFILLED` trong cùng transaction shop. Gói nạp
thiếu snapshot số xu hoặc đơn thiếu đối tượng mua sẽ chuyển `REVIEW_REQUIRED`.
Không lấy giá trị gói hiện tại để suy ra số xu của giao dịch cũ.

Scheduler giữ chu kỳ hiện có: mặc định bắt đầu sau 30 giây và quét mỗi 60 giây
sau khi lượt trước kết thúc. Ví lỗi kéo dài thì đơn vẫn chờ, không tự bỏ qua.

## FE cần đọc gì?

Các URL và envelope hiện có giữ nguyên. Trong dữ liệu đơn hàng:

- `status=PENDING`: chờ xác nhận tiền. Nếu `expiresAt` đã qua, hiển thị đang đối
  soát, không tiếp tục cho thanh toán chỉ vì backend còn `PENDING`.
- `status=PAID` và `fulfillmentStatus=PENDING`: hiển thị “Đã thanh toán, đang xử lý
  cộng xu/giao vật phẩm”; tiếp tục đọc `GET /api/payments/orders/{orderId}` hoặc
  gọi `POST /api/payments/orders/{orderId}/sync`.
- `status=PAID` và `fulfillmentStatus=FULFILLED`: hoàn tất; tải lại số dư/inventory.
- `status=PAID` và `fulfillmentStatus=REVIEW_REQUIRED`: thông báo cần hỗ trợ đối
  soát, không yêu cầu khách thanh toán lại.

`GET` detail có đối soát và có thể retry giao thưởng. Lịch sử
`GET /api/payments/orders/me` trả trạng thái đã lưu. `/orders/pending` chỉ dành
cho đơn chờ xác nhận tiền, không trả đơn đã `PAID` còn chờ giao thưởng; FE giữ
`orderId` để theo dõi đơn đó qua detail/sync.

Response từ yêu cầu hủy cũng phải đọc `status`: có thể là `PAID` nếu nhận tiền
cùng lúc hủy. Khi chưa xác nhận được hủy, API trả lỗi dịch vụ và DB không tự
đổi sang `CANCELLED`.

Chưa sửa FE trong đợt này; cần cập nhật cách hiển thị trên trước nghiệm thu UI.

## Migration và cấu hình

Migration mới `V22__track_order_fulfillment.sql` thêm hai cột và index cho worker.
Các đơn `PAID` đã tồn tại được đặt `REVIEW_REQUIRED`: dữ liệu cũ không chứng minh
được đã cộng tiền/giao vật phẩm. Đối chiếu ledger wallet bằng `ORDER:<orderId>`
hoặc inventory trước khi quyết định trạng thái; chưa thêm API/UI đối soát admin.
Đơn mới và đơn cũ chưa `PAID` bắt đầu với fulfillment `PENDING`.

Không sửa migration cũ, không đổi version PostgreSQL, không xóa volume. Agent
chỉ chạy migration trên PostgreSQL riêng của test, chưa áp lên DB local/VPS.

Không có secret bắt buộc mới. Có thêm tùy chọn trong env examples:

```dotenv
SHOP_WALLET_TIMEOUT=3s
```

Đây là timeout connect/read cho HTTP từ shop tới wallet; bỏ biến vẫn mặc định
3 giây. Các key nội bộ và cấu hình PayOS từ các phần trước tiếp tục dùng.

## Đã kiểm tra và cách chạy sau review

- 71 test pass: nghiệp vụ đơn hàng, retry, scheduler, controller/webhook,
  idempotency cache, wallet HTTP contract và security shop.
- Trong đó 5 test dùng PostgreSQL 16 tạm qua Testcontainers: lưu đơn còn chờ giao
  thưởng, webhook/worker đồng thời, wallet đã nhận nhưng transaction shop
  rollback, PayOS timeout rồi xác nhận lại, và nâng schema từ V21 lên V22 với
  đơn `PAID` cũ. Wallet, PayOS và publisher là mock trong các test này.
- Chưa gọi PayOS thanh toán thật, chưa thử giao nhận RabbitMQ thật, chưa nghiệm
  thu FE hay triển khai bản sửa lên stack đang chạy.

Sau review, từ root BE có thể cập nhật riêng shop:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build shop-service
```

Lần shop mới khởi động sẽ áp V22 lên DB đang cấu hình. Với VPS, build/publish và
triển khai image shop mới theo quy trình hiện có; migration nằm trong image.
Không cần `down -v`. Lệnh trên chưa được agent thực thi.
