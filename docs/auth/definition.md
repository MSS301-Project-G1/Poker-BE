# Định nghĩa phạm vi auth-service và api-gateway

> Cập nhật: 2026-10-06. Chủ sở hữu: Khanh.
>
> Tài liệu này ghi phạm vi đã thống nhất với Khanh và các vấn đề cần debate trước khi triển khai. Không phải tài liệu API đã chạy. Endpoint chi tiết, DTO, status code và lỗi sẽ được bổ sung theo implementation; hợp đồng giữa service phải đồng bộ với [SPEC](../SPEC.md).

## 1. Những điểm đã chốt

- Ưu tiên một luồng hoàn chỉnh: đăng ký → xác thực email → đăng nhập → nhận xu lần đầu → vào sảnh → ghép Rank → chơi → xem kết quả Elo/xu.
- Khanh phụ trách `api-gateway` và **toàn bộ** phạm vi tài khoản trong SPEC: auth, hồ sơ, điểm danh, báo cáo, xử phạt và Admin liên quan; cùng phần hạ tầng/common/khung FE đã phân công.
- Tạo `auth-service` để triển khai phạm vi này. Giữ nguyên module `identity-service` hiện có; không chia đôi hoặc xây trùng nghiệp vụ tài khoản ở hai service. Khanh sẽ thông báo cập nhật cho nhóm.
- `auth-service`: package `com.msspoker.authservice`, port mặc định `8090`, database nghiệp vụ dự kiến `auth_db`. Không thay đổi port của các service khác. Database chưa được tạo.
- Phần phức tạp không phục vụ luồng đầu tiên được làm ở phase sau; vẫn thuộc phạm vi tổng thể của Khanh.
- Chỉ xử lý nghiệp vụ của Khanh. Phần ví, ghép trận, engine, Elo, chat và shop do chủ module quyết định/triển khai; cần gì thì dùng API/event đã thống nhất.
- Đang đầu tuần học 5, deadline tuần học 10. Lịch tích hợp chung còn cần nhóm xác nhận.

## 2. Thứ tự thực hiện

| Ưu tiên | Phạm vi của Khanh | Kết quả cần có |
|---|---|---|
| 1 — phục vụ luồng đầu tiên | Gateway, tài khoản, xác thực email, đăng nhập, phiên/token, phát `account.registered`, thưởng đăng nhập đầu, profiles batch tối thiểu, khung FE/sảnh | Người chơi có tài khoản và token hợp lệ, nhận xu và truy cập các service qua gateway |
| 2 — mở rộng phần tài khoản | Hồ sơ đầy đủ và upload avatar, điểm danh thường/lịch tháng, quên/reset mật khẩu | Người chơi quản lý tài khoản và dùng các chức năng hỗ trợ |
| 3 — phần phức tạp sau luồng chính | Điểm danh bù, báo cáo, xử phạt và Admin tương ứng | Hoàn thiện phạm vi còn lại khi có thời gian và đã chốt nghiệp vụ |

Refresh và logout là một phần của quản lý phiên ở ưu tiên 1. Google login và các ý tưởng có nhãn đề xuất trong SPEC chưa trở thành cam kết triển khai. Thứ tự này không quyết định tiến độ hoặc luật của module khác.

## 3. Quyền sở hữu và ranh giới

### auth-service

- Nguồn dữ liệu tài khoản, mật khẩu, trạng thái xác thực email, vai trò và hồ sơ.
- Quản lý OTP, phiên đăng nhập và refresh token; phát hành access token.
- Theo dõi việc nhận quà lần đăng nhập đầu, điểm danh và lịch điểm danh.
- Tiếp nhận báo cáo, quản lý xử phạt và API Admin thuộc phạm vi tài khoản ở phase sau.
- Phát `account.registered` và `account.penalized` theo hợp đồng SPEC.
- Cung cấp `/internal/accounts/{accountId}/restrictions` và `/api/profiles/batch`.

### api-gateway

- Route HTTP và WebSocket đến đúng service; bảng mục tiêu ở SPEC mục 6.4.
- Kiểm tra access token cho HTTP, xóa header danh tính do client tự gửi và gắn header tin cậy.
- Phân biệt route public, cần đăng nhập và Admin; chặn đường `/internal/**` qua gateway.
- Quản lý CORS HTTP và request ID; mỗi chủ WebSocket cấu hình kiểm tra origin tại service của mình theo danh sách origin chung.
- Không xử lý đăng ký, OTP, hồ sơ, điểm danh, cộng xu hoặc logic poker.

### Dữ liệu từ các service khác

- Xu: gọi wallet; auth không giữ hoặc cập nhật số dư ví.
- Elo/bậc: lấy từ ranking; không coi dữ liệu cache là nguồn sự thật.
- Lịch sử trận: lấy từ game qua API đã thống nhất.
- Skin/avatar frame: dùng component của Tùng ở FE, không tự xây inventory.
- Module `identity-service` được giữ như scaffold; không có DB nghiệp vụ riêng trong kế hoạch hiện tại.

