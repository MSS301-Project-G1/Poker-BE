# ADMIN: danh sách user và ban/unban

Cập nhật 04/11/2026. BE ở nhánh `fix/protect_login`; FE ở `feat/ban-user`.

## Chạy và kiểm tra

Từ root BE, dùng `.env` và project Compose hiện tại:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build auth-service api-gateway game-service social-service chat-service
```

Discovery và các service nghiệp vụ khác chạy như trước. Lệnh trên rebuild social/chat vì chúng dùng
`SessionAccessClient` từ common để nhận biết lỗi ban khi kiểm tra socket. Không cần biến môi trường mới.
Auth chạy migration **V11** để bổ sung metadata và lịch sử ban; không sửa V8 đã có dữ liệu.
Không cần `down -v`. Restart game lúc không còn ván cần giữ vì bàn hiện nằm trong bộ nhớ.

FE: chạy `npm run dev` trong repo FE, dùng nhánh `feat/ban-user`.
Login ADMIN → trang **Quản trị hệ thống** tại `http://localhost:5173/admin` → chọn **Người dùng**,
hoặc mở `http://localhost:5173/admin/users`.
Màn này có tìm kiếm, lọc đang khóa/không bị khóa, phân trang và lịch sử theo tài khoản.

Khu quản trị có layout/điều hướng và nút đăng xuất riêng. ADMIN login không bị URL sảnh đã lưu
đưa về lobby; chỉ giữ URL sau login nếu nó thuộc `/admin`. ADMIN không cần gọi game, ví hay
khởi động socket/queue người chơi để vào trang quản trị. Các module chỉ gọi API khi mở màn tương ứng.

USER hoặc khách mở `/admin` hay `/admin/**` thấy trang 404 trên FE, không mount màn quản trị
và không gửi API quản trị. Guard được áp dụng cả trang cấu hình bàn trong chế độ dev.
API BE vẫn trả 401/403 theo hợp đồng bảo mật; hiển thị 404 trên SPA không thay HTTP status của API.
Luồng USER trở về URL người chơi hoặc nối lại bàn đang chơi sau login giữ nguyên.

Kiểm tra thủ công bằng hai browser có cookie riêng:

1. Browser A login USER. Browser B login ADMIN. Không dùng hai tab cùng browser cho hai tài khoản khác nhau vì cookie dùng chung.
2. ADMIN tìm user bằng email/tên/mã người chơi; chọn **Khóa**, nhập lý do và xác nhận.
3. USER thấy màn **Tài khoản đã bị khóa**; API/socket mất quyền, login lại cũng trả 403.
4. ADMIN chọn **Gỡ khóa**, nhập lý do. USER phải login lại, token cũ không được khôi phục.
5. Thử ban đến một thời điểm tương lai. Đến hạn, USER được phép login lại dù ADMIN chưa bấm gỡ.
6. **Chi tiết** giữ lịch sử BAN/UNBAN cùng admin thực hiện, thời điểm, lý do và hạn ban.

Tài khoản ADMIN không có nút ban hoạt động; BE cũng từ chối ban/unban bản thân và ADMIN khác.
USER không thể gọi các API quản trị bằng cách giả header `X-User-Role`/`X-User-Id`.

## Chính sách

- Phạm vi hiện tại là ban **toàn tài khoản**, có thời hạn hoặc đến khi gỡ. Lý do bắt buộc cho cả ban và unban, 1–500 ký tự sau khi chuẩn hóa.
- Ban đang có hiệu lực khi `banned=true` và `banned_until` là null hoặc lớn hơn thời gian hiện tại.
  So sánh thời hạn ở BE bằng UTC; không cần job cập nhật cờ khi hết hạn. Danh sách và restrictions trả trạng thái hiệu lực này.
- Ban thu hồi mọi refresh session của account trong cùng transaction lưu trạng thái và audit.
  Login, refresh, claim và các protected HTTP/socket từ chối tài khoản bị ban.
  Ban được kiểm tra trước trạng thái revoked để trả đúng **403 AUTH_ACCOUNT_BANNED** cho phiên còn nhận diện được.
- Gỡ ban hoặc hết hạn không bật lại token cũ. User phải đăng nhập lại.
- Ban một tài khoản đang bị ban trả **409 AUTH_BAN_ALREADY_ACTIVE**, giữ nguyên lý do/thời hạn cũ.
  Muốn tạo lệnh mới thì gỡ lệnh hiện tại trước. Unban lặp lại trả 200 và không thêm audit trùng khi cờ đã được gỡ.
- Audit lưu người thực hiện từ JWT đã xác minh, không lấy actorId từ body/header FE.
  Service kiểm tra role ADMIN trong DB, kể cả JWT cũ vẫn ghi ADMIN sau khi role trong DB thay đổi.
- Chưa có UI/command riêng cho CHAT_BAN, RANK_BAN, duyệt report hoặc phát `account.penalized` qua RabbitMQ.
  Các trường chat/rank đã có được đọc để giữ hợp đồng nội bộ; tính năng này không tự ghi chúng.

FE dùng lại cơ chế `tabSession.js` hiện tại. `AUTH_ACCOUNT_BANNED` là lỗi kết thúc quyền sử dụng:
khóa toàn app, unmount provider/socket, không tự refresh. Game gửi `GAME_ACCOUNT_BANNED`, FE quy về cùng lỗi.
Màn khóa có **Kiểm tra lại** để reload và **Đăng nhập tài khoản khác** để trở về form login.
Quay về form không tự khôi phục phiên hoặc sửa cookie của tab/browser khác.

