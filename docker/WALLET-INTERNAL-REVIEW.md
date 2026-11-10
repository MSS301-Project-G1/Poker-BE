# Phần 1: bảo vệ HTTP nội bộ wallet

Chỉ xử lý xác thực bên gọi `/internal/**` của wallet. Chưa sửa lỗi tin header ở
`/api/wallet/**`, các service khác hoặc API nội bộ shop; review/commit riêng phần này trước.

## Hợp đồng

- Wallet và 5 caller (auth, shop, matchmaking, ranking, competition) dùng cùng biến BE `WALLET_INTERNAL_KEY`.
- Mọi HTTP request nội bộ wallet cần header `X-Wallet-Internal-Key` khớp key đã cấu hình.
- Thiếu/sai key, nhiều header key, hoặc wallet chưa cấu hình key: 403 `WALLET_FORBIDDEN`, trước controller/nghiệp vụ.
- Filter đăng ký theo servlet URL `/internal` và `/internal/*`, bảo vệ cả credit/debit, balance và tra giao dịch.
- Key chỉ chứng minh bên gọi thuộc nhóm backend tin cậy, chưa phân quyền theo từng service/lý do giao dịch.
  Không đưa key vào FE, query URL hoặc log. Gateway loại header này nếu client tự gửi.
- Giữ body, response, idempotency key và các quy tắc tiền hiện có. Không sửa migration hoặc dữ liệu.
- Swagger wallet có Authorize → walletInternalKey cho nhóm API nội bộ. Không gọi credit/debit trên ví thật để thử key.

Các thay đổi caller chỉ thêm header trên request ví: auth điểm danh; matchmaking phí/hoàn phí;
ranking thưởng; competition phí/thưởng/hoàn phí; shop debit/credit và GET tra giao dịch.
Các cờ mock/stub hiện có được giữ nguyên. Không có thay đổi FE.

## Cấu hình local

`.env.example` thêm `WALLET_INTERNAL_KEY=`. `.env` hiện tại đã được bổ sung giá trị ngẫu nhiên nếu chưa có biến;
không in giá trị vào docs. Thành viên khác điền một secret ngẫu nhiên, dùng cùng giá trị cho cả 6 service.
Compose yêu cầu biến không rỗng; app Java ngoài Docker đọc cùng biến từ env của tiến trình/IDE.

Không chạy riêng wallet bản mới với caller bản cũ: caller chưa gửi key sẽ bị 403.
Sau review, từ root BE có thể cập nhật cả nhóm cùng lúc:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build wallet-service auth-service shop-service matchmaking-service ranking-service competition-service api-gateway
```

Không cần `down -v`. Lệnh trên dành cho người chạy sau review; agent chưa rebuild/restart stack.

## Kiểm tra

- Test filter chặn cả đọc/ghi nội bộ khi thiếu/sai key hoặc giả header user/ADMIN; kiểm tra key rỗng và trùng header.
- Đúng key được đi tiếp tới controller cho 4 endpoint, public wallet nằm ngoài filter này.
- Kiểm tra request từ đủ 5 client mang đúng header; hồi quy lỗi/contract ví và thưởng điểm danh.
- HTTP client dùng fixture, auth dùng PostgreSQL Testcontainers tạm; không cộng/trừ tiền thật.
- Compose config phải hợp lệ với `.env`; chưa nghiệm thu toàn bộ các service với wallet thật.

Lưu ý review: public wallet vẫn nhận `X-User-Id` như trước và sẽ được bảo vệ ở phần xác minh gateway sau.
Shop hiện có một số cách xử lý lỗi/response ví riêng; phần này chỉ thêm xác thực, chưa thay các hợp đồng đó.