## 4. Gateway và xác thực

### Quy tắc kế thừa SPEC

- FE dùng `Authorization: Bearer <accessToken>` cho HTTP cần đăng nhập.
- Header tin cậy: `X-User-Id`, `X-User-Role`, `X-Request-Id`. Client không được tự chọn danh tính hoặc vai trò.
- Backend chỉ tin header danh tính khi route nghiệp vụ không thể được client gọi trực tiếp. Compose hiện publish port service để dùng scaffold, chưa thực hiện ranh giới này.
- Admin phải được kiểm tra quyền tại gateway và tại chức năng Admin của auth-service.
- WebSocket: FE gửi token trong frame STOMP `CONNECT`; service sở hữu WebSocket tự xác thực. Gateway và các chủ WebSocket phải thống nhất cách handshake và dùng cùng hợp đồng JWT.

### Các quyết định cần debate trước khi triển khai

| Chủ đề | Nội dung cần chốt |
|---|---|
| Hợp đồng JWT | Thuật toán, issuer, audience, `sub` là accountId, claim vai trò, thời hạn và sai lệch đồng hồ cho phép |
| Khóa xác minh | Cách gateway và các service WebSocket lấy khóa xác minh; lưu cấu hình/secret ở đâu |
| Refresh token | Cookie hay body; thời hạn, rotation, xử lý dùng lại token cũ và nhiều request refresh đồng thời |
| Phiên đăng nhập | Đa thiết bị hay một phiên; logout một phiên hay tất cả; tác động của đổi/reset mật khẩu |
| Trạng thái tài khoản | Những trạng thái nào được đăng nhập/refresh; xử lý tài khoản chưa xác thực và bị ban |
| Hết hạn/thu hồi | Access token cũ còn hiệu lực đến khi nào sau logout/reset/ban; WebSocket xử lý token hết hạn thế nào |
| Route và allowlist | Method + path cụ thể được public; không mở public toàn bộ `/api/auth/**` theo prefix |
| CORS và cookie | Origin FE local/deploy, credentials; nếu dùng cookie phải chốt cả chính sách CSRF |
| Lỗi | Phân biệt thiếu/sai token, thiếu quyền và service đích không sẵn sàng; format lỗi theo SPEC |

Không có thuật toán, thời hạn hoặc chính sách lưu token nào trong bảng này được coi là đã chốt.

## 5. Nghiệp vụ tài khoản cần debate

### 5.1. Đăng ký và OTP

Theo luồng SPEC, người chơi đăng ký và xác thực email trước khi đăng nhập. Cần chốt:

- Chuẩn hóa email và xử lý đăng ký lại email đang chờ xác thực.
- Quy tắc mật khẩu, tên hiển thị mặc định và lúc tạo profile.
- Độ dài OTP, thời hạn, giới hạn nhập sai và thời gian chờ gửi lại.
- Gửi lại OTP có làm mã cũ hết hiệu lực không; khi gửi mail lỗi thì trả trạng thái gì.
- `account.registered` phát tại bước nào. **Đề xuất để debate:** phát sau khi xác thực email thành công, đúng luồng 7.1; phải đồng bộ thời điểm này với Tùng và Hoài Anh.

### 5.2. Đăng nhập và quà lần đầu

Giữ quy tắc SPEC: quà được cấp ở lần đăng nhập đầu, không cấp trực tiếp lúc đăng ký. Mức `500 xu` là giá trị đề xuất hiện có; gọi wallet với key `first-login:{accountId}`.

Cần chốt:

- Quà chưa cấp được có chặn đăng nhập không hay đăng nhập vẫn thành công và hiển thị trạng thái chờ nhận quà.
- Khi ví chưa được tạo hoặc wallet timeout, auth lưu dấu vết và tiếp tục cấp quà thế nào.
- Chỉ đánh dấu đã nhận sau khi xác định wallet đã xử lý thành công; gọi lại giữ nguyên key, không tạo key mới.
- Hai lần đăng nhập đồng thời không được nhận hai phần quà.
- Giới hạn thử đăng nhập sai và mức thông tin lỗi trả về.

### 5.3. Hồ sơ

- Phạm vi xem/sửa hồ sơ cá nhân; thông tin nào được trả trong batch công khai cho người đã đăng nhập.
- Quy tắc tên hiển thị, có cần unique không; các field tùy chọn.
- Loại file, dung lượng, nơi lưu avatar và quyền thay/xóa.
- Giới hạn số ID trong profiles batch; xử lý ID không tồn tại và dữ liệu nhạy cảm.
- Cách màn Hồ sơ hiển thị khi game/ranking chưa sẵn sàng, không làm hỏng phần hồ sơ riêng của auth.

