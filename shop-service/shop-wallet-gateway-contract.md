# Hợp đồng API Gateway — Shop Service & Wallet Service

Tài liệu bàn giao cho Gateway (Khanh) để cấu hình routing và phân quyền cho **Shop Service** và **Wallet Service**.

---

## ⚠️ Lưu ý kỹ thuật quan trọng cho Gateway Dev

1. **KHÔNG dùng `StripPrefix=2`:**
   - Các controller của `shop-service` và `wallet-service` đã khai báo đầy đủ prefix (ví dụ: `/api/shop/**`, `/api/wallet/**`).
   - Nếu để `StripPrefix=2`, request `/api/shop/products` sẽ bị cắt thành `/products` dẫn tới lỗi **404 Not Found**.
   - Cần cấu hình giữ nguyên path như cách làm với `game-service`.

2. **Cập nhật Path Predicates cho `shop-service`:**
   - `shop-service` đảm nhiệm nhiều nhóm tính năng (shop, inventory, payments, admin, sandbox).
   - Gateway cần match các prefix:
     ```text
     /api/shop/**, /api/inventory/**, /api/payments/**, /api/admin/products/**, /api/admin/bundles/**, /api/admin/payments/**, /api/sandbox/**
     ```

3. **Xử lý Webhook PayOS (`POST /api/payments/payos/webhook`):**
   - Webhook được PayOS gọi trực tiếp từ internet: **không có cookie JWT và không có token CSRF**.
   - Cần: Thêm vào `PUBLIC_POST` và cấu hình bỏ qua kiểm tra CSRF trong `CookieCsrfFilter`. Chữ ký webhook được `shop-service` tự kiểm tra bằng HMAC secret.

4. **API Internal (`/internal/**`):**
   - Là API nội bộ giữa các microservices (Game, Wallet, Shop, Ranking) gọi qua Docker network.
   - **Gateway tiếp tục chặn toàn bộ `/internal/**` đối với client ngoài.**

---

## 1. SHOP-SERVICE (Port nội bộ: 8088)

### 🌐 Nhóm 1: Public / Anonymous (Không cần login)
> Khách vãng lai xem danh sách sản phẩm, webhook thanh toán.

| Method | Path FE gọi | Path Controller | Quyền Gateway | Mô tả |
|---|---|---|---|---|
| `GET` | `/api/shop/products` | `/api/shop/products` | **Public** | Danh sách sản phẩm (hỗ trợ kèm `X-User-Id` nếu đã login để biết đã sở hữu chưa) |
| `GET` | `/api/shop/products/{id}` | `/api/shop/products/{id}` | **Public** | Xem chi tiết 1 sản phẩm |
| `GET` | `/api/shop/categories` | `/api/shop/categories` | **Public** | Danh sách danh mục sản phẩm |
| `GET` | `/api/shop/bundles` | `/api/shop/bundles` | **Public** | Danh sách các gói nạp VIP Coin |
| `POST` | `/api/payments/payos/webhook` | `/api/payments/payos/webhook` | **Public** | Webhook từ PayOS (cần miễn CSRF) |

---

### 👤 Nhóm 2: Authenticated User (Cần login — Gateway truyền `X-User-Id`)
> Yêu cầu User đã đăng nhập, Gateway xác thực JWT cookie và gắn `X-User-Id` vào header.

