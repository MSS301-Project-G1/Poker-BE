# chat-service

Cấu trúc package và lệnh kiểm tra: [Social/Chat/Matchmaking](../SERVICE_STRUCTURE.md).

Backend MVP TEXT theo SPEC §6 và §9.3, port 8085; PostgreSQL `chat_db` riêng.

## Chạy và cấu hình

Cần `CHAT_GATEWAY_KEY` ở Gateway/Chat và `SOCIAL_INTERNAL_KEY` ở Chat/Social. Cấu hình `SOCIAL_SERVICE_URL`; Social lỗi hoặc response thiếu boolean thì từ chối tạo/gửi DIRECT (502), không fallback coi là bạn. JWT/JWKS dùng `JWT_JWK_SET_URI`, `JWT_ISSUER`, `JWT_AUDIENCE`; không cần private key Auth.

```powershell
.\mvnw.cmd -pl chat-service -am clean verify
```

Java 21 + Docker; suite **18 tests**, dùng PostgreSQL/RabbitMQ thật, JWKS test và STOMP clients. Compose dùng DB riêng và RabbitMQ chung. Swagger `/swagger-ui.html`, OpenAPI `/v3/api-docs`. Bộ local hiện có chạy bằng `.\.local\start.ps1 -Build`.

REST `/api/**` cần `X-Chat-Gateway-Key` và `X-User-Id` do Gateway gắn; thiếu key bị 403. FE gọi Gateway bằng cookie và CSRF cho POST. USER/ADMIN đều phải là member; không có API admin bypass.

## REST

| Method | Path | Body / kết quả |
|---|---|---|
| POST | `/api/chat/conversations/direct` | `{ "accountId": "uuid" }`, get-or-create cùng một phòng cho cặp bạn bè |
| GET | `/api/chat/conversations?page=0&size=20` | Danh sách phòng của actor, size 1–100 |
| GET | `/api/chat/conversations/{id}` | Metadata + memberIds, chỉ member |
| GET | `/api/chat/conversations/{id}/messages?before=&limit=20` | `{ items, nextCursor }`, mới nhất trước, limit 1–100 |
| POST | `/api/chat/conversations/{id}/messages` | REST fallback, dùng chung service với STOMP |

Conversation DTO: `{ id, type, refId, closedAt, createdAt, memberIds }`. Message DTO: `{ id, conversationId, senderId, messageType, content, clientMessageId, createdAt }`. Không trả entity JPA.

Send body `{ "messageType": "TEXT", "content": "Xin chào", "clientMessageId": "uuid-tu-client" }`. clientMessageId là field tùy chọn thêm tương thích ngược: có field thì dedup `(senderId, clientMessageId)`; reuse khác phòng/nội dung trả 409. Không có field thì mỗi retry được coi là message mới. senderId luôn từ identity. Content strip, không rỗng, tối đa `CHAT_MAX_CONTENT_LENGTH` (mặc định 2000 UTF-16 code units).

DIRECT kiểm tra friendship/block khi tạo và mỗi lần gửi. Unfriend/block không xóa lịch sử, member vẫn đọc được. Cursor opaque dựa trên `(createdAt,id)` tránh mất/trùng tin khi timestamp bằng nhau; FE không tự tạo cursor. FE render content như text, không dùng HTML thô.

## Lọc nội dung tin nhắn

`ban-word-validator` 0.1.1 được gọi trong validation chung của `MessageService.send` cho
DIRECT và MATCH, cả REST lẫn STOMP. Tin vi phạm không được lưu DB hay phát realtime.
REST trả HTTP 400 với mã `CHAT_CONTENT_NOT_ALLOWED`; STOMP trả cùng mã qua
`/user/queue/chat-errors` và giữ kết nối để người dùng sửa tin.