### 5.4. Điểm danh

Giữ ngày theo `Asia/Ho_Chi_Minh`, unique `(accountId, checkinDate)` và key `checkin:{yyyy-MM-dd}:{accountId}`. `50 xu/ngày` vẫn là giá trị đề xuất trong SPEC.

Cần chốt gọi lại điểm danh trả kết quả cũ hay lỗi; trạng thái điểm danh khi wallet chưa cộng thưởng; cách xử lý lại mà không bỏ mất thưởng hoặc cộng hai lần. Client không được tự gửi accountId để điểm danh cho người khác hoặc tự chọn ngày cho điểm danh thường.

Điểm danh bù làm sau: phải chốt khoảng ngày cho phép, mức phí/thưởng, điều kiện và cách khôi phục khi đã trừ phí nhưng chưa lưu/cấp thưởng. Không triển khai trước khi debate luồng này.

### 5.5. Quên/reset mật khẩu

Cần chốt cách xác minh email, loại mã/token reset, thời hạn và dùng một lần; phản hồi khi email không tồn tại; chính sách thu hồi phiên sau reset. Quy tắc lưu mật khẩu/OTP/token phải được quyết định trước khi thêm entity và migration.

### 5.6. Báo cáo, xử phạt và Admin

Giữ toàn bộ phạm vi SPEC ở phase sau. Cần debate quyền báo cáo, chống gửi trùng/spam, trạng thái xử lý, thời hạn phạt và ban/unban; quyền Admin không được người đăng ký tự chọn.

Restrictions chưa có xử phạt trả mẫu dự kiến `{ "banned": false, "chatBannedUntil": null, "rankBannedUntil": null }`. Tài khoản không tồn tại không được coi như người chơi hợp lệ. Chính sách HTTP/lỗi cụ thể sẽ chốt khi làm API.

## 6. Trách nhiệm xử lý lỗi của phần Khanh

Các mục dưới đây chỉ mô tả trách nhiệm auth/gateway, không thiết kế lại service của người khác:

- Lưu tài khoản/xác thực thành công nhưng phát event thất bại: cần cơ chế phát lại có dấu vết; lựa chọn cụ thể (ví dụ outbox) còn cần debate. Không coi database commit và gửi RabbitMQ là một transaction chung.
- Cộng quà/điểm danh timeout: lưu trạng thái đủ để tiếp tục xử lý, dùng key ổn định và xác nhận kết quả trước khi đánh dấu hoàn tất.
- Gateway chuyển tiếp lỗi: trả lỗi rõ ràng; không tự retry thao tác thay đổi dữ liệu khi chưa có hợp đồng idempotency phù hợp.
- Consumer bên ngoài xử lý chậm: auth không giả định ví/Elo đã có ngay khi phát event; chốt hành vi FE/API khi dữ liệu phụ thuộc chưa sẵn sàng.
- Các cơ chế retry, pending và job phía auth sẽ được chốt theo từng nghiệp vụ, không mặc định rằng chỉ cần một boolean là đủ.

## 7. Tài liệu API sẽ bổ sung khi triển khai

Theo từng chức năng, ghi method/path, public hay cần quyền, request/response JSON, validation, status/lỗi, tác động dữ liệu và hành vi khi gọi lại. Tài liệu phải phản ánh API thực tế; route/DTO đề xuất chưa dùng làm hợp đồng FE cuối cùng.

Nhóm route mục tiêu của auth vẫn là `/api/auth/**`, `/api/profiles/**`, `/api/checkin/**`, `/api/reports/**`, `/api/admin/users/**`, `/api/admin/reports/**`. API nội bộ và event giữ tên trong SPEC; cập nhật bên cung cấp từ identity sang auth cần được nhóm xác nhận.

## 8. Trạng thái triển khai hiện tại

- Đã có scaffold `auth-service`: POM, Spring Boot entry point, cấu hình health, test khởi động context và thư mục migration trống.
- Đã đăng ký module ở parent POM và thêm cấu hình Compose/biến môi trường; Docker chưa được khởi chạy cho cập nhật này.
- Gateway có route **scaffold** `/api/auth-service/**` và `StripPrefix=2`, giống kiểu scaffold hiện tại. Chưa route các nhóm nghiệp vụ mục tiêu, chưa xác thực JWT.
- Chưa có controller, nghiệp vụ, schema, JWT, OTP, mail, cơ chế thưởng hoặc điểm danh.
- `identity-service` giữ nguyên nội dung. Không có bước migrate dữ liệu vì chưa có schema nghiệp vụ.

Nguồn tham khảo cho bước thiết kế tiếp theo: [Spring STOMP token authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html). Đây là tài liệu kỹ thuật, không chốt thay các chính sách nghiệp vụ phía trên.
