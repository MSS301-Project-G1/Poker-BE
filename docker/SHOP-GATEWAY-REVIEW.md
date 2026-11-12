# Phần 4: xác minh gateway trước khi nhận danh tính tại shop

Đã rà lại commit `bd8aad6` (wallet gateway): filter bảo vệ `/api/**`, key nội bộ
và key gateway độc lập; gateway bỏ key giả và chỉ gắn key cho đúng route wallet.
Chưa thấy lỗi mới trong phạm vi commit đó; test hồi quy wallet tiếp tục pass.
Các điểm xử lý lỗi cũ của shop/game đã ghi ở `WALLET-GATEWAY-REVIEW.md` vẫn để riêng.

Phần này chỉ xử lý shop và cấu hình chuyển tiếp tại gateway. Dừng để review/commit
trước khi làm tương tự cho matchmaking, ranking hoặc competition.

## Luồng request

1. FE gọi gateway ở port 8080 theo URL hiện tại, không giữ key backend.
2. Gateway bỏ `X-User-Id`, `X-User-Role`, `X-Shop-Gateway-Key` và
   `X-Shop-Internal-Key` do client tự gửi. Danh tính được lấy từ JWT/phiên đã xác thực.
3. Riêng route `shop-service`, gateway gắn `X-Shop-Gateway-Key` từ cấu hình.
   Thiếu key ở gateway trả 503 `GATEWAY_UPSTREAM_UNAVAILABLE`, không chuyển tiếp.
4. Shop kiểm tra key cho `/api` và `/api/*` trước Spring Security/controller.
   Thiếu/sai/trùng key hoặc cấu hình rỗng trả 403 `SHOP_FORBIDDEN`.
5. Đúng key mới tới bộ đọc danh tính và các kiểm tra quyền USER/ADMIN hiện có.
   Key gateway không tự cấp quyền ADMIN và không thay việc đăng nhập.

Gọi thẳng port shop kèm ID/role giả không còn truy cập được API quản trị hoặc mua
vật phẩm. API admin, inventory, payment và sandbox nằm trong cùng phạm vi `/api/**`.
Không đổi body/response của controller hoặc quy tắc mua hàng, cấp vật phẩm, thanh toán.

## Shop public và callback PayOS

Shop vẫn public theo contract của owner: khách chưa đăng nhập có thể xem catalog
qua gateway. Gateway vẫn gắn key của nó cho request khách, shop nhận request mà
không có danh tính user. Cookie hợp lệ tiếp tục cung cấp danh tính để đọc metadata
sở hữu. FE không cần thêm header key.

Public cho khách không có nghĩa là client tự khai ID/role rồi gọi thẳng port shop.
Các API `/api/**` gọi trực tiếp, kể cả catalog, cần gateway key, với đúng một ngoại lệ:

- **POST chính xác `/api/payments/payos/webhook`** không cần key gateway, cho phép
  PayOS gọi callback trực tiếp hoặc qua gateway. Không nhận danh tính từ header user.
- Payload tiếp tục đi qua `OrderService.handlePayosWebhook`, nơi gọi
  `payOS.webhooks().verify(...)`. Sai chữ ký vẫn bị từ chối.
- Không miễn kiểm tra key cho method khác, URL có dấu `/` cuối hoặc các URL payment khác.

Không đổi URL webhook hiện tại hoặc logic xác minh PayOS. Docs/health ngoài `/api`
không thuộc filter gateway mới. `/internal/**` tiếp tục dùng `SHOP_INTERNAL_KEY`.

## Cấu hình và chạy local sau review

`.env.example` thêm `SHOP_GATEWAY_KEY=`; `.env` local đã được bổ sung secret ngẫu nhiên.
Gateway và shop phải dùng cùng giá trị, khác `SHOP_INTERNAL_KEY` và các key của wallet.
Compose yêu cầu biến không rỗng. Khi chạy Java trong IDE, đặt biến trong environment
của tiến trình. Không đưa key vào FE, query URL hoặc log.

Từ root BE, sau review:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build api-gateway shop-service
```

Cập nhật cả gateway và shop cùng lúc. Không cần xóa DB/volume. Các caller nội bộ
game/ranking/competition vẫn cần bản đã gửi `SHOP_INTERNAL_KEY` từ phần 2.

Kiểm tra thủ công sau khi service sẵn sàng (các port dưới đây là mặc định):

- Gọi thẳng `GET http://localhost:8088/api/admin/payments/methods` kèm ID bất kỳ và
  `X-User-Role: ADMIN`, không có key: phải nhận 403 `SHOP_FORBIDDEN`.
- Chưa đăng nhập, gọi `GET http://localhost:8080/api/shop/products`: vẫn xem được shop.
- Đăng nhập USER qua FE: mua hàng/đọc inventory theo luồng cũ; API ADMIN vẫn bị từ chối.
  Chỉ thử giao dịch trên tài khoản/dữ liệu test phù hợp.

Agent chưa rebuild/restart container đang dùng hoặc thực hiện giao dịch thật.

## Xác minh đã chạy

- 34 test gateway pass: gồm bộ integration dùng HTTP fixture và cấu hình key thiếu
  ở wallet/shop; kiểm tra key giả bị thay, key không chuyển sang route khác,
  request public và quyền/CSRF hiện tại.
- 16 test shop pass: 7 test gateway ingress với controller và Spring Security thật
  trong MockMvc, 4 test bộ đọc danh tính, 4 test internal filter và 1 test service
  từ chối chữ ký webhook sai.
- 4 test wallet gateway hồi quy pass. Tổng 54 test pass.
- Package gateway/shop/wallet thành công; kiểm tra Compose với `.env` và
  `git diff --check` thành công.

Test shop dùng service mock và context MVC riêng, không kết nối DB/Redis/RabbitMQ
hoặc PayOS thật. Chưa nghiệm thu trình duyệt, callback PayOS thật hoặc stack Docker
đang chạy. Các hạn chế xử lý lỗi tài chính cũ không được coi là đã sửa trong phần này.
