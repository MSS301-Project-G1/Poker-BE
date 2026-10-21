# Khanh test auth + gateway + Eureka như thế nào?

Mục tiêu: chạy lại auth/gateway bản mới, thấy chúng đăng ký vào Eureka, rồi login
qua gateway như trước. Bài này chưa cần chạy game, wallet hay sửa FE.

## Bước 1 — Build và chạy lại đúng bộ của mình

Đứng ở root `Poker-BE`:

```bash
docker compose -p poker-auth-dev --env-file .env \
  -f docker/compose.infra.yml \
  -f discovery-service/compose.yml \
  -f auth-service/compose.yml \
  -f auth-service/compose.dev.yml \
  -f api-gateway/compose.yml \
  -f api-gateway/compose.dev.yml \
  up -d --build discovery-service auth-service api-gateway
```

Lần đầu sau khi pull thay đổi Eureka phải có `--build`. Các lần sau không đổi code
hoặc YAML thì bỏ `--build`. Không cần `down` trước; lệnh này giữ DB/khóa JWT hiện có.
Project `poker-auth-dev` trùng project auth đang dùng trên máy Khanh.

Kiểm tra:

```bash
docker ps --filter label=com.docker.compose.project=poker-auth-dev \
  --format 'table {{.Names}}\t{{.Status}}'
```

Bộ này có 5 container: `discovery-service`, `auth-service`, `api-gateway`,
`auth-postgres`, `rabbitmq` (tên có tiền tố `poker-auth-dev-` và hậu tố `-1`).

## Bước 2 — Nhìn danh bạ Eureka

Mở **http://localhost:8761**. Trong bảng `Instances currently registered with Eureka`
cần thấy `AUTH-SERVICE` và `API-GATEWAY` ở trạng thái UP.

Nếu vừa bật mà chưa thấy, chờ khoảng 30–90 giây rồi refresh trang. Có Eureka nhưng
không có hai dòng này thì chưa coi là đăng ký thành công, chuyển xuống mục lỗi.
Tên được hiển thị viết hoa trong dashboard là bình thường.

## Bước 3 — Kiểm tra gateway thật sự tìm auth qua Eureka

```bash
docker exec poker-auth-dev-api-gateway-1 printenv AUTH_SERVICE_URL
```

Kết quả cần là **`lb://auth-service`**. `lb://` ở đây nghĩa là gateway tìm instance
có tên auth-service trong registry. Nếu vẫn ra `http://auth-service:8090`, kiểm tra
đã build/recreate đúng gateway và `.env` không override `AUTH_SERVICE_URL` về HTTP.

Sau đó gọi:

```bash
curl -i http://localhost:8080/api/auth/csrf
```

Cần nhận **HTTP 200**, cookie `XSRF-TOKEN`, body có `result.token`.
**Bước 2 + URI `lb://` + request 200** là bằng chứng auth đang đi được qua discovery.
Chỉ thấy health UP hoặc chỉ thấy dashboard có tên service thì chưa đủ.

## Bước 4 — Test login như mình vẫn làm

Mở **http://localhost:8080/swagger-ui.html**:

1. Execute `GET /api/auth/csrf`, không nhập body hay query.
2. Copy `result.token` vào **Authorize → csrfToken**.
3. Execute `POST /api/auth/login` với account đã verify OTP.
4. Execute `GET /api/auth/me`: nhận HTTP 200, đúng email/account/role của mình.
5. Execute `POST /api/auth/logout`, rồi gọi `/me` lại: nhận HTTP 401.

Cookie do browser tự gửi. Không copy access JWT vào Bearer và không lấy CSRF mới
trước mỗi request. Cùng một token CSRF có thể dùng cho login/logout như hiện tại.
Nếu muốn test login tiếp sau logout thì dùng CSRF còn hợp lệ hoặc lấy lại khi BE báo hết/sai.

Test bảo vệ riêng bằng terminal, không dùng cookie browser:

```bash
curl -i http://localhost:8080/api/auth/me
# Mong đợi: 401 — terminal này không gửi cookie.

curl -i http://localhost:8080/internal/wallet/credit
# Mong đợi: 403 — FE không được đi vào API nội bộ.
```

Cuối cùng mở FE `http://localhost:5173`, thử login → F5 → logout. Không cần đổi URL
FE sang Eureka; FE vẫn gọi gateway `http://localhost:8080`.

## Khi nào bài test này được tính là xong?

- Eureka thấy auth và gateway UP.
- Gateway có `AUTH_SERVICE_URL=lb://auth-service`, gọi `/csrf` qua 8080 được 200.
- Login `/me` logout qua Swagger hoạt động đúng.
- FE auth đang có vẫn hoạt động.

Đó là xong kiểm tra nền gateway/discovery + auth. Để kết luận wallet/game đã nối
thật thì owner còn phải đăng ký và chạy service đó theo [ONBOARDING.md](ONBOARDING.md).

## Nếu lỗi thì xem chỗ nào?

| Hiện tượng | Kiểm tra tiếp |
|---|---|
| Không mở được 8761 | `docker logs --tail 80 poker-auth-dev-discovery-service-1` |
| Eureka không có AUTH-SERVICE | `docker logs --tail 80 poker-auth-dev-auth-service-1`; tìm lỗi startup/đăng ký registry |
| Eureka có auth nhưng gateway trả 503 | Đợi registry/cache cập nhật; kiểm tra auth/gateway cùng network và host auth công bố là `auth-service` |
| `/csrf` 200 nhưng login 403 CSRF | Lấy `result.token` của browser hiện tại, gán lại Authorize; đừng copy token từ terminal sang browser khác |
| Login 401 hoặc email chưa verify | Dùng account đúng mật khẩu và đã verify; đây là điều kiện auth |
| Một API service khác trả 503 | Kiểm tra service đó đã chạy và có UP trong Eureka chưa; cấu hình route không tự bật container |

Log gateway:

```bash
docker logs --tail 80 poker-auth-dev-api-gateway-1
```

Không dùng `down -v` để xử lý lỗi discovery vì lệnh đó xóa volume dữ liệu.
