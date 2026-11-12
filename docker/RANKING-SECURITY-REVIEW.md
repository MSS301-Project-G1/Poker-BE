# Phần 6: bảo vệ ranking qua gateway và HTTP nội bộ

Phạm vi: xác minh caller HTTP của ranking, bổ sung key cho hai client nội bộ hiện
có và bỏ cơ chế giả danh ở profile `dev`. Dừng để review/commit trước khi bảo vệ
gateway ingress và xử lý `DevIdentityFilter` của competition.

Không đổi controller, công thức Elo, mùa giải, phần thưởng, tutorial, database,
RabbitMQ hoặc FE. Các cờ mock ở profile `dev` giữ nguyên.

## Hai luồng độc lập

| Luồng | Bên giữ key | Biến environment | Header |
| --- | --- | --- | --- |
| FE → gateway → ranking `/api/**` | gateway và ranking | `RANKING_GATEWAY_KEY` | `X-Ranking-Gateway-Key` |
| Backend → ranking `/internal/**` | ranking, matchmaking, competition | `RANKING_INTERNAL_KEY` | `X-Ranking-Internal-Key` |

Dùng hai giá trị ngẫu nhiên khác nhau; không dùng lại key của wallet/shop/matchmaking.
Key chỉ dùng ở BE, không đưa vào FE, query URL hoặc log.

### Request qua gateway

1. FE vẫn gọi port 8080 bằng cookie, `X-Tab-Id` và CSRF cho request ghi dữ liệu.
2. Gateway xác thực JWT/phiên và quyền theo contract hiện tại. Nó bỏ ID/role/dev
   header và cả hai loại key ranking nếu client tự gửi.
3. Riêng route ID `ranking-service`, gateway gắn `X-Ranking-Gateway-Key` của nó
   và danh tính user đã xác thực khi route cần đăng nhập. Thiếu cấu hình key
   trả 503 `GATEWAY_UPSTREAM_UNAVAILABLE` trước khi chuyển tiếp.
4. Ranking kiểm tra key cho `/api` và `/api/*` trước controller/nghiệp vụ.
   Thiếu/sai/trùng key hoặc cấu hình rỗng trả 403 `RANK_FORBIDDEN`.
5. Đúng key mới tới các kiểm tra user/ADMIN, quyền đọc kết quả trận và nhận thưởng
   hiện có. Key không tự tạo danh tính hoặc cấp quyền ADMIN.

Leaderboard, mùa giải hiện tại và danh sách thưởng mùa giải vẫn public qua
gateway theo contract. API thông tin cá nhân/tutorial/nhận thưởng vẫn cần đăng nhập;
admin seasons vẫn cần role ADMIN. Gọi thẳng port ranking, kể cả API public,
cần key gateway. FE không cần sửa để giữ key.

### Request nội bộ

- Matchmaking thêm key khi đọc GET `/internal/ranking/{accountId}` để lấy Elo.
- Competition thêm key khi đọc GET `/internal/ranking/top?limit=...`.
- Ranking kiểm tra key cho `/internal` và `/internal/*`. Thiếu/sai/trùng key hoặc
  cấu hình rỗng trả 403 `RANK_INTERNAL_FORBIDDEN` trước khi đọc dữ liệu.
- Key gateway không thay được key nội bộ và ngược lại. Gateway tiếp tục chặn
  `/internal/**`, kể cả client có cookie ADMIN.
- Body/response, query `limit`, timeout và cờ stub/mock không đổi. Nếu ranking từ
  chối key, hai client báo lỗi dịch vụ phụ thuộc theo luồng xử lý lỗi hiện có.

Key nội bộ xác nhận caller thuộc nhóm backend tin cậy, chưa phân quyền riêng từng
caller/endpoint. Docs/health ngoài `/api` và `/internal` không thuộc hai filter này.

## Thay đổi ở profile dev

