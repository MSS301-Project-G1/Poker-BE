# Thành viên nối service của mình vào gateway

Bạn tự làm BE và FE feature của mình. Gateway/auth/Eureka là phần dùng chung đã có.
Bạn không cần giao danh sách API cho Khanh để Khanh viết cấu hình hoặc FE hộ.

Bài này dùng **ranking-service** làm ví dụ thật. Owner khác thay tên module, port
và API bằng của mình. Chạy Docker trên cùng máy trước; chưa cần thử ghép nhiều máy.

## Hiểu luồng bằng một ví dụ

Người chơi mở màn Xếp hạng:

```text
FE gọi GET http://localhost:8080/api/ranking/me kèm cookie
  → gateway xác minh user đang đăng nhập
  → gateway tra Eureka: ranking-service đang ở host/port nào?
  → gateway gọi GET /api/ranking/me của ranking-service
    và gắn X-User-Id, X-User-Role
  → ranking trả dữ liệu
  → gateway chuyển nguyên response về FE
```

Service đăng ký địa chỉ vào Eureka khi khởi động. FE chỉ gọi gateway; không tự lấy
JWT, không gọi Eureka và không tự gắn `X-User-Id`.

## Bước 0 — Pull code và chạy phần dùng chung

Chạy hết [bài test nền](TESTING.md) trước. Cần mở được Swagger 8080, dashboard
Eureka 8761 và login bằng account đã verify. Chỉ mở health UP là chưa đủ.

Hiện auth/gateway đã có Eureka client. Ranking, wallet, game... cần owner làm các
bước tiếp theo; có file route sẵn không có nghĩa service đã tự đăng ký.

## Bước 1 — Thêm thư viện vào BE của bạn

