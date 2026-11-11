# Swagger chung qua gateway

## Trước và sau

Trước đây `/swagger-ui.html` và `/v3/api-docs/**` trên gateway đều được proxy tới
auth, nên mở port 8080 hay 8090 đều chỉ thấy API auth.

Nay gateway tự phục vụ giao diện Swagger tại:

```text
http://localhost:8080/swagger-ui.html
```

Chọn service trong **Select a definition**: auth, game, matchmaking, social,
chat, ranking, competition, shop và wallet. Tài liệu được chia theo service;
không gộp schema của các module vào một tài liệu lớn để tránh trùng tên model.
Identity hiện chỉ có application skeleton, chưa có controller nên không đưa vào.

Auth vẫn giữ Swagger riêng ở `http://localhost:8090/swagger-ui.html`.

## Luồng hoạt động

1. Browser tải giao diện và danh sách service từ gateway.
2. Chọn wallet → browser GET `/v3/api-docs/wallet-service` trên gateway.
3. Gateway lấy `/v3/api-docs` của wallet qua URI trong catalog (mặc định Eureka),
   giữ schemas và chỉ đưa các operation có route `/api/**` vào tài liệu.
4. Gateway đặt `servers` về `/`, bỏ server override của path/operation. Try it
   out vì vậy gọi đúng origin gateway, không gọi trực tiếp port service.
5. API thật tiếp tục đi qua JWT, kiểm tra phiên/tab, role, CSRF và gateway key
   hiện có. Tài liệu công khai không làm API nghiệp vụ trở thành public.

Gateway không forward cookie hay Authorization của browser khi lấy tài liệu
từ service. Tài liệu không đưa các endpoint `/internal/**`, `/dev/**` hoặc API
chưa đăng ký route vào giao diện; header danh tính/key nội bộ không phải input
cho người dùng Swagger. Game vẫn kiểm tra service key khi gateway đọc tài liệu.

Nếu service đang tắt hoặc chưa có OpenAPI, chọn service đó sẽ báo lỗi 503 thay
vì hiện API auth. Các mục khác vẫn độc lập.

## Cách test

1. Mở Swagger gateway, chọn `auth-service`.
2. Execute `POST /api/auth/login` với email/mật khẩu. Giao diện tự lấy CSRF và
   gửi `X-Tab-Id`; trình duyệt lưu cookie HttpOnly từ response.
3. Chọn service khác rồi Try it out. Cookie/tab ID được giữ, không cần nhập
   Bearer JWT, X-User-Id, role hoặc key nội bộ.
4. Đơn POST/PUT/PATCH/DELETE tự lấy CSRF trước khi gọi API. Request URL trong
   Swagger phải là `localhost:8080/api/...` khi đang dùng local mặc định.

Trang Swagger không tự tiếp quản phiên FE khi mở. Nếu muốn dùng refresh cookie
của phiên hiện có, chủ động gọi `POST /api/auth/session/claim` trong mục auth;
quy tắc khóa tab cũ vẫn áp dụng như FE. Nếu phiên hết hạn, dùng endpoint refresh
hoặc login lại; Swagger không tự refresh mọi request như HTTP client FE.

Thực hiện giao dịch thanh toán, grant vật phẩm hay admin ban trong Swagger có
tác dụng nghiệp vụ thật giống FE; bộ kiểm tra của đợt này chỉ dùng upstream giả.

## Service owner cần làm gì?

- Giữ hợp đồng route trong `api-gateway/src/main/resources/services/<service>.yml`
  và import file đó vào `application.yml` như hiện tại. Swagger lấy danh sách
  từ chính catalog này, không cần thêm URL Swagger hoặc code Java ở gateway.
- Service Spring MVC phải sinh `/v3/api-docs`. Với Spring Boot 4.1 hiện tại,
  có thể dùng `springdoc-openapi-starter-webmvc-api:3.1.1`; `webmvc-ui` cũng đã
  bao gồm phần sinh tài liệu. API-only đủ nếu chỉ dùng giao diện gateway.
- Đặt `springdoc.paths-to-match=/api/**`. Game, ranking, competition vừa được
  bổ sung dependency API-only và cấu hình này; không sửa nghiệp vụ của chúng.
- Nếu module chưa có API/docs, đặt `documentation-enabled: false` trong entry
  catalog của nó, giống identity. Mặc định các service trong catalog có docs.
- Mô hình hiện tại giữ nguyên đường dẫn API qua gateway. Owner dùng strip-prefix
  cho service mới cần cấu hình/đối chiếu đường dẫn OpenAPI tương ứng trước khi
  bật mục Swagger; identity skeleton đang dùng strip-prefix đã được loại khỏi danh sách.

Tham khảo: [Swagger UI configuration](https://swagger.io/docs/open-source-tools/swagger-ui/usage/configuration/)
và [springdoc compatibility](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).

## Chạy sau review

Từ root BE, cập nhật gateway và ba service vừa thêm bộ sinh tài liệu:

```bash
docker compose -p poker-auth-dev --env-file .env -f docker-compose.yml \
  up -d --build api-gateway game-service ranking-service competition-service
```

Đây là lệnh để người chạy thực hiện sau review. Chưa restart stack đang chạy.
Game giữ bàn ở RAM, nên chọn lúc không còn ván cần giữ để rebuild/restart game.
Không cần xóa volume hay chạy migration cho Swagger.

Không cần secret hoặc port mới. Có tùy chọn `GATEWAY_SWAGGER_ENABLED=true` trong
env examples và Compose. Đặt false để tắt Swagger trên gateway; không đổi API
nghiệp vụ hoặc cấu hình tài liệu riêng của auth.

Trên VPS, nếu muốn truy cập UI qua domain thì reverse proxy cần chuyển cả
`/swagger-ui.html`, `/swagger-ui/**` và `/v3/api-docs/**` tới gateway. Đợt này
không sửa reverse proxy hoặc public thêm port.

## Đã xác minh

- 41 test gateway/routing/discovery pass, gồm lấy OpenAPI qua Eureka, đủ 9 mục,
  rewrite server, không forward cookie/key từ client, lọc API nội bộ và lỗi upstream.
- Browser Chromium với gateway thật chạy riêng + upstream giả: thấy 9 mục,
  login với CSRF/tab ID tự động, đổi sang wallet và gọi API bằng cookie thành
  công. Các request browser đều đi cùng origin gateway; không có lỗi JavaScript.
- 4 test JavaScript pass: CSRF/tab ID/cookie, giữ tab qua reload, chặn gọi thẳng
  service khác origin và dừng thao tác ghi nếu không lấy được CSRF.
- Package gateway/game/ranking/competition, Compose config và diff check pass.
- Chưa thay image trên stack local/VPS, chưa nghiệm thu các bộ sinh OpenAPI mới
  trên game/ranking/competition đang chạy và chưa gọi nghiệp vụ thật để test Swagger.

Chạy test JS không cần cài thêm package:

```bash
node api-gateway/src/test/js/swagger-initializer.test.mjs
```
