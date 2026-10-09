# Kế hoạch triển khai phần Khanh

> Cập nhật: 2026-10-10. Phase 1 đã triển khai và kiểm tra trên PostgreSQL 17 thật; chờ review. Chưa triển khai API nghiệp vụ.
>
> Nguồn: [SPEC](../SPEC.md), [definition](definition.md), [câu trả lời debate](debate/debate.md) và cấu trúc auth-service đã commit tại `e6f5f1f`.

## 1. Phạm vi và cách làm

- Sở hữu `auth-service`, `api-gateway`, phần common/hạ tầng và khung FE đã phân công trong SPEC. Giữ nguyên scaffold `identity-service`.
- Ưu tiên một luồng hoàn chỉnh trước; profile, điểm danh và phần nâng cao vẫn nằm trong phạm vi tổng thể.
- Các phase dưới đây là đợt công việc của Khanh, không đổi phân công hoặc lịch của thành viên khác. Không tương đương trực tiếp với nhãn MVP/GĐ2 của SPEC.
- Mỗi phase cần có kết quả có thể kiểm tra; cập nhật tài liệu API theo code thực tế. Chỉ đánh dấu xong khi kiểm tra tương ứng đã đạt.
- Mỗi phase có thể chia nhiều commit nhỏ. Backend và FE ở hai repo; kiểm chứng BE không đồng nghĩa màn hình FE đã hoàn thành. Khi làm FE phải đọc cấu trúc repo FE thực tế trước.
- Các câu đã được trả lời trong debate hoặc xác nhận ở definition không hỏi lại. Chỉ bàn những chi tiết còn mở khi bắt đầu đúng chức năng đó; không mở thêm debate nghiệp vụ của ví/game/ranking.

## 2. Tổng quan các phase

| Phase | Nội dung | Kết quả kiểm tra được | Trạng thái |
|---|---|---|---|
| 0 | Scaffold và cây package | Cấu trúc auth-service đã có trong Git | Đã xong phần cấu trúc |
| 1 | Nền tảng auth và database | Kết nối `auth_db`, migration và format lỗi hoạt động | Đã code/test PostgreSQL 17; chờ review |
| 2 | Đăng ký và OTP | Đăng ký → nhận mail → xác thực email | Chưa làm |
| 3 | Login, refresh, logout | Duy trì đăng nhập và logout đúng thiết bị | Chưa làm |
| 4 | Gateway và tích hợp luồng đầu tiên | Auth dùng qua gateway, game nhận đúng danh tính; nối luồng Rank với nhóm | Chưa làm |
| 5 | Hồ sơ và khôi phục tài khoản | Xem/sửa hồ sơ, avatar, quên/reset mật khẩu; đổi mật khẩu là chức năng riêng | Chưa làm |
| 6 | Điểm danh thường | Điểm danh/lịch tháng, thưởng cố định một lần mỗi ngày | Chưa làm |
| 7 | Phần nâng cao | Điểm danh bù, báo cáo, xử phạt, Admin | Để sau luồng chính |

## Phase 0 — Scaffold và cây package

- [x] Module `auth-service`, package gốc `com.msspoker.authservice`, port 8090.
- [x] Đăng ký module ở Maven và cấu hình Compose scaffold.
- [x] Cây package đã commit: controller, DTO theo nghiệp vụ, entity, repository, mapper, service/impl, enums, integration và shared.
- [x] Giữ nguyên `identity-service`.

**Lưu ý trạng thái:** kết quả phase 0 chỉ là cấu trúc; database được thêm ở phase 1. Route gateway vẫn là mẫu.

## Phase 1 — Nền tảng auth và database

**Việc cần làm**

