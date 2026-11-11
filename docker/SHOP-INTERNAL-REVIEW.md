# Phần 2: bảo vệ HTTP nội bộ shop

Phần này tiếp nối commit wallet internal, chỉ xử lý xác thực người gọi `/internal/**` của shop.
Chưa xử lý việc tin header danh tính ở `/api/**` của shop/các service khác.

## Thay đổi để review

- Shop đăng ký servlet filter cho `/internal` và `/internal/*`, chạy trước controller/nghiệp vụ.
- Header `X-Shop-Internal-Key` phải khớp biến BE `SHOP_INTERNAL_KEY`.
- Thiếu/sai/trùng key hoặc cấu hình rỗng trả 403 `SHOP_INTERNAL_FORBIDDEN`.
  Header tự khai `X-User-Id`/`X-User-Role: ADMIN` không thay thế được key.
- Bảo vệ đủ 3 endpoint hiện tại: GET `/internal/inventory/{accountId}/owns/{productId}`,
  GET `/internal/inventory/{accountId}/sounds/{productId}`, POST `/internal/inventory/grant`.
- Game gửi key khi lấy âm thanh; ranking/competition gửi key khi trao vật phẩm thưởng.
  Hợp đồng body/envelope, idempotency và các cờ mock giữ nguyên.
- Gateway loại `X-Shop-Internal-Key` từ request do client gửi, giống key nội bộ wallet.
- Swagger shop bổ sung Authorize → `shopInternalKey` cho nhóm API inventory nội bộ.
- Key riêng với wallet; không đưa key vào FE/query/log. Chưa phân quyền mỗi caller theo loại vật phẩm hoặc lý do trao thưởng.

Không thay nghiệp vụ mua hàng, PayOS, database, S3 hay FE. Filter chỉ áp dụng `/internal/**`;
API xem shop public và webhook vẫn theo cấu hình/quy tắc xác thực hiện có.

## Cấu hình và chạy sau review

`.env.example` thêm `SHOP_INTERNAL_KEY=`. `.env` local đã được bổ sung secret ngẫu nhiên nếu chưa có biến.
Shop, game, ranking, competition phải dùng cùng giá trị. Compose yêu cầu biến không rỗng bằng `${SHOP_INTERNAL_KEY:?...}`.
Java trên host cần biến này trong environment của tiến trình/IDE.

Không cập nhật riêng shop mà để caller chạy bản cũ vì chúng chưa gửi key và sẽ nhận 403.
Từ root BE, sau review có thể chạy:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build shop-service game-service ranking-service competition-service api-gateway
```

Không cần xóa volume hoặc migrate DB. Restart game khi không còn ván cần giữ vì bàn hiện ở RAM.
Agent chưa rebuild/restart stack đang dùng và chưa trao vật phẩm trên dữ liệu thật.

## Xác minh

Test filter chặn đọc/ghi với key thiếu/sai/trùng/rỗng, kể cả giả header ADMIN;
key đúng gọi được controller cho cả ba endpoint. Có test xác minh URL public nằm ngoài phạm vi filter
bằng endpoint giả lập; đây không phải test giao dịch PayOS thật.
Test HTTP fixture xác minh game/ranking/competition gửi đúng key; có hồi quy controller inventory và taunt.
Build/package các module bị ảnh hưởng và kiểm tra Compose riêng; chưa nghiệm thu trao vật phẩm/âm thanh trên stack thật.

Dừng ở phần shop internal để review/commit. Phần tiếp theo mới là xác minh gateway cho các `/api/**`.