Đã xóa `ranking-service/.../security/DevIdentityFilter.java` và bộ test cũ xác nhận
hành vi giả danh; thay bằng test bảo mật chạy profile `dev` với controller/validator thật.

- Thiếu role không tự trở thành ADMIN.
- `X-Dev-User` không thay account ID, không tạo danh tính và không thay quyền.
- Các key ingress vẫn bắt buộc ở profile `dev`.
- Role ADMIN do gateway cung cấp hợp lệ vẫn truy cập được API quản trị.

Đã clean trước build/test để tránh class filter đã xóa còn sót trong artifact.
README ranking được cập nhật theo hợp đồng mới. Không sửa `DevIdentityFilter`
của competition trong phần này.

## Cấu hình và chạy sau review

`.env.example` thêm `RANKING_GATEWAY_KEY=` và `RANKING_INTERNAL_KEY=`. `.env` local
đã được bổ sung hai giá trị ngẫu nhiên, không in ra docs. Thành viên pull code cần
điền hai biến trước khi chạy Compose. Khi chạy Java từ IDE/terminal, đặt chúng trong
environment của tiến trình; `--env-file .env` chỉ truyền cấu hình cho Compose.

Phải cập nhật ranking và hai caller cùng lúc vì caller bản cũ chưa gửi internal key.
Từ root BE:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build api-gateway ranking-service matchmaking-service competition-service
```

Không cần xóa DB/volume hoặc thêm migration. Agent chưa rebuild/restart stack đang
dùng, chưa tác động Elo, mùa giải, trận hoặc phần thưởng thật.

Kiểm tra thủ công sau khi service sẵn sàng, dùng các port mặc định:

- Tự gắn ID/role ADMIN gọi thẳng `http://localhost:8086/api/admin/seasons`, không
  có key gateway: nhận 403 `RANK_FORBIDDEN`.
- Gọi thẳng `http://localhost:8086/internal/ranking/top?limit=7`, không có internal
  key: nhận 403 `RANK_INTERNAL_FORBIDDEN`.
- Chưa đăng nhập, gọi `http://localhost:8080/api/ranking/leaderboard?page=0&size=20`:
  vẫn xem được leaderboard. API `/api/ranking/me` qua gateway vẫn cần đăng nhập.
- Dùng tài khoản test để xác minh matchmaking đọc Elo và competition đọc top theo
  các luồng nghiệp vụ hiện có; không dùng nhận thưởng/đóng mùa giải thật để thử key.

## Xác minh

- 36 test gateway pass: 32 integration test dùng HTTP fixture và 4 test thiếu key
  gateway wallet/shop/matchmaking/ranking. Kiểm tra public reads, danh tính được
  xác thực, role/CSRF, key giả bị thay, không chuyển key sang route khác và chặn internal.
- 7 test ranking pass với profile `dev`: chặn đủ 14 API endpoint và 2 internal endpoint;
  kiểm tra key rỗng/trùng/sai loại, phân trang public, quyền USER/ADMIN, tutorial/
  nhận thưởng/account ID và hành vi an toàn khi thiếu role hoặc gửi `X-Dev-User`.
- 7 test matchmaking pass: 5 test ingress hồi quy và 2 test HTTP client đọc Elo,
  gồm gửi đúng key và ánh xạ 403 thành lỗi dependency.
- 3 test competition ranking client pass: gửi key, giữ query/response và báo lỗi
  dependency khi upstream mất kết nối hoặc từ chối key. Tổng 53 test pass.
- Package 4 module bị ảnh hưởng, kiểm tra Compose với `.env` và `git diff --check`
  thành công. Artifact ranking không chứa `DevIdentityFilter` đã xóa.

Ranking dùng context MVC riêng với service mock; client/gateway dùng HTTP fixture.
Không kết nối DB/Redis/RabbitMQ/game/wallet thật và không tạo H2 cho các test mới.
Chưa nghiệm thu FE hoặc toàn bộ stack Docker đang chạy.