Danh sách Việt/Anh nằm tại `src/main/resources/moderation/banned-words.txt`; sửa danh
sách rồi build và khởi động lại Chat. Nội dung được chuẩn hóa trên bản sao (Unicode,
chữ thường, ký tự vô hình, một số dạng thay chữ bằng số, chèn dấu giữa các chữ và kéo
dài chữ). Có thêm cách đọc bỏ ký hiệu chèn vào và tách nhóm chữ viết tắt như `Đ# mm @`;
vẫn giữ khoảng trắng giữa các từ và không cấm tên quốc gia “Đan Mạch”.
Danh sách có các nhóm viết tắt dm/đm, dcm/đcm, dkm/đkm, dạng thêm m và các cụm
Việt thường gặp; nhận diện cả lặp đôi/kéo dài chữ, Unicode và ký hiệu xen giữa chữ.
So khớp theo ranh giới từ/cụm từ; nội dung hợp lệ được lưu nguyên bản sau strip.
Không bỏ dấu toàn bộ tin nhắn để tránh chặn nhầm từ như “các”, “lớn”, “buổi”, “du lịch”.
Các cụm trong danh sách có thêm biến thể không dấu và viết liền. Đây là bộ lọc từ điển,
không bảo đảm nhận ra mọi lời xúc phạm theo ngữ cảnh hoặc mọi cách viết né mới.

## STOMP

Endpoint `/ws/chat`; CONNECT có `Authorization: Bearer <JWT>` từ `/api/auth/ws-token`, giữ token trong memory. JWT kiểm tra RS256, issuer, audience, exp và UUID subject. Chỉ subscribe `/user/queue/chat` và `/user/queue/chat-errors`; SEND `/app/conversations/{id}/send` với body như REST.

Message được lưu/commit trước fan-out tới từng member. Lỗi nghiệp vụ gửi ErrorResponse `CHAT_*` về `/user/queue/chat-errors`, giữ socket. Token sai/hết hạn, destination trái phép hoặc vượt rate thì đóng socket. Cấu hình origin `CHAT_ALLOWED_ORIGINS`, frame limit `CHAT_WS_MESSAGE_SIZE` mặc định 8192, send rate `CHAT_WS_RATE` mặc định 10/s/session; heartbeat 10s, thời hạn CONNECT 10s.

Delivery WS là best effort sau DB commit; reconnect tải lịch sử rồi reconcile bằng message.id/clientMessageId. Simple broker hỗ trợ một instance; chưa có durable WS delivery/read receipt/unread/edit/delete.

## MATCH lifecycle

RabbitMQ exchange chung `poker.events`; queues `chat-service.match.started` và `chat-service.match.finished`, mỗi queue có `.dlq`, retry hữu hạn 5 lần. EventId được claim trong cùng transaction với lifecycle; transaction lỗi thì receipt rollback.

`match.started` tạo một MATCH conversation theo matchId, thêm đúng playerIds. Event trùng không tạo lại; cùng matchId khác membership bị từ chối. `match.finished` đóng phòng, sau đó chỉ đọc. Khi finished đến trước started, ghi tombstone; started tới sau tạo phòng đã đóng với đúng members, không có thời điểm mở gửi tin. Không có public API tạo/đóng MATCH.

STICKER/SOUND, Shop ownership, chat restrictions và ChatPanel FE chưa thuộc backend MVP này. Game restart hủy trận chưa phát match.cancelled theo SPEC hiện tại; cần contract bổ sung để đóng những phòng đó. Không tự coi match.finished là hủy trận vì nó còn ảnh hưởng trao thưởng.

## Kiểm chứng 17/10/2026

`mvnw.cmd -pl social-service,chat-service,api-gateway,competition-service -am verify`: **87 tests, 0 failures/errors** (Social 15, Chat 18, Gateway 18, Competition 36). PostgreSQL/RabbitMQ Testcontainers kiểm tra migration, concurrency, dedup, lifecycle, JWT, routing riêng user và DLQ.

Docker local qua Gateway 18080 đã test Auth OTP Mailpit/login bằng cookie → gửi/accept lời mời → notification Rabbit/WS → DIRECT get-or-create → STOMP chat hai user → REST retry dedup → outsider bị 403 → block chặn tin mới nhưng vẫn đọc lịch sử → logout. Không đánh dấu FE hoặc Matching producer là đã hoàn tất.