| Method | Path FE gọi | Path Controller | Quyền Gateway | Nghiệp vụ |
|---|---|---|---|---|
| `POST` | `/api/shop/products/{id}/purchase` | `/api/shop/products/{id}/purchase` | **User** | Mua vật phẩm bằng VIP Coin |
| `GET` | `/api/inventory/me` | `/api/inventory/me` | **User** | Xem túi đồ của người chơi |
| `GET` | `/api/inventory/{productId}` | `/api/inventory/{productId}` | **User** | Xem chi tiết vật phẩm trong túi |
| `PUT` | `/api/inventory/{productId}/equip` | `/api/inventory/{productId}/equip` | **User** | Trang bị skin/vật phẩm |
| `DELETE` | `/api/inventory/{productId}/equip` | `/api/inventory/{productId}/equip` | **User** | Tháo trang bị |
| `POST` | `/api/payments/orders` | `/api/payments/orders` | **User** | Tạo đơn hàng nạp PayOS |
| `POST` | `/api/payments/bundles/{bundleId}/orders` | `/api/payments/bundles/{bundleId}/orders` | **User** | Tạo đơn mua gói nạp qua PayOS |
| `GET` | `/api/payments/orders/me` | `/api/payments/orders/me` | **User** | Lịch sử nạp tiền của tôi |
| `GET` | `/api/payments/orders/{orderId}` | `/api/payments/orders/{orderId}` | **User** | Chi tiết đơn thanh toán |
| `PUT` | `/api/payments/orders/{orderId}/cancel` | `/api/payments/orders/{orderId}/cancel` | **User** | Hủy đơn thanh toán chờ |
| `POST` | `/api/payments/orders/{orderId}/sync` | `/api/payments/orders/{orderId}/sync` | **User** | Đồng bộ thủ công trạng thái PayOS |
| `POST` | `/api/sandbox/bundles/{bundleId}/purchase` | `/api/sandbox/bundles/{bundleId}/purchase` | **User** | Mua gói xu sandbox (chỉ mở ở non-prod) |

---

### 👑 Nhóm 3: Admin (Yêu cầu Role `ADMIN`)
> Gateway kiểm tra role `ADMIN` trong JWT claim.

| Method | Path FE gọi | Path Controller | Quyền Gateway | Nghiệp vụ |
|---|---|---|---|---|
| `POST` | `/api/shop/categories` | `/api/shop/categories` | **ADMIN** | Tạo danh mục mới |
| `PUT` | `/api/shop/categories/{id}` | `/api/shop/categories/{id}` | **ADMIN** | Cập nhật danh mục |
| `DELETE` | `/api/shop/categories/{id}` | `/api/shop/categories/{id}` | **ADMIN** | Xóa danh mục |
| `POST` | `/api/admin/products` | `/api/admin/products` | **ADMIN** | Thêm sản phẩm mới |
| `PUT` | `/api/admin/products/{id}` | `/api/admin/products/{id}` | **ADMIN** | Cập nhật sản phẩm |
| `DELETE` | `/api/admin/products/{id}` | `/api/admin/products/{id}` | **ADMIN** | Xóa sản phẩm |
| `POST` | `/api/admin/products/{id}/deck-skin/upload-zip` | `/api/admin/products/{id}/deck-skin/upload-zip` | **ADMIN** | Upload file ZIP 53 lá skin bài |
| `POST` | `/api/admin/products/{id}/deck-skin/upload-files` | `/api/admin/products/{id}/deck-skin/upload-files` | **ADMIN** | Upload danh sách ảnh skin bài |
| `GET` | `/api/admin/bundles` | `/api/admin/bundles` | **ADMIN** | Danh sách tất cả gói nạp |
| `GET` | `/api/admin/bundles/{id}` | `/api/admin/bundles/{id}` | **ADMIN** | Chi tiết gói nạp |
| `POST` | `/api/admin/bundles` | `/api/admin/bundles` | **ADMIN** | Tạo gói nạp VIP Coin mới |
| `PUT` | `/api/admin/bundles/{id}` | `/api/admin/bundles/{id}` | **ADMIN** | Cập nhật gói nạp |
| `PATCH` | `/api/admin/bundles/{id}/toggle` | `/api/admin/bundles/{id}/toggle` | **ADMIN** | Bật/tắt mở bán gói nạp |
| `DELETE` | `/api/admin/bundles/{id}` | `/api/admin/bundles/{id}` | **ADMIN** | Xóa gói nạp |
| `GET` | `/api/admin/payments/methods` | `/api/admin/payments/methods` | **ADMIN** | Danh sách cấu hình cổng thanh toán |
| `GET` | `/api/admin/payments/methods/active` | `/api/admin/payments/methods/active` | **ADMIN** | Xem cổng thanh toán đang active |
| `PUT` | `/api/admin/payments/methods/{id}/switch` | `/api/admin/payments/methods/{id}/switch` | **ADMIN** | Kích hoạt cổng thanh toán |
| `POST` | `/api/admin/payments/methods` | `/api/admin/payments/methods` | **ADMIN** | Thêm cấu hình cổng thanh toán |
| `PUT` | `/api/admin/payments/methods/{id}` | `/api/admin/payments/methods/{id}` | **ADMIN** | Sửa cấu hình cổng thanh toán |

