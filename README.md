# Poker-BE

Backend dạng Maven multi-module cho dự án Poker. Trạng thái từng phần xem README của module: [game-service](game-service/README.md), [auth-service](auth-service/README.md). Auth đã có nền tảng database ở phase 1; API auth/JWT chưa triển khai.

## Yêu cầu

- Java 21
- Maven 3.9+ (hoặc dùng Maven wrapper ở root)
- Docker Compose nếu muốn chạy bằng container

## Cấu trúc

```text
poker-be/
├── pom.xml                 # parent POM, quản lý 12 module
├── common/                 # thư viện dùng chung, giữ thật mỏng
├── api-gateway/            # định tuyến HTTP
├── auth-service/           # phần tài khoản, hồ sơ, điểm danh của Khanh
├── identity-service/       # giữ nguyên scaffold, không nhận nghiệp vụ mới
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
| auth-service | Khanh | 8090 | Tài khoản, xác thực, hồ sơ, điểm danh, báo cáo và xử phạt |
| identity-service | Khanh | 8081 | Scaffold giữ nguyên, không nhận nghiệp vụ mới |
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
./mvnw.cmd -pl auth-service -am install -DskipTests
./mvnw.cmd -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local
```

Trên macOS/Linux, dùng `./mvnw` thay cho `./mvnw.cmd`. Auth cần PostgreSQL trước khi chạy Java; làm theo [hướng dẫn chạy auth riêng](auth-service/README.md). Khi chạy service riêng lẻ, gateway chuyển tiếp từ các route scaffold tới service tương ứng trên localhost và bỏ hai segment đầu của path. Riêng scaffold mới dùng `/api/auth-service/**` với `AUTH_SERVICE_URL` (mặc định `http://localhost:8090`); đây chưa phải route nghiệp vụ `/api/auth/**`. Các URL đích khác có thể đổi bằng `IDENTITY_SERVICE_URL`, `GAME_SERVICE_URL`, v.v. Bảng route nghiệp vụ mục tiêu nằm trong [SPEC](docs/SPEC.md#64-bảng-route-public-qua-gateway).

Chạy toàn bộ bằng Docker:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Các port public trong `.env` có thể đổi để tránh xung đột trên máy. Gateway có health endpoint tại `http://localhost:8080/actuator/health`; mỗi service cũng có `/actuator/health` trên port của mình. Auth chưa có endpoint nghiệp vụ nên các path auth hiện trả về 404. Root Compose có DB riêng cho auth; hạ tầng và cấu hình riêng của game xem README game trước khi chạy cả nhóm.

## Migration

Mỗi service sở hữu database đặt script Flyway tại `src/main/resources/db/migration/`. Auth đã có `V1__create_accounts_and_profiles.sql`, driver PostgreSQL và Flyway; game cũng quản lý migration riêng. Không đặt migration tại `common` hoặc `api-gateway`. Không sửa script đã áp dụng ở môi trường dùng chung; tạo migration phiên bản mới.

## Phạm vi hiện tại

Ưu tiên một luồng Rank hoàn chỉnh trước rồi mở rộng các phần còn lại. Khanh phát triển toàn bộ phạm vi tài khoản trong `auth-service`; `identity-service` giữ nguyên và không triển khai nghiệp vụ trùng lặp. Xem [SPEC](docs/SPEC.md), [định nghĩa auth/gateway](docs/auth/definition.md) và [kế hoạch phase](docs/auth/implementation-plan.md) để phân biệt phần đã chốt với các quyết định còn cần debate. DTO và hợp đồng FE chi tiết sẽ được mô tả cùng lúc triển khai API auth.

## Quy tắc Git

Nhánh chính là `main`. Tạo nhánh riêng cho từng thay đổi, mở pull request và chạy `./mvnw.cmd clean verify` trước khi merge. Không commit `.env`, secret, file IDE hay `target/`.
