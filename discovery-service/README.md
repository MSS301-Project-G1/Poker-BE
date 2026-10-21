# discovery-service

Eureka Server, port mặc định **8761**. Dashboard: <http://localhost:8761>.
Service không có DB và không nằm trên đường truyền request nghiệp vụ.

Chạy Java từ root:

```bash
mvn -pl discovery-service spring-boot:run
```

Chạy Docker riêng từ root:

```bash
docker compose -f discovery-service/compose.yml up -d --build discovery-service
```

Khi chạy cùng auth/gateway, dùng bộ Compose và project hiện có trong
[hướng dẫn gateway](../api-gateway/TESTING.md).
Không chạy riêng rồi nghĩ container ở project khác sẽ tự cùng network.

Owner đọc [hướng dẫn tự tích hợp BE/FE](../api-gateway/README.md).