---

### 🔒 Nhóm 4: Internal / Private (Microservices gọi nhau, KHÔNG QUA GATEWAY)
| Method | Path | Gọi từ đâu? | Mục đích |
|---|---|---|---|
| `GET` | `/internal/inventory/{accountId}/owns/{productId}` | Game / Social | Kiểm tra người chơi có sở hữu skin bài / vật phẩm hay không |
| `POST` | `/internal/inventory/grant` | Game / Admin | Cấp vật phẩm trực tiếp (quà sự kiện, đua top) |

---

## 2. WALLET-SERVICE (Port nội bộ: 8089)

### 👤 Nhóm 1: Authenticated User (Cần login — Gateway truyền `X-User-Id`)

| Method | Path FE gọi | Path Controller | Quyền Gateway | Nghiệp vụ |
|---|---|---|---|---|
| `GET` | `/api/wallet/me` | `/api/wallet/me` | **User** | Xem thông tin và số dư ví cá nhân |
| `GET` | `/api/wallet/me/transactions` | `/api/wallet/me/transactions` | **User** | Lịch sử biến động số dư (phân trang) |

---

### 🔒 Nhóm 2: Internal / Private (Microservices gọi nhau, KHÔNG QUA GATEWAY)
| Method | Path | Gọi từ đâu? | Mục đích |
|---|---|---|---|
| `POST` | `/internal/wallet/credit` | Game / Shop | Cộng xu vào ví (thắng ván bài, nạp tiền thành công) |
| `POST` | `/internal/wallet/debit` | Game / Shop | Trừ xu từ ví (mua đồ shop, đặt cược ván bài) |
| `GET` | `/internal/wallet/{accountId}/balance` | Game / Matchmaking | Kiểm tra nhanh số dư tài khoản |

---

## 📋 Mẫu cấu hình Gateway đề xuất để copy paste

### 1. `api-gateway/src/main/resources/application.yml`
```yaml
            - id: shop-service
              uri: ${SHOP_SERVICE_URL:http://localhost:8088}
              predicates:
                - Path=/api/shop/**,/api/inventory/**,/api/payments/**,/api/admin/products/**,/api/admin/bundles/**,/api/admin/payments/**,/api/sandbox/**

            - id: wallet-service
              uri: ${WALLET_SERVICE_URL:http://localhost:8089}
              predicates:
                - Path=/api/wallet/**
```

### 2. `api-gateway/src/main/java/com/msspoker/apigateway/shared/config/GatewaySecurityConfig.java`
```java
    private static final String[] PUBLIC_GET = {
            "/api/auth/csrf", "/.well-known/jwks.json", "/actuator/health",
            "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            // Shop public APIs:
            "/api/shop/products", "/api/shop/products/**", "/api/shop/categories", "/api/shop/bundles"
    };

    private static final String[] PUBLIC_POST = {
            "/api/auth/register", "/api/auth/register/verify-otp",
            "/api/auth/register/resend-otp", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            // Webhook PayOS (không cần JWT token):
            "/api/payments/payos/webhook"
    };
```
*(Lưu ý: Route `/api/payments/payos/webhook` cần được loại trừ kiểm tra CSRF trong `CookieCsrfFilter`)*