Socket có kiểm tra nền khoảng 1 giây; FE kiểm tra phiên khoảng 2 giây và khi focus.
Thời gian hiển thị còn phụ thuộc mạng/timer browser. Request đã được cấp quyền trước khi ban có thể hoàn tất;
không rollback giao dịch, tự rời bàn, trừ xu hay tự kết thúc ván. Đồng hồ và luật game vẫn do game-service quản lý.

## API quản trị qua gateway

Tất cả API dưới đây cần access cookie HttpOnly, `X-Tab-Id` và ADMIN.
POST còn cần cookie CSRF + `X-XSRF-TOKEN`. Swagger sử dụng Authorize → `tabId` và `csrfToken` như trước.
Public response giữ envelope `{code,message,result,timestamp,path}`.

| Method | URL | Ý nghĩa |
|---|---|---|
| GET | `/api/admin/users?keyword=&banned=true&page=0&size=20` | Danh sách. Bỏ banned để lấy tất cả; false gồm ban đã hết hạn. Keyword tối đa 100 ký tự. |
| GET | `/api/admin/users/{accountId}` | Thông tin hiện tại, không trả password hash/token. |
| POST | `/api/admin/users/{accountId}/ban` | Khóa tài khoản, thu hồi phiên. |
| POST | `/api/admin/users/{accountId}/unban` | Gỡ khóa, không khôi phục token cũ. |
| GET | `/api/admin/users/{accountId}/bans?page=0&size=20` | Lịch sử mới nhất trước. |

Danh sách và lịch sử được phân trang tại DB; page từ 0, size từ 1–100.
`result` là `{content: [...], page, size, totalElements, totalPages}`.
Danh sách sắp theo createdAt/id giảm dần; audit theo occurredAt/id giảm dần.
Account đã xóa không xuất hiện và không thể bị thao tác qua admin API.

Body ban đến khi gỡ:

```json
{"reason":"Spam hoặc vi phạm quy tắc","until":null}
```

Body ban có thời hạn:

```json
{"reason":"Vi phạm quy tắc","until":"2030-01-01T12:00:00Z"}
```

`until` phải còn ở tương lai lúc BE xử lý. FE nhập datetime-local theo giờ thiết bị và chuyển thành ISO UTC;
BE vẫn kiểm tra độc lập. Body unban: `{"reason":"Đã xem xét và chấp nhận khiếu nại"}`.

`result` của detail/ban/unban và từng item danh sách:

```json
{
  "accountId":"user-uuid",
  "email":"player@example.com",
  "displayName":"Player",
  "playerCode":"PLAYER0001",
  "role":"USER",
  "accountStatus":"ACTIVE",
  "emailVerified":true,
  "banned":true,
  "bannedUntil":null,
  "banReason":"Spam hoặc vi phạm quy tắc",
  "bannedAt":"2026-11-04T00:00:00Z",
  "bannedBy":"admin-uuid",
  "createdAt":"2026-11-01T00:00:00Z"
}
```

UUID trong ví dụ là placeholder, dùng giá trị thật từ API. Unban xóa metadata ban hiện tại, audit vẫn giữ.
Ban có thời hạn đã hết có thể còn metadata cũ trong DB/response nhưng `banned=false`.

## Hợp đồng nội bộ restrictions

Đã cung cấp **GET `/internal/accounts/{accountId}/restrictions`**, trả JSON thô đúng ba field:

```json
{"banned":true,"chatBannedUntil":null,"rankBannedUntil":null}
```

`banned` phản ánh ban còn hiệu lực. Account không tồn tại/đã xóa trả 404.
Theo hợp đồng hiện tại, GET này không cần cookie/JWT/service key; chỉ mở đúng endpoint đọc này,
gateway vẫn chặn toàn bộ `/internal/**`. Không đổi quyền của các endpoint nội bộ khác.

Owner game/matchmaking có thể nối endpoint thật bằng cờ cấu hình sẵn có:
`GAME_RESTRICTIONS_ENABLED=true` và `MATCHMAKING_RESTRICTION_STUB_ENABLED=false`.
Đợt này không đổi Compose/cờ stub hay logic queue của service khác. Cơ chế chặn HTTP/socket đã dùng
live session check, độc lập với hai cờ này. Việc hủy queue đã tham gia, xử lý ghế/án phạt trong ván,
và consumer event xử phạt cần owner service phối hợp riêng.

## Kiểm tra tự động

Test PostgreSQL tạm bao phủ: quyền ADMIN/giả header/role cũ, ban bản thân/ADMIN khác,
CSRF/validation, search/filter/phân trang, ban thu hồi phiên, unban không hồi sinh token,
deadline chính xác, audit và login chạy đồng thời với ban. Có hồi quy login/tab và kiểm tra gateway/socket mã lỗi ban.

FE có test browser với API giả lập cho ban/unban, redirect theo role, guard 404, logout và hồi quy USER:

```bash
npm run test:admin-users
```

Playwright cần Chromium đã cài (`npx playwright install chromium --only-shell`).
Trong phiên triển khai đã dùng browser cache tạm `/tmp/poker-ban-browsers`:
`PLAYWRIGHT_BROWSERS_PATH=/tmp/poker-ban-browsers npm run test:admin-users`.
Test chạy Vite riêng ở localhost:5187, không gọi BE thật. Lint phần mới và production build được kiểm tra riêng.
Sidebar shop có sẵn hai lỗi ESLint `no-unused-vars` ở props productCount/bundleCount; kiểm tra bản HEAD cũng lỗi như vậy.
Đợt này chỉ thêm link quản lý user vào sidebar, chưa sửa các props cũ đó.
Chưa nghiệm thu ban bằng ADMIN/USER thật trên stack Docker; chưa chạy V11 vào DB đang dùng.
