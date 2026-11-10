# Một tài khoản, một phiên và một tab hoạt động

Cập nhật 01/11/2026, triển khai ở BE `fix/protect_login` và FE `fix/protect-login`.

## Hành vi đã chốt

| Tình huống | Kết quả |
|---|---|
| B đăng nhập đúng tài khoản A đang dùng ở browser/máy khác | B thắng; access và refresh của A bị chặn khi kiểm tra phiên. A thấy “Tài khoản đã đăng nhập ở nơi khác.” |
| B mở thêm tab trong cùng browser | B tiếp quản phiên dùng chung cookie. A ngắt socket, khóa toàn màn hình và hiện “Bạn đã chuyển sang tab khác. Bạn có thể đóng tab này.” |
| Đang có bàn chơi | B tự vào lại cùng bàn theo account, giữ ghế/bài/chip của game đang chạy. Không gọi leave, không tạo bàn mới. |
| Chưa có bàn | B dùng ứng dụng bình thường. |
| Quay lại focus A | A vẫn khóa; không tự giành lại quyền. |
| Đóng B | A vẫn khóa. F5 A là một lần khởi tạo mới và có thể tiếp quản lại. |

Tab mất quyền không gọi logout, không xóa cookie và không tự refresh/reconnect.
Trình duyệt thường không cho tự đóng tab do người dùng mở, nên A hiển thị màn khóa.
Toàn bộ khu vực chức năng bị khóa, gồm cả shop/ví; không chỉ chặn màn game.
Chưa thêm luật cấm chủ tab đang hoạt động mua hàng khi đang chơi.

## Chạy và test ngay

Từ root BE, dùng cùng project Compose/volume đã chạy trước đó. Ví dụ project hiện tại:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build auth-service api-gateway game-service social-service chat-service
```

Compose tự đưa lên các dependency đã khai báo. Discovery phải đang chạy; nếu chưa có:

```bash
docker compose -p poker-auth-dev --env-file .env up -d discovery-service
```

Nếu muốn toàn bộ stack, vẫn dùng lệnh quen thuộc:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build
```

FE cần code nhánh tương ứng, chạy `npm run dev` như hiện tại, FE gọi gateway `http://localhost:8080`.
Trước khi rebuild game, kết thúc các ván thử: game hiện lưu bàn đang chơi trong bộ nhớ,
restart game không phải phép thử giữ lại ván.

**Lần nâng cấp này cần đăng nhập lại.** Migration V10 thêm tab owner/trạng thái thay phiên và
thu hồi các phiên trước nâng cấp vì chúng chưa có `sid`/tab owner. Migration chạy khi auth khởi động.
Không xóa volume và không sửa migration đã dùng. Lúc triển khai code, agent chưa chạy migration lên DB của fen.

Trong `.env` có thêm `AUTH_SESSION_INTERNAL_KEY`. Auth, gateway, game, social và chat phải dùng cùng giá trị;
Compose đã truyền biến này. File `.env` local đã được bổ sung key ngẫu nhiên nếu trước đó chưa có.
Key chỉ ở BE, không thêm vào `VITE_*` hoặc gửi cho FE. Java chạy ngoài Docker cần URL
`AUTH_SESSION_CHECK_URL=http://localhost:8090/internal/auth/session/check` ở gateway/game/social/chat;
Compose dùng hostname nội bộ `auth-service` sẵn.

### Test hai tab cùng browser

1. Tab A login, vào một bàn thật. Ghi nhớ tableId/ghế/bài/chip hiện tại.
2. Mở tab B cùng origin FE, kể cả mở thẳng URL sảnh/shop.
3. B khôi phục phiên, hỏi bàn đang chơi và chuyển về đúng bàn đó trước khi mount các chức năng khác.
4. A hiện màn khóa, ngắt game/social/chat socket; focus A không được tự mở khóa.
5. Thao tác game ở B vẫn chạy. A không được mua hàng, refresh hoặc logout thay B.
6. Đóng B: A vẫn khóa. F5 A: A được tiếp quản và khôi phục bàn nếu phiên còn hạn.

Kiểm tra Network: protected HTTP có `X-Tab-Id`, STOMP CONNECT có `X-Tab-Id` và Bearer JWT.
Muốn test BE độc lập, dùng cookie mới của browser với **tab ID cũ A** gọi `/api/auth/me`,
`/api/auth/refresh` hoặc `/api/auth/logout`: nhận 409 `AUTH_TAB_REPLACED` (write cần CSRF đúng),
không có Set-Cookie xóa token và B vẫn dùng được. Không in/chia sẻ giá trị cookie thật.

