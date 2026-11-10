# Phần 7: xác minh gateway trước khi xử lý competition

Phạm vi lần này là HTTP ingress của competition và bỏ cơ chế giả danh ở profile
`dev`. Không đổi controller, nghiệp vụ phòng/giải đấu, quyền chủ phòng, phí/thưởng,
deadline, tạo bàn, database, RabbitMQ, FE hoặc các HTTP client gọi service khác.

## Luồng request

1. FE vẫn gọi gateway port 8080 bằng cookie và `X-Tab-Id`. Request ghi dữ liệu
   tiếp tục cần CSRF cookie/header theo luồng chung.
2. Gateway xác thực JWT/phiên và quyền hiện có; bỏ danh tính/dev header và
   `X-Competition-Gateway-Key` nếu client tự gửi.
3. Riêng route ID `competition-service`, gateway gắn key BE của nó, cùng ID/role
   đã xác thực khi route yêu cầu đăng nhập. Thiếu cấu hình key trả 503
   `GATEWAY_UPSTREAM_UNAVAILABLE` trước khi chuyển tiếp.
4. Competition kiểm tra `X-Competition-Gateway-Key` cho `/api` và `/api/*`, trước
   controller/nghiệp vụ. Thiếu/sai/trùng key hoặc cấu hình rỗng trả 403 `COMP_FORBIDDEN`.
5. Đúng key mới tới kiểm tra ID, role ADMIN, quyền chủ phòng, thành viên, điều kiện
   tham gia và các quy tắc nghiệp vụ hiện có. Key không tự cấp danh tính/quyền.

Gọi thẳng port competition với ID/role ADMIN giả không còn truy cập được API.
Docs/health ngoài `/api` không thuộc filter mới.

## Contract public và quyền

Các public endpoint hiện tại vẫn cho khách xem qua gateway:

- GET `/api/rooms`: danh sách phòng public.
- GET `/api/tournaments`: danh sách giải, giữ query `past`, `page`, `size`.
- GET `/api/tournaments/{tournamentId}`: chi tiết giải.

Gateway vẫn gắn key cho request khách, không gắn danh tính giả. FE không giữ key.
Các API phòng cá nhân/tham gia, đăng ký/xác nhận giải vẫn cần đăng nhập qua gateway.
Tạo/start/cancel giải qua `/api/admin/tournaments/**` vẫn cần ADMIN. Kiểm tra quyền
chủ phòng, thành viên và quyền xem phòng vẫn do service hiện có xử lý.

Competition hiện chưa có HTTP endpoint `/internal/**` riêng. Các request đi từ
competition sang ranking/wallet/shop vẫn dùng internal key đã có; game/social
tiếp tục theo contract trước đó. Không thêm key nội bộ mới cho competition.

## Profile dev

Đã xóa `competition-service/.../security/DevIdentityFilter.java` và test cũ xác
nhận hành vi giả danh; thay bằng test bảo mật với controller/validator thật ở profile `dev`.

- Thiếu role không tự trở thành ADMIN.
- `X-Dev-User` không tạo danh tính và không đè account ID do gateway gửi.
- Key gateway vẫn bắt buộc ở `dev`; ADMIN hợp lệ vẫn quản lý được giải đấu.
- Các cờ mock wallet/shop/social trong `application-dev.properties` giữ nguyên.

README competition đã cập nhật theo contract mới. Đã clean trước build/test để
tránh class filter đã xóa còn sót trong artifact.

## Cấu hình và chạy sau review

`.env.example` thêm `COMPETITION_GATEWAY_KEY=`; `.env` local đã được bổ sung secret
ngẫu nhiên. Chỉ gateway và competition giữ cùng giá trị, khác các key gateway và
internal của service khác. Không đưa key vào FE, query hoặc log.

Thành viên pull code cần điền biến này vào `.env` trước khi chạy Compose. Chạy Java
từ IDE/terminal thì đặt biến trong environment của tiến trình; `--env-file .env`
chỉ truyền cấu hình cho Compose.

Từ root BE, sau review, cập nhật cả gateway và competition:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build api-gateway competition-service
```

Không cần xóa DB/volume hoặc thêm migration. Agent chưa restart/rebuild stack đang
dùng, chưa tạo/hủy giải thật, thu phí hoặc trao thưởng trên dữ liệu thật.

Kiểm tra thủ công khi service sẵn sàng, với port mặc định:

- Tự gắn ID/role ADMIN gọi thẳng port 8087, không có key gateway: nhận 403
  `COMP_FORBIDDEN`, kể cả GET `/api/rooms` hoặc POST start/cancel giải.
- Chưa đăng nhập, gọi `http://localhost:8080/api/rooms` hoặc `/api/tournaments`:
  vẫn xem danh sách public. GET `/api/rooms/me` qua gateway vẫn cần đăng nhập.
- Dùng tài khoản test USER/ADMIN kiểm tra các luồng phòng và giải theo quyền hiện
  có. Chỉ thử tạo/hủy giải, đăng ký thu phí hoặc phát thưởng trên dữ liệu test phù hợp.

## Xác minh đã chạy

- 37 test gateway pass: 32 integration test dùng HTTP fixture và 5 test thiếu key
  wallet/shop/matchmaking/ranking/competition. Kiểm tra public reads, quyền/CSRF,
  giả danh/key giả bị thay, key không chuyển sang route khác, giữ path/method/body.
- 7 test competition ingress pass với profile `dev`: thiếu/sai key chặn đủ 18 API;
  key rỗng/trùng/sai loại bị từ chối; public queries, account ID, room arguments và
  các lệnh quản trị giải giữ đúng contract. USER/thiếu role không trở thành ADMIN;
  `X-Dev-User` không tạo/đè danh tính.
- 6 test HTTP client hồi quy pass: ranking (3), wallet (2), shop (1).
  Tổng 50 test pass.
- Package gateway/competition, kiểm tra Compose với `.env` và `git diff --check`
  thành công; artifact competition không chứa `DevIdentityFilter` đã xóa.

Test ingress dùng context MVC riêng với service mock; gateway/client dùng HTTP
fixture. Không kết nối DB/Redis/RabbitMQ/game/wallet thật và không tạo H2 cho các
test mới. Chưa nghiệm thu trình duyệt hoặc toàn bộ stack Docker đang chạy.

Các phần chặn giả danh HTTP đã triển khai lần lượt cho wallet, shop, matchmaking,
ranking và competition; wallet/shop/ranking có key cho HTTP nội bộ. Đây là phạm vi
đã sửa theo feedback, không phải kết luận toàn hệ thống đã qua kiểm toán bảo mật.
Các điểm xử lý lỗi ví/âm thanh cũ đã ghi trong `WALLET-GATEWAY-REVIEW.md` đã được xử lý
sau đó ở [phần 8](UPSTREAM-ERRORS-REVIEW.md).
