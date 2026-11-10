# Phần 3: xác minh gateway trước khi đọc ví

Phạm vi lần này là `/api/**` của wallet. Dừng để review/commit trước khi bảo vệ
gateway ingress cho shop, matchmaking, ranking và competition.

## Rà lại hai commit trước

Đã đọc lại `31a40dc` (wallet internal) và `2ffa0d9` (shop internal), đối chiếu
endpoint, các HTTP caller hiện có, property và Compose. Chưa thấy lỗi mới làm
bỏ qua xác thực internal key trong phạm vi hai commit này:

- Filter chạy trước controller; thiếu/sai/trùng key hoặc cấu hình rỗng bị từ chối.
- Wallet có đủ key ở auth, shop, matchmaking, ranking, competition.
- Shop có đủ key ở game, ranking, competition; chưa có HTTP caller cho endpoint `owns`.
- Gateway loại internal key do client gửi. Key nội bộ không thay thế danh tính user.
- Đây vẫn là key dùng chung cho các backend được tin cậy, chưa phân quyền theo từng caller.

Các điểm xử lý lỗi đã tồn tại từ trước hai commit, cần xem riêng với owner service:

1. `shop-service/.../RestWalletClientImpl.java`: mọi HTTP error khi debit đều bị
   chuyển thành lỗi thiếu tiền. Vì vậy sai internal key trả 403 cũng có thể bị
   báo thành thiếu tiền, dù wallet đã chặn giao dịch. Nên chỉ ánh xạ đúng mã
   thiếu số dư; lỗi xác thực/kết nối phải là lỗi dịch vụ.
2. `game-service/.../ShopSoundClient.java`: mọi 4xx của shop bị coi là chưa sở hữu
   âm thanh. Sai internal key cũng bị báo như vậy. Nên tách lỗi xác thực backend
   khỏi lỗi sở hữu vật phẩm. Không có đường bỏ qua kiểm tra key vì việc này.
3. Hàm `findDebitAmountByIdempotencyKey` của shop đọc trường `result` trong khi
   envelope wallet dùng `data`, và trả empty cho cả một số lỗi upstream. Chưa có
   nơi gọi hàm này trong source nghiệp vụ hiện tại; cần sửa hợp đồng trước khi
   dùng nó để quyết định giao dịch/hoàn tiền.

Lần này chưa thay các nhánh nghiệp vụ trên. Không coi test gửi đúng header là
bằng chứng rằng mọi cách xử lý lỗi tài chính của các service đã đúng.

## Luồng mới

1. FE vẫn gọi gateway: GET `/api/wallet/me` hoặc `/api/wallet/me/transactions`.
   FE gửi cookie/tab header theo luồng hiện tại, không giữ secret gateway.
2. Gateway xác thực JWT và phiên theo cấu hình hiện có; bỏ header danh tính/key
   do client tự gửi, rồi gắn `X-User-Id`/`X-User-Role` đã xác thực.
3. Chỉ route có ID `wallet-service` được gateway gắn `X-Wallet-Gateway-Key`.
   Thiếu cấu hình key ở gateway trả 503 `GATEWAY_UPSTREAM_UNAVAILABLE`.
4. Wallet kiểm tra key trước controller. Thiếu/sai/trùng key hoặc cấu hình rỗng
   trả 403 `WALLET_FORBIDDEN`. Tự gọi port 8089 kèm ID/role giả không đọc được ví.
5. Đúng key mới tới controller/nghiệp vụ; response, query phân trang giữ nguyên.

Servlet filter đăng ký cho `/api` và `/api/*`. Docs/health ngoài `/api` không nằm
trong filter này. Swagger gọi thẳng public wallet API cũng cần gateway ingress;
FE không được nhận key để gọi thẳng wallet.

Hai loại key độc lập:

| Biến | Bên giữ | Header | Phạm vi |
| --- | --- | --- | --- |
| `WALLET_GATEWAY_KEY` | gateway, wallet | `X-Wallet-Gateway-Key` | wallet `/api/**` |
| `WALLET_INTERNAL_KEY` | wallet và các backend caller | `X-Wallet-Internal-Key` | wallet `/internal/**` |

Dùng giá trị ngẫu nhiên khác nhau cho hai biến. Có key nội bộ không thay được key
gateway và ngược lại. Gateway không chuyển key gateway wallet sang route khác.
Không thay contract debit/credit, quyền chi tiền hoặc idempotency trong phần này.

## Cấu hình và chạy sau review

`.env.example` thêm `WALLET_GATEWAY_KEY=`. `.env` local đã được bổ sung giá trị;
gateway và wallet dùng cùng giá trị qua Compose. Không đưa key vào FE/query/log.
Thành viên pull code cần bổ sung biến này trong `.env` trước khi chạy Compose.
Chạy Java từ IDE thì đặt biến trong environment của tiến trình.

Cập nhật gateway và wallet cùng lúc, từ root BE:

```bash
docker compose -p poker-auth-dev --env-file .env up -d --build api-gateway wallet-service
```

Không cần xóa DB/volume. Agent chưa rebuild hoặc restart stack đang dùng.

## Xác minh

- 43 test wallet/gateway pass: request thiếu/sai/trùng/rỗng key; giả danh trực tiếp;
  đúng key đi tiếp; hai loại key không thay nhau; gateway bỏ key giả rồi gắn key thật;
  không rò key sang route khác; thiếu key gateway trả 503; hồi quy gateway hiện có.
- Chạy lại 17 test filter/controller shop và HTTP caller của hai commit internal:
  đều pass, không gọi giao dịch tài chính trên stack thật.
- Build/package 8 module bị ảnh hưởng, gồm compile test source; kiểm tra Compose
  bằng `.env` và `git diff --check` trước khi bàn giao.
- Controller/service trong test wallet được mock, các upstream dùng HTTP fixture.
  Chưa nghiệm thu FE hoặc gọi wallet thật trên stack Docker đang chạy.
