# social-service

Backend MVP theo SPEC §6 và §9.3, port 8084. Sở hữu PostgreSQL `social_db`; không đọc DB khác hoặc lưu name/avatar.

## Chạy và bảo vệ ingress

Cấu hình `SOCIAL_GATEWAY_KEY` giống nhau ở Gateway/Social và `SOCIAL_INTERNAL_KEY` giống nhau ở Social/Chat/Competition. Dùng giá trị ngẫu nhiên riêng, không commit secret. Key rỗng từ chối request; không có fallback mock production.

HTTP FE đi qua Gateway với access cookie; request ghi dữ liệu cần CSRF. Gateway bỏ các header giả rồi gắn `X-User-Id` và `X-Social-Gateway-Key`. Gọi thẳng `/api/**` không có key bị 403. Internal cần `X-Social-Internal-Key`, không được route công khai.

```powershell
.\mvnw.cmd -pl social-service -am clean verify
```

Test cần Java 21 và Docker; tự tạo PostgreSQL 17/RabbitMQ tạm, không dùng dữ liệu local. Suite Social: **15 tests**. Swagger local `/swagger-ui.html`, OpenAPI `/v3/api-docs`. Compose của module dùng RabbitMQ chung từ `docker/compose.infra.yml` và PostgreSQL riêng. Với bộ local đã setup: `.\.local\start.ps1 -Build`.

## REST MVP

Tất cả API public cần login USER/ADMIN; ADMIN không bỏ qua quyền nghiệp vụ. Actor lấy từ `X-User-Id`, không từ body.

| Method | Path | Body / quyền |
|---|---|---|
| GET | `/api/friends?page=0&size=20` | Bạn của actor |
| GET | `/api/friends/requests?direction=INCOMING&page=0&size=20` | INCOMING hoặc OUTGOING, gồm trạng thái terminal để audit |
| POST | `/api/friends/requests` | `{ "addresseeId": "uuid" }` |
| POST | `/api/friends/requests/{id}/accept` | Chỉ addressee |
| POST | `/api/friends/requests/{id}/decline` | Chỉ addressee |
| DELETE | `/api/friends/requests/{id}` | Chỉ requester cancel |
| DELETE | `/api/friends/{id}` | Gỡ friendship hai chiều |
| GET | `/api/blocks?page=0&size=20` | Block list của actor |
| POST | `/api/blocks/{id}` | Block, hủy pending và gỡ friendship nguyên tử |
| DELETE | `/api/blocks/{id}` | Chỉ gỡ block do actor tạo |

List trả `{ items, page, size, totalElements, totalPages }`, size 1–100. Request trả `{ id, requesterId, addresseeId, status, createdAt, updatedAt }`. Friend/block list chỉ chứa UUID. Retry cùng pending request không tạo trùng; gửi ngược chiều sẽ accept request hiện có. Unblock không khôi phục tình bạn.

Internal: GET `/internal/friends/{accountId}` trả mảng UUID; GET `/internal/friends/{a}/with/{b}` trả chính xác `{ isFriend, isBlocked }`. Block tính cả hai chiều. Khóa DB theo cặp + unique constraint bảo vệ các thao tác concurrent.

## Notification realtime

`/ws/social`: STOMP CONNECT gửi `Authorization: Bearer <JWT>` lấy từ `/api/auth/ws-token`. Service kiểm tra RS256/JWKS, issuer, audience, exp và UUID subject; đóng socket khi token hết hạn. Chỉ SUBSCRIBE `/user/queue/notifications`; không có client SEND. Origin cấu hình bằng `SOCIAL_ALLOWED_ORIGINS`.

Queue durable `social-service.notification.requested`, routing key `notification.requested`, exchange chung `poker.events`. Validate payload/envelope, dedup eventId trong transaction; sau commit đẩy `{ eventId, type, title, body, data, occurredAt }` đúng accountIds. Retry tối đa 5 lần, backoff; lỗi vào `social-service.notification.requested.dlq`. FRIEND_REQUEST/FRIEND_ACCEPTED ghi outbox cùng transaction quan hệ, publish persistent và chỉ đánh dấu published sau confirm + không bị returned; lỗi retry backoff tối đa 300s cộng jitter.

MVP realtime là best effort sau commit: người offline có thể không nhận lại; chưa có notification history/read API. Simple broker chỉ hỗ trợ một instance. Redis/presence và history thuộc GĐ2. Chưa có API xác minh target account tồn tại từ Auth; không tự đọc DB Auth. Matching hiện còn thiếu producer MATCH_FOUND/MATCH_READY; Social đã sẵn sàng nhận các event đúng contract.

## Kiểm chứng 17/10/2026

`mvnw.cmd -pl social-service,chat-service,api-gateway,competition-service -am verify`: **87 tests, 0 failures/errors** (Social 15, Chat 18, Gateway 18, Competition 36). PostgreSQL/RabbitMQ Testcontainers kiểm tra migration, concurrency, dedup, lifecycle, JWT, routing riêng user và DLQ.

Docker local qua Gateway 18080 đã test Auth OTP Mailpit/login bằng cookie → gửi/accept lời mời → notification Rabbit/WS → DIRECT get-or-create → STOMP chat hai user → REST retry dedup → outsider bị 403 → block chặn tin mới nhưng vẫn đọc lịch sử → logout. Không đánh dấu FE hoặc Matching producer là đã hoàn tất.

## Phạm vi tích hợp trên develop

Code Chat/Social được lấy từ bản đã tích hợp trên testing. Thay đổi trên develop chỉ nằm trong hai module; giữ cấu hình Eureka của ba service phụ trách. Gateway và các service của thành viên khác không thay đổi.

Các kiểm chứng Docker/Gateway ghi ở trên thuộc bản tích hợp trước đó, không xác nhận Gateway hiện tại trên develop. Chủ Gateway cần tích hợp route `/api/friends/**`, `/api/blocks/**`, `/api/chat/**` giữ nguyên path; gắn đúng ingress key sau xác thực và chặn header giả; chuyển tiếp WebSocket `/ws/social`, `/ws/chat`. Cấu hình cùng `SOCIAL_GATEWAY_KEY`, `CHAT_GATEWAY_KEY` giữa Gateway và service, cùng `SOCIAL_INTERNAL_KEY` giữa Social và Chat. Key rỗng sẽ từ chối truy cập.

Swagger trực tiếp: `http://localhost:8084/swagger-ui.html` và `http://localhost:8085/swagger-ui.html`. PostgreSQL host mặc định: Social 15436, Chat 15437.