- [x] Cấu hình PostgreSQL `auth_db`, JDBC/JPA/Flyway, biến môi trường và profile local/test.
- [x] Chuẩn bị cấu hình hạ tầng local cần cho auth, gồm PostgreSQL và Mailpit; RabbitMQ dùng ở phase 2.
- [x] Schema ban đầu cho tài khoản và hồ sơ tối thiểu; chỉ thêm bảng OTP/token/event khi đến phần dùng chúng.
- [x] Chuẩn ID UUID v7, thời gian UTC, soft delete và migration theo SPEC.
- [x] Chuẩn lỗi, exception handler và validation. Những kiểu thực sự dùng giữa service đặt ở module `common`; không đưa nghiệp vụ auth hoặc repository vào common.
- [x] Cấu hình password encoder và mapper theo cấu trúc đã chốt.

**Hoàn thành khi:** auth khởi động với schema được Flyway tạo và Hibernate kiểm tra; có kiểm chứng persistence/lỗi trên database test tách biệt. Không khởi động toàn bộ service của nhóm chỉ để kiểm tra auth.

**Kết quả để review:** xem [phase-1.md](phase-1.md) cho style theo StellarStay identify, schema tối thiểu, chuẩn hóa email, giới hạn lưu và format lỗi. 12 test auth đã pass với PostgreSQL 17 riêng qua Testcontainers, Flyway/Hibernate validate thật; không dùng H2 trong auth. Compose mới được kiểm tra cấu hình; Mailpit, app qua Compose và Docker build chưa chạy.

## Phase 2 — Đăng ký và OTP email

**Việc cần làm**

- [ ] API đăng ký email + mật khẩu, lưu mật khẩu đã hash; field đăng ký chính xác chốt trước khi thêm DTO.
- [ ] OTP đăng ký: 6 chữ số, hạn 5 phút, tối đa 5 lần nhập sai/mã; gửi lại cách nhau ít nhất 60 giây, mã mới thay mã cũ.
- [ ] Gửi mail qua Mailpit local; thông số mail/OTP lấy từ config.
- [ ] Xác thực thành công thì tài khoản đủ điều kiện login; không yêu cầu OTP mỗi lần login.
- [ ] Đăng ký lại email chưa xác thực hướng dẫn xác thực tiếp/gửi lại OTP; email đã xác thực báo đã sử dụng.
- [ ] Phát `account.registered` sau xác thực thành công theo câu trả lời debate; công bố hợp đồng cho wallet/ranking và chọn cách phát lại khi gửi event thất bại.
- [ ] Ghi request/response/lỗi thực tế vào `docs/auth/api-reference.md` khi triển khai.

**Hoàn thành khi:** chạy được đăng ký → xem OTP trong Mailpit → xác thực; kiểm chứng email trùng, OTP sai/hết hạn/dùng lại, giới hạn nhập sai, gửi lại và xác thực đồng thời không phát hai sự kiện nghiệp vụ cho cùng lần đăng ký.

**Chi tiết cần chốt lúc làm:** field đăng ký, quy tắc mật khẩu, tự login hay chuyển về màn login sau xác thực. Chưa tự coi đề xuất cũ là đã được đồng ý.

## Phase 3 — Login, refresh và logout

**Việc cần làm**

- [ ] Login bằng email + mật khẩu; tài khoản chưa hoàn tất OTP đăng ký chưa được login.
- [ ] JWT access token khớp nhu cầu của game hiện có: khóa xác minh qua JWKS, issuer/audience, UUID `sub`; chốt claim vai trò và cách quản lý khóa khi triển khai.
- [ ] Refresh token có bản ghi backend; không lưu token thô trong DB.
- [ ] Thời hạn ban đầu access 15 phút, refresh 7 ngày; đọc từ biến môi trường `.env`.
- [ ] Dùng cookie; hỗ trợ lấy lại trạng thái đăng nhập sau F5/đóng mở trình duyệt khi token còn hợp lệ.
- [ ] Cho nhiều thiết bị đăng nhập; logout xóa cả hai token phía trình duyệt và thu hồi refresh token tương ứng tại BE.
- [ ] Không thu hồi refresh token của thiết bị khác; không thêm cơ chế chặn access token trước khi hết hạn ở phase đầu.
- [ ] Giới hạn đăng nhập sai và chờ tạm; chưa thêm quản lý thiết bị/logout all hoặc khóa dài ngày.
- [ ] Chuẩn bị cơ chế lấy thông tin người đang login và token cần cho STOMP, tương thích lựa chọn cookie; không thay giao thức của game một cách âm thầm.

