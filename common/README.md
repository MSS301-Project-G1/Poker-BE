# common

Thư viện dùng chung cho các kiểu dữ liệu hoặc tiện ích thực sự được nhiều service sử dụng.
Không đặt entity, repository hay nghiệp vụ của một service vào module này.

Ngoại lệ theo SPEC: `BaseEntity` là mapped superclass dùng chung (UUID v7,
`Instant`, `is_deleted`), không phải entity nghiệp vụ. `ErrorResponse` định nghĩa
JSON lỗi `code`, `message`, `timestamp`, `requestId`. Module không kéo Spring Boot
starter vào các service dùng nó. Jakarta Persistence, Hibernate và Spring Data JPA
là dependency `provided` cho mapping, UUID và auditing; service dùng BaseEntity
phải cung cấp JPA runtime và bật `@EnableJpaAuditing`. Lombok chỉ dùng lúc compile.
