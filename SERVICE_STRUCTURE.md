# Cấu trúc Social, Chat và Matchmaking

Ba service theo convention của Competition/Ranking: interface nghiệp vụ ở
`service/`, implementation ở `service/impl/`. Giữ nguyên endpoint, DTO và schema.

## Các package chung

```text
config/          Cấu hình Spring, thời gian và tích hợp
constant/        Hằng số
controller/      REST transport
dto/             request/, response/ và event/ khi có
entity/          JPA entity ánh xạ bảng do service sở hữu
exception/       Lỗi nghiệp vụ và exception handler
mapper/          Mapping entity/model sang DTO
repository/      Spring Data JPA, @Query, @Modifying và khóa dữ liệu
security/        Kiểm tra ingress/session
service/         Interface nghiệp vụ
service/impl/    Implementation và transaction
```

`enums/`, `validator/`, `websocket/`, `model/`, `util/` chỉ tồn tại khi service
có code sử dụng. REST/STOMP controller gọi service; không xử lý SQL tại controller.

## Social

```text
service/
├── SocialService.java
├── NotificationOutbox.java
├── event/OutboxPublisher.java
└── impl/
    ├── SocialServiceImpl.java
    ├── NotificationOutboxImpl.java
    └── event/
        ├── OutboxPublisherImpl.java
        ├── NotificationConsumer.java
        └── NotificationDelivery.java
```

`RelationLockRepository` tạo và khóa dòng theo cặp account trong transaction.
`EventReceiptRepository` claim event bằng insert chống trùng. SQL nằm trực tiếp
trong interface repository, không có bộ ba `Repository`/`Custom`/`CustomImpl`.

## Chat

```text
service/
├── ChatService.java
├── MatchLifecycleService.java
├── client/SocialClient.java
└── impl/
    ├── ChatServiceImpl.java
    ├── MatchLifecycleServiceImpl.java
    ├── client/SocialClientImpl.java
    └── event/
        ├── MatchEventConsumer.java
        └── MessageDelivery.java
```

- `ChatLockRepository` chỉ thao tác bảng `chat_locks`.
- `MatchLifecycleRepository` và entity `MatchLifecycle` ánh xạ bảng
  `match_lifecycle` hiện có. Khóa dòng trước khi xử lý lifecycle; giữ lần finish
  đầu tiên và tạo phòng read-only nếu finish đến trước start.
- `EventReceiptRepository` claim event trong cùng transaction với lifecycle.
- Giữ `ChatService` hiện tại cho conversation/message; việc đổi package không
  thay đổi hợp đồng REST, STOMP hay quyền truy cập lịch sử.

## Matchmaking

```text
service/
├── *Service.java, *Coordinator.java
├── client/                 Interface gọi Game, Wallet, Ranking, Restriction
├── store/                  QueueStore, PartyStore, PresenceStore
└── impl/
    ├── *ServiceImpl.java, *CoordinatorImpl.java
    ├── client/http/        HttpGameClient, HttpWalletClient, ...
    ├── client/stub/        StubGameClient, StubWalletClient, ...
    ├── store/              RedisQueueStore, RedisPartyStore, RedisPresenceStore
    └── job/                Finder, recovery, expiry, abandoned-player workers
```

| Package cũ dưới `service/` | Package mới dưới `service/` |
|---|---|
| `normal/`, `rank/`, `party/`, `presence/`, `proposal/` (interface) | package gốc |
| `normal/impl/`, `rank/impl/`, `party/impl/`, `presence/impl/`, `proposal/impl/` | `impl/` |
| `normal/worker/`, `rank/worker/`, `presence/worker/` | `impl/job/` |
| `client/http/`, `client/stub/` | `impl/client/http/`, `impl/client/stub/` |
| `store/redis/` | `impl/store/` |

Test được chuyển package tương ứng với implementation. Interface client/store
giữ nguyên nhóm riêng. Lịch chạy worker, Redis key, Lua và transaction không đổi.

## Kiểm tra

Yêu cầu JDK 21. Compile sạch cả source và test:

```powershell
.\mvnw.cmd -pl social-service,chat-service,matchmaking-service -am clean test-compile
```

Full suite cần Docker cho PostgreSQL/RabbitMQ/Redis Testcontainers:

```powershell
.\mvnw.cmd -pl social-service,chat-service,matchmaking-service -am verify
```

Để kiểm tra repository khi không có Docker, tạo **hai database test riêng**
`social_structure`, `chat_structure` trên PostgreSQL 17 với role `structure_test`.
Không dùng database ứng dụng. `PostgresRepositoryTests` chỉ bật khi truyền URL
prefix, ví dụ một PostgreSQL test ở port 15487:

```powershell
.\mvnw.cmd -pl social-service,chat-service -am test '-Dtest=PostgresRepositoryTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstructure.test.postgres-url=jdbc:postgresql://127.0.0.1:15487/'
```

Flyway áp dụng migration nguyên bản lên database test. Rabbit listener/publisher
và Eureka bị tắt trong các test này; Social HTTP client của Chat được mock.
Các test kiểm tra concurrency của khóa, dedup message/event, rollback và lifecycle.
Chúng không thay thế kiểm tra RabbitMQ/WebSocket end-to-end trong full suite.