### Test hai browser hoặc hai thiết bị

1. A login và chơi; B dùng browser khác hoặc cửa sổ ẩn danh riêng, login cùng tài khoản.
2. A bị khóa với thông báo đăng nhập ở nơi khác, không tự refresh hoặc nối lại socket.
3. B vào lại cùng bàn/ghế; engine tiếp tục ván hiện tại.
4. Access JWT cũ dù còn hạn vẫn nhận 401 `AUTH_SESSION_REPLACED`; refresh phiên cũ cũng bị từ chối.
5. Account khác không bị ảnh hưởng. Login sai mật khẩu không đá người đang chơi.

Quyền được kiểm tra trước mỗi protected HTTP và mỗi STOMP SEND/SUBSCRIBE; không đợi JWT hết hạn.
Socket đứng yên được quét theo chu kỳ khoảng 1 giây, FE kiểm tra khoảng 2 giây và khi focus.
Thời gian hiện màn khóa/đóng socket còn phụ thuộc số kết nối, mạng và việc browser hạn chế timer tab nền.
BroadcastChannel giúp báo nhanh cùng origin nhưng BE mới quyết định quyền.
Request đã được cho qua trước lúc chuyển quyền có thể hoàn tất; không rollback giao dịch đã thực hiện.
Không tự retry thao tác mua hàng/chuyển tiền sau lỗi mạng không rõ kết quả.

## Hợp đồng FE

Mỗi document tạo một UUID bằng `crypto.randomUUID()`, giữ trong bộ nhớ và gửi header `X-Tab-Id`.
Không lấy tab ID từ cookie/localStorage/sessionStorage: duplicate tab có thể sao chép sessionStorage.
ID này phải giữ nguyên qua HTTP và STOMP trong document đó. F5 tạo ID mới.

| API | Cookie/CSRF | Ý nghĩa |
|---|---|---|
| GET `/api/auth/csrf` | Public | Lấy CSRF như trước. |
| POST `/api/auth/login` | CSRF + X-Tab-Id; body email/password | Tạo session mới, thay mọi session cũ của account. |
| POST `/api/auth/session/claim` | Refresh cookie + CSRF + X-Tab-Id; không body | Tab mới tiếp quản; cấp lại access/refresh, giữ sessionId và hạn 7 ngày ban đầu. Không cần access còn hạn. |
| GET `/api/auth/session` | Access cookie + X-Tab-Id | Kiểm tra còn là phiên/tab chủ; trả accountId/sessionId/tabId. Không yêu cầu CSRF. |
| POST `/api/auth/refresh` | Refresh cookie + CSRF + X-Tab-Id | Chỉ tab chủ được refresh; đổi cả hai token, giữ sessionId/hạn phiên. |
| POST `/api/auth/logout` | Refresh cookie + CSRF + X-Tab-Id | Chỉ tab chủ được revoke phiên còn sống và xóa cookie. |
| GET `/api/auth/me`, `/api/auth/ws-token` và protected API khác | Access cookie + X-Tab-Id | Kiểm tra chữ ký JWT và trạng thái phiên/tab trước xử lý. Write vẫn cần CSRF. |

Login, refresh và claim giữ envelope `ApiResponse`; `result` bổ sung hai UUID:

```json
{
  "accountId": "account-uuid",
  "email": "player@example.com",
  "role": "USER",
  "accessExpiresAt": "2026-11-01T15:15:00Z",
  "refreshExpiresAt": "2026-11-08T15:00:00Z",
  "sessionId": "session-uuid",
  "tabId": "tab-uuid"
}
```

Ví dụ UUID chỉ minh họa vị trí, FE dùng giá trị thật. Access/refresh vẫn là cookie HttpOnly,
không nằm trong JSON này. JWT có thêm claim `sid`, bằng ID bản ghi phiên refresh.
`sessionId` ổn định khi refresh/chuyển tab; login mới tạo `sessionId` khác.

FE bootstrap: CSRF → claim một lần/document → `/me` → GET `/api/game/me/active-table`
→ có bàn thì replace URL `/table/{tableId}` → mới mount feature providers/socket.
Không claim khi focus hoặc bị đá, nếu không các tab sẽ giành quyền liên tục.
Nếu game chưa sẵn sàng, FE giữ màn “Khôi phục phiên chơi” và nút Thử lại,
không mở sảnh/shop trong khi chưa biết account có bàn hay không. Vì vậy test FE luồng này cần game và gateway route game hoạt động.

