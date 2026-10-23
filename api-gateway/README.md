# API Gateway + Eureka

FE gọi **gateway 8080**. Gateway xác minh cookie JWT, kiểm tra quyền/CSRF rồi tìm
service trong **Eureka 8761** để chuyển request. URL API cũ giữ nguyên.

## Đọc bài nào?

| Bạn cần làm gì? | Đọc tài liệu này |
|---|---|
| Khanh chạy/test phần auth + gateway + Eureka ngay | **[TESTING.md](TESTING.md)** — lệnh chạy, từng thao tác Swagger, kết quả mong đợi, xem lỗi |
| Thành viên tự nối BE và làm FE module mình | **[ONBOARDING.md](ONBOARDING.md)** — ví dụ ranking từ POM → properties → Compose → gateway YAML → gọi API → FE |

Hai file nằm trong Git; mọi người pull code sẽ có. Thư mục `docs/` đang gitignore,
vì vậy gửi link hai file trên cho nhóm thay vì chỉ gửi tài liệu trong `docs/`.

## Hiện đã làm tới đâu?

- Có Eureka Server (`discovery-service`), auth và gateway đã cấu hình Eureka client.
- Route/quyền được đọc từ `src/main/resources/services/<service-name>.yml`.
- Có file gateway cho các module hiện tại. Owner module khác **vẫn phải tự thêm
  client, cấu hình và chạy service của họ** thì gateway mới tìm thấy qua Eureka.
- API mới thuộc prefix đã có, cần login bình thường: không phải thêm từng API vào gateway.
- Owner tự làm adapter/màn hình FE feature của mình bằng client auth dùng chung.

## Quy tắc chung

| Loại request | Gateway xử lý |
|---|---|
| `/api/**` bình thường | Cần cookie access hợp lệ |
| `/api/admin/**` | Cần ADMIN |
| Public method/path trong YAML | Cho khách gọi; có thể bật danh tính tùy chọn |
| POST/PUT/PATCH/DELETE | Cần CSRF, trừ ngoại lệ khai báo cụ thể |
| `/internal/**` | Chặn khỏi FE |
| Service chưa có instance UP | Trả 503 |

Gateway xóa header danh tính/Bearer giả do client gửi, xác minh JWT RS256 bằng
JWKS của auth rồi gắn `X-User-Id`, `X-User-Role`, `X-Request-Id`. Service tự kiểm tra
sở hữu tài nguyên/nghiệp vụ và bảo vệ đường vào trực tiếp trước khi tin header.
JWT kiểm tra issuer/audience/UUID subject/role/time và các claim bắt buộc; không
blacklist access token sau logout theo hợp đồng hiện tại.

JWKS gateway dùng HTTP URL riêng (`GATEWAY_JWK_SET_URI`), Compose trỏ
`http://auth-service:8090/.well-known/jwks.json`; issuer vẫn `http://localhost:8090`.
HTTP proxy connect timeout 3 giây, response timeout 10 giây; JWKS timeout 3/5 giây.
CORS local mặc định FE 5173, gateway 8080, auth 8090; thêm origin ở cả auth/gateway khi đổi.

Thay YAML/import cần build/recreate gateway; thay địa chỉ instance được Eureka cập
nhật qua registry/cache. Không có hot reload file quyền và không tự mở
`/service-name/**`. WebSocket chưa proxy qua gateway. Auth thưởng điểm danh vẫn
gọi HTTP nội bộ wallet trực tiếp.

35 test quan trọng đã qua ở đợt triển khai discovery: hồi quy gateway, đăng ký/tra
cứu Eureka thật và gateway dùng Eureka client/load balancing với HTTP fixture.
Auth/gateway/discovery build thành công; các kết quả này chưa chứng minh Docker/FE
hoặc game/wallet nghiệp vụ thật đã chạy. Làm bài test thủ công trước khi bàn giao.