**Hoàn thành khi:** login đúng/sai hoạt động; refresh hết hạn hoặc đã thu hồi bị từ chối; logout một phiên không ảnh hưởng phiên khác; kiểm tra chữ ký/hạn/issuer/audience của JWT. Có kiểm chứng việc xóa cookie và hạn cookie, không chỉ kiểm tra response body.

**Chi tiết cần chốt lúc làm:** thuộc tính cookie, rotation refresh, refresh đồng thời và thông số chờ khi login sai. Những chi tiết này phục vụ login/logout đơn giản, không phát triển thành tính năng quản lý thiết bị.

## Phase 4 — Gateway và tích hợp luồng đầu tiên

**Việc cần làm**

- [ ] Route HTTP theo SPEC mục 6.4, giữ đúng path controller; sửa chỗ `StripPrefix=2` đang lệch với `/api/game/**`.
- [ ] Route WebSocket game/chat/social và Admin theo service sở hữu; không mở `/internal/**` qua gateway.
- [ ] Xác thực access token, xóa header danh tính/service key do client tự gửi rồi gắn header tin cậy; kiểm tra quyền Admin.
- [ ] Hoàn thiện CORS và bảo vệ request dùng cookie theo cách đã thống nhất. Chốt allowlist bằng method/path cụ thể.
- [ ] Nối `X-Game-Service-Key`, JWKS/issuer/audience và địa chỉ auth cho restrictions qua cấu hình thuộc phần Khanh. Thay đổi code game nếu cần phải phối hợp với Bảo.
- [ ] API profiles batch tối thiểu, không lộ dữ liệu nhạy cảm, và restrictions cho tài khoản hợp lệ khi chưa có tính năng xử phạt.
- [ ] Nối việc nhận quà lần đầu sau login/khi vào dashboard: một phần quà/tài khoản; gọi wallet với key ổn định, không tự viết ví hoặc cập nhật số dư.
- [ ] Chuẩn bị hợp đồng để FE dùng auth và sảnh; triển khai khung FE/API client/màn auth ở repo FE khi bắt đầu phần FE.

**Hoàn thành phần Khanh khi:** luồng đăng ký → OTP → login → refresh/logout đi qua gateway; game nhận được danh tính hợp lệ, path đúng; spoof header/thiếu quyền và truy cập internal qua gateway bị từ chối. Profiles/restrictions có kiểm chứng và lời gọi thưởng không tạo key mới khi gọi lại.

**Mốc tích hợp cùng nhóm:** tài khoản mới → login → nhận xu → vào Rank → chơi hết trận → thấy Elo/xu thay đổi. Mốc này phụ thuộc wallet, matchmaking, ranking và FE; dùng mock giúp phát triển phần Khanh nhưng không được báo toàn bộ luồng đã chạy thật khi phụ thuộc vẫn là mock.

**Phạm vi ví:** chỉ tích hợp API/event, ghi nhận kết quả ở auth và phối hợp với Tùng. Không mở rộng sang thiết kế sổ cái, thu phí Rank hoặc logic hoàn phí.

## Phase 5 — Hồ sơ và khôi phục tài khoản

Chia thành từng phần nhỏ để triển khai/kiểm tra riêng:

- [ ] Xem/sửa hồ sơ, phân biệt thông tin chủ tài khoản và dữ liệu được công khai; tên hiển thị được trùng.
- [ ] Avatar: chọn hình có sẵn hoặc upload; chốt loại file, dung lượng và nơi lưu khi làm chức năng này.
- [ ] Lấy Elo/lịch sử trận từ ranking/game cho màn Hồ sơ; không sao chép nghiệp vụ hoặc đọc DB của họ.
- [ ] Quên/reset mật khẩu bằng OTP email.
- [ ] Đổi mật khẩu khi đang đăng nhập là chức năng riêng; chốt quy tắc phiên sau đổi/reset trước khi triển khai.
- [ ] Hoàn thiện các màn FE tương ứng và cập nhật API reference.

**Hoàn thành khi:** chỉ sửa được hồ sơ của mình; không lộ field nhạy cảm qua batch; avatar đạt quy tắc đã chốt; reset bằng OTP hợp lệ dùng một lần và được kiểm chứng độc lập với đổi mật khẩu.

## Phase 6 — Điểm danh thường

- [ ] API điểm danh ngày hiện tại và lịch theo tháng; lấy danh tính từ request đã xác thực.
- [ ] Ngày theo `Asia/Ho_Chi_Minh`; thưởng cố định, chưa có chuỗi ngày hoặc quà theo mốc. Mức 50 xu hiện là đề xuất trong SPEC, xác nhận giá trị lúc làm.
- [ ] Một bản ghi/ngày/tài khoản; gọi wallet bằng key `checkin:{yyyy-MM-dd}:{accountId}`.
- [ ] Khi làm tới, chốt hành vi gọi lại và trạng thái nếu wallet chưa cấp thưởng; câu C2 hiện chưa được trả lời.
- [ ] Màn Điểm danh và lịch tháng ở FE.

**Hoàn thành khi:** một ngày không nhận hai phần thưởng kể cả hai request đồng thời; kiểm tra ranh giới ngày theo giờ Việt Nam và lỗi từ API ví theo cách đã chốt. Chưa gồm điểm danh bù.

## Phase 7 — Phần nâng cao sau luồng chính

- [ ] Điểm danh bù: ngày cho phép, phí/thưởng và xử lý khi một bước thất bại.
- [ ] Gửi/xem báo cáo; Admin xử lý báo cáo.
- [ ] Ban/unban, cấm chat/cấm Rank có thời hạn và `account.penalized`; đồng bộ hợp đồng loại phạt với bên nhận trước khi tích hợp.
- [ ] Admin người dùng và các màn FE tương ứng.
- [ ] Logout all/quản lý thiết bị hoặc Google login chỉ làm khi có yêu cầu ưu tiên bổ sung.

**Hoàn thành theo từng chức năng:** chốt nghiệp vụ, API/event, kiểm tra quyền và kiểm chứng thực tế trước khi đánh dấu. Không mặc định cam kết làm hết phase này trước deadline tuần học 10.

## 3. Mốc bàn giao và bước tiếp theo

- **Mốc A — tài khoản chạy được:** phase 1–3 hoàn tất và được kiểm chứng ở BE. FE auth còn phải được kiểm tra khi nối gateway.
- **Mốc B — sẵn sàng ghép vào luồng Rank:** phase 4 hoàn tất phần Khanh; ghi rõ phụ thuộc nào đã nối thật hoặc vẫn mock. Kiểm tra trình duyệt F5, refresh và logout khi FE đã có.
- **Mốc C — mở rộng tài khoản:** phase 5–6 theo tiến độ, rồi mới chọn chức năng phase 7.
- Mỗi mốc cập nhật hướng dẫn chạy, biến môi trường, API reference và kết quả kiểm tra; chuẩn bị demo trên môi trường tách biệt trước khi nộp bài.

**Bước tiếp theo:** Khanh review phase 1, có thể chạy app với PostgreSQL dev theo [README auth](../../auth-service/README.md); sau đó chốt field đăng ký/quy tắc mật khẩu và bắt đầu phase 2. Chưa triển khai trước các phase sau trong đợt này.