Các lần login/claim/refresh/logout dùng chung Web Lock `poker.auth.session` trong browser;
refresh cùng tab vẫn gộp bằng một promise. Broadcast chỉ báo có thay đổi,
không chứa JWT/cookie/tab owner. Không lưu JWT vào localStorage.

| Lỗi | FE xử lý |
|---|---|
| 401 `AUTH_SESSION_REPLACED` | Khóa app, dừng refresh/reconnect, hiện đăng nhập nơi khác. |
| 409 `AUTH_TAB_REPLACED` | Khóa app, ngắt socket, cho người dùng đóng tab. |
| 401 access hết hạn thông thường | Refresh một lần ở tab chủ rồi thử lại request đọc phù hợp. |
| 401 `AUTH_REFRESH_INVALID` | Phiên đã kết thúc; yêu cầu login lại, không tự claim giành quyền. |
| 503 `AUTH_SESSION_UNAVAILABLE` | Chưa kiểm tra được quyền; không thực hiện action. Không báo giả là login nơi khác. |

Game gửi STOMP ERROR `GAME_SESSION_REPLACED`/`GAME_TAB_REPLACED`, FE quy về hai màn khóa tương ứng.
Social/chat có thể đóng socket trực tiếp; kiểm tra `/api/auth/session`/Broadcast giúp FE xác định việc mất quyền.

### Swagger

Mở `http://localhost:8090/swagger-ui.html`, Authorize → `tabId`: nhập một UUID từ `crypto.randomUUID()`.
GET csrf → copy `result.token` vào Authorize → `csrfToken` → login và gọi API như cũ.
Giữ cùng `tabId` cho lần test. Muốn giả lập tab mới trong cùng cookie: đổi UUID rồi gọi POST session/claim.
Chỉ đổi UUID rồi gọi `/me` sẽ bị 409 vì chưa tiếp quản. Swagger không tự quản lý tab như FE.

## BE thực hiện kiểm tra ở đâu?

- Auth lưu activeTabId/revokedAt/replaced ở `refresh_tokens`, kiểm tra bằng `SessionGuard`.
  Login/claim/refresh/logout khóa account trước token để tuần tự hóa thay đổi quyền.
- Gateway vẫn verify RS256 bằng JWKS; sau đó gọi auth qua GET `/internal/auth/session/check`
  bằng Bearer JWT + X-Tab-Id + X-Session-Internal-Key. Endpoint trả 204 khi hợp lệ,
  lỗi trả status và header X-Session-Error. Gateway không cache trạng thái phiên.
- `/internal/**` bị chặn ở gateway; không cho FE gọi endpoint authority hoặc biết key nội bộ.
- Game/social/chat dùng `common.security.SessionAccessClient` để kiểm tra CONNECT và lệnh trên socket,
  đồng thời quét phiên cũ. Không thay logic bài, chip, bàn, bạn bè hoặc chat.
- Auth lỗi/mất kết nối: protected action fail closed. Socket game chờ kiểm tra nền giữ kết nối khi authority 503,
  nhưng gửi lệnh vẫn bị từ chối; không tự coi đó là một lần login mới.

Service HTTP đang tin header gateway vẫn cần bảo vệ đường vào trực tiếp bằng hợp đồng key/network hiện tại.
Nếu owner thêm WebSocket hay endpoint dùng JWT trực tiếp ngoài gateway, owner phải kiểm tra trạng thái phiên
như các socket trên; chỉ verify chữ ký JWT không đủ để thực hiện thu hồi ngay.

## Kiểm tra đã chạy

Đã qua các test trọng tâm auth/gateway, chiếm quyền tab, access/refresh bị thay phiên,
đổi/reset mật khẩu, profile, điểm danh và game socket/reconnect. Social/chat regression dùng
mock authority; các test này không thay thế thao tác hai browser trên stack thật.
Migration được chạy trong PostgreSQL Testcontainers tạm, không trên DB đang dùng.

FE: `npm run test:session` chạy 6 test hành vi bằng Node trên source client thực;
focused ESLint và production build đã được kiểm tra. Test Node mô phỏng browser/HTTP,
chưa chứng minh hành vi tab nền, socket và cookie trong browser thật. Dùng checklist ở trên để nghiệm thu.
