# Phần 5: xác minh gateway trước khi xử lý matchmaking

Phạm vi lần này là HTTP ingress của matchmaking. Dừng để review/commit trước khi
làm ranking và competition. Không đổi controller, thuật toán ghép trận, trạng thái
Redis, deadline, phí ví, tạo bàn, reconnect, RabbitMQ hoặc FE.

## Luồng request

1. FE tiếp tục gọi gateway port 8080 bằng cookie và `X-Tab-Id` như hiện tại.
   Request ghi dữ liệu tiếp tục cần CSRF cookie/header theo luồng chung.
2. Gateway kiểm tra JWT/phiên, bỏ các header danh tính và key do client tự gửi,
   rồi gắn `X-User-Id`/`X-User-Role` từ danh tính đã xác thực.
3. Chỉ route có ID `matchmaking-service` được gắn `X-Matchmaking-Gateway-Key`.
   Nếu gateway thiếu cấu hình key, trả 503 `GATEWAY_UPSTREAM_UNAVAILABLE` trước khi chuyển tiếp.
4. Matchmaking kiểm tra key trước controller. Thiếu/sai/trùng key hoặc cấu hình
   rỗng trả 403 `MM_FORBIDDEN`; giả header ID/ADMIN không thay thế được key.
5. Đúng key mới tới controller/service hiện có. Key không thay thế account ID,
   quyền chủ party, thành viên proposal, điều kiện tham gia hoặc các kiểm tra nghiệp vụ.

Filter đăng ký theo servlet URL `/api` và `/api/*`, bao phủ cả bốn controller:

| Nhóm API | Đường dẫn |
| --- | --- |
| Normal: join/leave/status/reconnect | `/api/matchmaking/normal/**` |
| Rank: join/leave/status/reconnect | `/api/matchmaking/rank/**` |
| Proposal Normal/Rank: accept/decline | `/api/matchmaking/proposals/**` |
| Party: me/create/join/kick/leave | `/api/party/**` |

Docs và health ngoài `/api` không thuộc filter này. Gọi API trực tiếp bằng Swagger
của matchmaking cũng cần gateway key; FE dùng gateway và không được giữ secret.
Các HTTP client từ matchmaking sang game/wallet/ranking không đổi trong phần này.

## Cấu hình và chạy local sau review

`.env.example` thêm `MATCHMAKING_GATEWAY_KEY=`. `.env` local đã được bổ sung secret
ngẫu nhiên; gateway và matchmaking nhận cùng giá trị qua Compose. Dùng giá trị khác
các key gateway/internal của wallet và shop; không đưa key vào FE, query hoặc log.
Khi chạy Java trên host, đặt biến trong environment của tiến trình/IDE.

Từ root BE, sau review:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build api-gateway matchmaking-service
```

Cập nhật cả gateway và matchmaking cùng lúc. Không cần xóa DB/volume. Agent chưa
restart/rebuild container đang dùng và chưa tác động queue/party/trận hoặc ví thật.

Kiểm tra thủ công sau khi service sẵn sàng, với port mặc định:

- Gọi thẳng `GET http://localhost:8083/api/matchmaking/normal/status`, tự gắn
  `X-User-Id` và `X-User-Role: ADMIN`, không có key: nhận 403 `MM_FORBIDDEN`.
- Chưa đăng nhập, gọi cùng path qua `http://localhost:8080`: nhận 401 từ gateway.
- Đăng nhập bằng FE, đọc status/reconnect/party qua gateway như cũ. Join/leave và
  accept/decline tiếp tục cần CSRF; chỉ thử trên tài khoản và trận test phù hợp.

## Xác minh đã chạy

- 35 test gateway pass: 32 integration test dùng HTTP fixture, cộng 3 test thiếu
  key gateway của wallet/shop/matchmaking. Kiểm tra key giả bị thay, key không chuyển
  sang route khác; danh tính, đường dẫn, method, body và CSRF được giữ đúng.
- 26 test matchmaking pass: 5 test filter mới, 21 test hồi quy bốn controller.
  Chặn toàn bộ 15 endpoint khi thiếu/sai key; key rỗng/trùng và key dịch vụ khác
  không được chấp nhận. Key đúng giữ account ID, mã HTTP, body party và cả hai nhánh proposal.
- Tổng 61 test pass; package gateway/matchmaking, Compose config với `.env` và
  `git diff --check` thành công.

Test controller dùng MockMvc và service mock; không kết nối PostgreSQL, Redis,
RabbitMQ, game hoặc wallet thật. Chưa nghiệm thu trình duyệt hoặc stack Docker thật.
Fixture matchmaking trong test gateway được đổi từ địa chỉ chết sang HTTP server
để kiểm tra chuyển tiếp thành công; test upstream mất kết nối dùng route fixture riêng.

Phần còn lại: ranking gateway + internal ranking + DevIdentityFilter, sau đó
competition gateway + DevIdentityFilter. Những lỗi xử lý response ví/âm thanh cũ
đã ghi trong `WALLET-GATEWAY-REVIEW.md` đã được xử lý sau đó ở [phần 8](UPSTREAM-ERRORS-REVIEW.md).