Mở **`ranking-service/pom.xml`**, thêm vào bên trong `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Giữ các dependency cũ. Không ghi version riêng vì parent POM đã quản lý.

## Bước 2 — Cho service biết đăng ký danh bạ ở đâu

Mở **`ranking-service/src/main/resources/application.properties`**.
File đã có `spring.application.name=ranking-service`, giữ nguyên dòng đó và thêm:

```properties
eureka.client.enabled=${EUREKA_CLIENT_ENABLED:true}
eureka.client.service-url.defaultZone=${EUREKA_SERVICE_URL:http://localhost:8761/eureka/}
eureka.instance.hostname=${EUREKA_INSTANCE_HOSTNAME:localhost}
eureka.instance.prefer-ip-address=false
eureka.instance.instance-id=${spring.application.name}:${random.value}
```

Không cần tạo controller đăng ký hoặc thêm `@EnableEurekaClient`.
Trong profile test của module nên đặt `eureka.client.enabled=false` để test không
đăng ký vào danh bạ đang dùng thật.

Mở **`ranking-service/compose.yml`**, tìm:

```yaml
services:
  ranking-service:
    environment:
      # Các giá trị cũ của module đang ở đây.
```

Thêm hai dòng vào **cùng block environment**, giữ các giá trị cũ:

```yaml
      EUREKA_SERVICE_URL: http://discovery-service:8761/eureka/
      EUREKA_INSTANCE_HOSTNAME: ranking-service
```

Giải thích đúng hai giá trị này:

- `EUREKA_SERVICE_URL`: địa chỉ container danh bạ.
- `EUREKA_INSTANCE_HOSTNAME`: địa chỉ gateway dùng để gọi ngược về container của bạn.
  Ranking chạy trong Docker thì công bố `ranking-service`, không công bố `localhost`.

Tên `spring.application.name`, tên service Compose và tên trong `lb://...` ở bước
sau cần thống nhất. Service phải nối chung Docker network với gateway/discovery.

## Bước 3 — Xem file gateway của module mình

Mở **`api-gateway/src/main/resources/services/ranking-service.yml`**.
File đã có sẵn; không thêm route lần nữa vào `application.yml`.

```yaml
gateway:
  catalog:
    services:
      ranking-service:
        uri: ${RANKING_SERVICE_URL:lb://ranking-service}
        routes:
          - id: ranking-service
            paths:
              - /api/ranking/**
              - /api/seasons/**
              - /api/tutorial/**
              - /api/admin/seasons/**
        public-endpoints:
          - method: GET
            paths:
              - /api/ranking/leaderboard
              - /api/seasons/current
              - /api/seasons/*/rewards
```

Đọc file bằng ba câu:

1. Tìm service tên **ranking-service** trong Eureka.
2. URL bắt đầu bằng một trong bốn prefix thì chuyển tới service này, giữ nguyên path.
3. Ba GET trong `public-endpoints` cho khách xem; API khác cần đăng nhập,
   URL `/api/admin/**` cần ADMIN.

Ví dụ sau này bạn thêm `GET /api/ranking/history`: **không sửa gateway**, vì đã
nằm dưới `/api/ranking/**` và cần login theo mặc định.
Nếu bạn tạo API dưới prefix mới thì thêm prefix vào `routes[].paths`.
Nếu API mới cho khách xem thì thêm **đúng method/path** vào `public-endpoints`.

Shop/wallet/game... đã có file tương ứng trong cùng thư mục; sửa đúng file của bạn.
Service hoàn toàn mới chưa có file/import thì xem mục cuối bài.

## Bước 4 — Chạy thêm module của bạn

Ví dụ ranking, đứng ở root `Poker-BE`:

```bash
docker compose -p poker-auth-dev --env-file .env \
  -f docker/compose.infra.yml \
  -f discovery-service/compose.yml \
  -f auth-service/compose.yml \
  -f auth-service/compose.dev.yml \
  -f api-gateway/compose.yml \
  -f api-gateway/compose.dev.yml \
  -f ranking-service/compose.yml \
  up -d --build ranking-service api-gateway
```

Dòng mới so với bài test nền là `-f ranking-service/compose.yml`. Compose tự khởi
động DB/dependency module đã khai báo. Giữ `-p poker-auth-dev` để cùng project auth.
Sau khi chạy, mở **http://localhost:8761**, đợi thấy thêm **RANKING-SERVICE / UP**.

Nếu module lỗi DB/RabbitMQ thì owner sửa cấu hình module trước. Auth dev có thể dùng
credential broker khác fallback module bạn; cấu hình module phải khớp broker đang chạy.
Ranking đọc `RABBITMQ_USER` và `RABBITMQ_PASSWORD`; kiểm tra đúng tên biến của module,
không đoán mọi service đều dùng cùng tên. Không đổi credential broker có dữ liệu để né lỗi.

Ví dụ wallet: owner thay file/service tương ứng **sau khi bỏ hoặc tách broker
RabbitMQ khai báo trùng trong Compose wallet**, đồng thời giữ network/project dùng
chung. Không copy lệnh ranking rồi đổi tên thành wallet và coi là đã xử lý broker.

## Bước 5 — Gọi API qua gateway trước khi nối FE

Chưa login, chạy terminal:

```bash
curl -i 'http://localhost:8080/api/ranking/leaderboard?page=0&size=20'
# Kỳ vọng: 200, vì đây là GET public. Response ranking có content/page/size/...

curl -i http://localhost:8080/api/ranking/me
# Kỳ vọng: 401, vì curl không gửi cookie đăng nhập.
```

Mở Swagger 8080 và login như bài test nền. Sau đó mở **tab khác cùng browser** tới:

```text
http://localhost:8080/api/ranking/me
```

Browser tự gửi access cookie, không cần tự thêm header.
Kỳ vọng HTTP 200, body có `accountId` đúng account login, `elo`, `tier`, v.v.
Ranking trả DTO trực tiếp, không đọc `body.result` hoặc `body.data` cho API này.
Swagger 8080 hiện chỉ có tài liệu auth; không thấy endpoint ranking trong Swagger
không có nghĩa gateway thiếu route. Bạn có thể gọi URL trên hoặc dùng FE/Postman.

Nếu có lỗi nghiệp vụ của ranking, owner chuẩn bị dữ liệu/kiểm tra module của mình.
503 `GATEWAY_UPSTREAM_UNAVAILABLE` thì kiểm tra registry và network; lỗi từ service
thì xem log module. Việc đăng ký Eureka không chứng minh DB/đầu vào nghiệp vụ đã đúng.

## Bước 6 — Tự nối FE feature của bạn

FE đã có client dùng chung tại **`src/shared/api/gatewayClient.js`**, export
`gatewayRequest`. Client tự gửi cookie, gắn CSRF cho API ghi và chia sẻ luồng refresh.
Mỗi owner tạo API adapter trong feature mình, ví dụ:
**`src/features/ranking/api/rankingApi.js`**.

```js
import { gatewayRequest } from '@/shared/api/gatewayClient'

// Ranking trả DTO gốc, gatewayRequest trả nguyên payload.
export function fetchMyRanking() {
  return gatewayRequest('/api/ranking/me')
}

export function fetchLeaderboard(page = 0) {
  return gatewayRequest(`/api/ranking/leaderboard?page=${page}&size=20`, {
    auth: false,
  })
}
```

Màn hình của bạn tự gọi `fetchMyRanking()`, hiển thị loading/error/rỗng và dữ liệu.
Không sửa `gatewayClient` để tự bóc envelope chung cho tất cả module.

| Module/API | Adapter FE đọc dữ liệu |
|---|---|
| Auth/profile/check-in | `payload.result` |
| Shop/wallet | `payload.data` |
| Game/ranking | DTO gốc (`payload`) |
| Module khác | Owner đối chiếu response controller của mình |

Với API ghi, gọi `gatewayRequest(path, { method: 'POST', body: object })`; dùng đúng
endpoint/body của module. Không tự thêm token, cookie, header ID/role hay service key.
Nút ADMIN ẩn/hiện theo auth role; quyền thực tế vẫn được BE kiểm tra.

## Khi nào owner báo đã tích hợp xong?

- Service hiện UP trong Eureka; gateway gọi API module được bằng URL 8080.
- GET bảo vệ: thiếu cookie 401, login đúng thì đúng account; USER không gọi được API ADMIN.
- API ghi (nếu có): thiếu CSRF 403, dùng client chung thì thực hiện đúng nghiệp vụ.
- FE feature của bạn hoạt động bằng account thật và có xử lý loading/error.

Owner tự cập nhật contract/FE của mình và báo kết quả. Khanh hỗ trợ cơ chế chung;
không cần chuyển phần viết adapter/màn hình sang Khanh.

## Tra cứu khi cần

- **Public có danh tính nếu đã login:** thêm `identity: OPTIONAL` vào rule public,
  như shop. Mặc định `IGNORE` bỏ qua access cookie trên API public.
- **ADMIN ngoài `/api/admin/**`:** khai báo `admin-endpoints` với method/paths;
  xem POST/PUT/DELETE danh mục trong `shop-service.yml`.
- **Webhook:** chỉ miễn CSRF bằng `csrf-exempt-endpoints` khi service tự xác minh
  chữ ký. API public bình thường vẫn cần CSRF khi ghi.
- **Auth CSRF:** `csrf-delegated-paths` chỉ dành cho service tự kiểm tra CSRF;
  không copy từ auth nếu module chưa làm cơ chế đó.
- **Thêm service mới:** tạo `<name>.yml` theo schema trên, thêm import
  `classpath:services/<name>.yml` vào danh sách `spring.config.import` gateway;
  route ID phải duy nhất. Đổi YAML/import cần build lại gateway, không có hot reload.
- **Đổi địa chỉ instance:** client đăng ký lại, gateway nhận qua registry/cache;
  không cần sửa route YAML. Eureka không tự start container hoặc nối network.
- **Game:** REST còn cần `GAME_SERVICE_KEY` khớp hai phía; giữ route ID `game-service`.
  WebSocket `/ws/game` chưa proxy qua gateway; STOMP dùng luồng token của game.
- **Điểm danh:** auth gọi wallet trực tiếp bằng `AUTH_WALLET_BASE_URL`; đăng ký
  Eureka không thay đổi lời gọi nội bộ này.
- **Java trên host / nhiều máy:** đây là hướng dẫn Docker cùng máy. Nếu chạy trộn,
  host/IP công bố phải truy cập được từ container gateway; `localhost` là chính
  container. Mặc định registry chỉ publish `127.0.0.1:8761`, chưa mở cho LAN.

Tham khảo chính thức: [Eureka](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html).
