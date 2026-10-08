# Poker-BE

Backend dạng Maven multi-module cho dự án Poker. `game-service` đã có engine, API,
STOMP xác thực JWT, schema PostgreSQL/Flyway, lịch sử và Match Settings.
Các service còn lại và gateway tiếp tục được chủ module tích hợp.

Chạy phần game với PostgreSQL/RabbitMQ Docker theo
[game-service/README.md](game-service/README.md); kế hoạch và kết quả kiểm thử ở
[IMPLEMENTATION_PLAN.md](game-service/IMPLEMENTATION_PLAN.md).

## Yêu cầu

- Java 21
- Maven 3.9+ (hoặc dùng Maven wrapper ở root)
- Docker Compose nếu muốn chạy bằng container

## Cấu trúc

```text
poker-be/
├── pom.xml                 # parent POM, quản lý 11 module
├── common/                 # thư viện dùng chung, giữ thật mỏng
├── api-gateway/            # định tuyến HTTP
├── identity-service/
├── game-service/
├── matchmaking-service/
├── social-service/
├── chat-service/
├── ranking-service/
├── competition-service/
├── shop-service/
├── wallet-service/
├── Dockerfile
└── docker-compose.yml
```

| Module | Người phụ trách | Port | Chức năng dự kiến |
| --- | --- | ---: | --- |
| api-gateway | Khanh | 8080 | Định tuyến request; JWT sẽ triển khai sau |
| identity-service | Khanh | 8081 | Tài khoản, hồ sơ, điểm danh, báo cáo và xử phạt |
| game-service | Bảo | 8082 | Engine poker, bàn chơi, lịch sử trận |
| matchmaking-service | Duy | 8083 | Hàng chờ Rank/Normal, party |
| social-service | Duy | 8084 | Bạn bè, trạng thái online, thông báo |
| chat-service | Duy | 8085 | Chat bạn bè và trong trận |
| ranking-service | Hoài Anh | 8086 | Elo, bậc rank, bảng xếp hạng, mùa giải |
| competition-service | Hoài Anh | 8087 | Custom Room, giải đấu |
| shop-service | Tùng | 8088 | Shop, túi đồ, thanh toán PayOS |
| wallet-service | Tùng | 8089 | Ví xu, lịch sử giao dịch |

## Build và chạy

```powershell
./mvnw.cmd clean verify
./mvnw.cmd -pl identity-service -am spring-boot:run
```

Trên macOS/Linux, dùng `./mvnw` thay cho `./mvnw.cmd`. Khi chạy service riêng lẻ, gateway chuyển tiếp từ `/api/{service}/**` tới service tương ứng trên localhost và bỏ hai segment đầu của path. Các URL đích có thể đổi bằng biến môi trường `IDENTITY_SERVICE_URL`, `GAME_SERVICE_URL`, v.v.

Compose scaffold cho toàn bộ service (cần cấu hình DB/JWT của từng module;
để chạy riêng game, dùng Compose trong hướng dẫn game ở trên):

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Các port public trong `.env` có thể đổi để tránh xung đột trên máy. Gateway có health endpoint tại `http://localhost:8080/actuator/health`; mỗi service cũng có `/actuator/health` trên port của mình. Gateway cần cập nhật route/JWT theo SPEC để nối các endpoint nghiệp vụ game đã triển khai.

## Migration

Mỗi service sở hữu database đặt script Flyway tại `src/main/resources/db/migration/`, ví dụ `identity-service/src/main/resources/db/migration/V1__create_users_table.sql`. Không đặt migration tại `common` hoặc `api-gateway`. Game đã có migration V1; các module còn lại thêm driver/Flyway và migration khi triển khai. Không sửa script đã áp dụng ở môi trường dùng chung; tạo migration phiên bản mới.

## Quy tắc Git

Nhánh chính là `main`. Tạo nhánh riêng cho từng thay đổi, mở pull request và chạy `./mvnw.cmd clean verify` trước khi merge. Không commit `.env`, secret, file IDE hay `target/`.
