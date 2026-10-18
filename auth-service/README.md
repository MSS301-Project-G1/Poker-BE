# auth-service

Phase 2–3: đăng ký/OTP qua SMTP, event `account.registered` qua RabbitMQ với outbox,
login/refresh/logout bằng cookie HttpOnly và JWT RS256/JWKS.
Port mặc định `8090`; `identity-service` giữ nguyên. Auth đã được nối qua gateway
trong phase 4A, xem [README gateway](../api-gateway/README.md). Phase 5.1 đã có xem/sửa
hồ sơ cá nhân; 5.2 có avatar có sẵn và flow S3 presigned PUT; 5.3 có quên/reset mật khẩu bằng OTP.
Đổi mật khẩu khi đang đăng nhập và FE chưa triển khai.
Phase 4B.1 bổ sung `GET /api/profiles/batch?ids=...` để FE lấy tên/avatar người chơi;
API cần access cookie, chưa phải chức năng chỉnh sửa hồ sơ.
Tài liệu local trong `docs/` đang gitignore: [API reference](../docs/auth/api-reference.md),
[review phase 2](../docs/auth/phase-2.md), [kế hoạch](../docs/auth/implementation-plan.md),
[chạy Docker và test Swagger](../docs/auth/local-development.md).

## Avatar có sẵn và S3 upload

- GET `/api/profiles/avatars`: danh sách avatar có sẵn; PUT `/api/profiles/me/avatar` với `{avatarId}` để chọn.
- GET `/api/profiles/me/avatar/presign?fileName=avatar.png&contentType=image%2Fpng&fileSize=123456`: xin key và `uploadUrl`; cần access cookie, không cần body/CSRF.
- FE **PUT file trực tiếp tới `uploadUrl` S3**, dùng `headers` BE trả và không gửi cookie/CSRF tới S3.
- POST `/api/profiles/me/avatar` với `{key}` sau PUT thành công: BE validate ảnh và cập nhật hồ sơ.
- Các request BE cần access cookie; POST/PUT cần CSRF. Response vẫn bọc `ApiResponse`.

PNG/JPEG, mặc định tối đa 2 MB và mỗi cạnh tối đa 2048 px. DB lưu object key cho ảnh upload;
`avatarUrl` trong DTO là URL GET có hạn để bucket giữ private. Avatar có sẵn dùng được khi S3 chưa bật.

Điền các biến `AUTH_S3_*` trong `.env.example` sang `.env`, cấu hình CORS bucket rồi đặt
`AUTH_S3_ENABLED=true`. Mặc định false giúp auth khởi động khi chưa có credentials.
Không có endpoint upload multipart lên BE hoặc volume lưu ảnh local. Chỉ thêm cấu hình vào
Compose auth; root/infra giữ nguyên. Flow FE, env, CORS/IAM và cách test ở [avatar-s3.md](../docs/auth/avatar-s3.md).
SPEC chưa cập nhật, chờ Khanh review xong như đã yêu cầu.

## Xem/sửa hồ sơ cá nhân

Login trước trên Swagger qua gateway `http://localhost:8080/swagger-ui.html`, mở nhóm **Hồ sơ**:

- `GET /api/profiles/me`: lấy hồ sơ của tài khoản trong access cookie, gồm email/điện thoại và thông tin riêng của chủ tài khoản.
- `PUT /api/profiles/me`: lưu toàn bộ form, gửi cookie và header `X-XSRF-TOKEN` đã lấy từ `/api/auth/csrf`.

Ví dụ body PUT:

```json
{
  "displayName": "Khanh",
  "fullName": "Nguyễn Khánh",
  "phoneNumber": "0901234567",
  "gender": "MALE",
  "birthDate": "2003-05-12",
  "bio": "Thích chơi Poker",
  "countryCode": "VN",
  "city": "Hồ Chí Minh"
}
```

`displayName` bắt buộc, tối đa 100 ký tự và được trùng. PUT thay toàn bộ các field được sửa:
field tùy chọn bị bỏ khỏi body/null/chuỗi trắng sẽ xóa giá trị cũ. Service strip text,
chuyển mã quốc gia thành chữ hoa; điện thoại nhận 7–15 chữ số với dấu `+` tùy chọn ở đầu,
ngày sinh không được sau ngày hiện tại theo giờ Việt Nam. Điện thoại chỉ là thông tin hồ sơ,
chưa xác thực OTP số điện thoại.

Account ID lấy từ JWT, không lấy từ body/query/header của client. Email và avatar chỉ đọc
trong đợt này; role/mật khẩu/trạng thái tài khoản không thuộc form. `/api/auth/me` tiếp tục trả
thông tin phiên và hồ sơ tối thiểu; profiles batch tiếp tục chỉ trả ID/tên/avatar.
Response vẫn dùng `ApiResponse`, dữ liệu trong `result`. Chi tiết và lỗi ở
[API reference](../docs/auth/api-reference.md) và [phase-5.md](../docs/auth/phase-5.md) trong docs local.

## Lấy hồ sơ công khai theo danh sách ID

Sau khi login trên Swagger qua gateway `http://localhost:8080/swagger-ui.html`,
mở nhóm **Hồ sơ → GET `/api/profiles/batch`** và nhập account ID lấy từ `/api/auth/me`.
FE gọi:

```javascript
const ids = ["019a4c30-8000-7000-8000-000000000001"];
const response = await fetch(
  `http://localhost:8080/api/profiles/batch?${new URLSearchParams({ ids: ids.join(",") })}`,
  { credentials: "include" },
);
const body = await response.json();
if (!response.ok) throw new Error(body.message);
const profiles = body.result;
```

- Nhận 1–100 UUID, phân cách bằng dấu phẩy hoặc lặp query `ids`; giới hạn tính trước khi bỏ trùng.
- `result` là danh sách `{accountId, displayName, avatarUrl}`, giữ thứ tự ID xuất hiện đầu tiên.
- ID không tồn tại, chưa có profile hoặc tài khoản đã xóa được bỏ qua; không tìm thấy trả `[]`.
- Không trả email, số điện thoại, mật khẩu hoặc thông tin hồ sơ riêng tư. Avatar chưa có trả `null`.
- GET không cần CSRF header; vẫn bắt buộc access cookie hợp lệ. Thiếu/sai ID trả 400, chưa login trả 401.
- Dữ liệu công khai này không xác nhận user được phép chơi; restrictions là hợp đồng riêng ở bước tiếp theo.

## Chạy auth riêng bằng Docker

Chạy từ root repo và điền SMTP trong `.env`. Ghép hạ tầng gốc với cấu hình auth:

```bash
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml up -d --build auth-service
```

Lệnh này chỉ chạy `auth-service`, `auth-postgres` và `rabbitmq`.
`rabbitmq` chỉ được định nghĩa ở `docker/compose.infra.yml`; cấu hình auth chính
tham chiếu broker qua `depends_on`, không khai báo lại service/volume RabbitMQ.
Root `docker-compose.yml` include infra và auth nên dùng được cấu hình này mà không trùng service.
Các lệnh auth riêng bên dưới ghép file bằng `-f`, không dùng root làm base.

Auth ở `http://localhost:8090`. Cấu hình chính không publish port PostgreSQL;
RabbitMQ kế thừa port AMQP `127.0.0.1:56729` và UI `http://localhost:15679` từ infra.
Auth mặc định dùng `guest`/`guest` để khớp broker chung. Auth trong Docker luôn
kết nối `rabbitmq:5672`. Nếu `.env` đang đặt `AUTH_RABBIT_USERNAME=poker` và
`AUTH_RABBIT_PASSWORD=poker_dev`, khi chạy root/cấu hình chung cần đổi thành
`guest`/`guest` hoặc bỏ/comment hai biến để dùng mặc định; `.env` luôn ghi đè fallback.

### Override khi phát triển

`auth-service/compose.dev.yml` chỉ chứa các khác biệt dành cho dev:

- `auth-service`: bind API vào localhost và đổi credential RabbitMQ mặc định thành `poker`/`poker_dev`.
- `auth-postgres`: mở PostgreSQL ở `127.0.0.1:54330` để dùng DBeaver/IDE.
- `auth-mailpit`: mở SMTP ở `127.0.0.1:1026` khi bật profile `mailpit`.
- `rabbitmq`: ghi đè broker infra bằng image `4.1-management-alpine`, credential mặc định `poker`/`poker_dev`, volume `auth-rabbit`; port AMQP `AUTH_RABBIT_PORT` (mặc định `5673`) và UI `15673`.

File dev chỉ override các khác biệt trên, kế thừa healthcheck của RabbitMQ từ infra
và giữ project name. Biến `AUTH_RABBIT_USERNAME`/`AUTH_RABBIT_PASSWORD` nếu đặt
trong `.env` sẽ ghi đè mặc định ở cả app lẫn broker dev; bỏ/comment hai biến để
tự chọn đúng default chung/dev. Cần Docker Compose 2.24.4+ để dùng `!override`.
Nếu đang chạy bộ dev cũ, đọc mục chuyển đổi bên dưới trước khi chạy lần đầu.
Ghép file dev **sau** infra và cấu hình auth; không chạy file dev một mình:

```bash
# Build và chạy auth + PostgreSQL + RabbitMQ.
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f auth-service/compose.dev.yml up -d --build auth-service

# Chạy cùng nhóm từ image có sẵn.
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f auth-service/compose.dev.yml up -d auth-service

curl http://localhost:8090/actuator/health
```

Không cần profile `app`. Cấu hình chính và dev dùng cùng project `poker-auth-dev`
(được khai báo trong `auth-service/compose.yml` để giữ tên project cũ), cùng volume
DB và khóa JWT. Chỉ file dev gắn volume RabbitMQ `auth-rabbit` của bộ auth cũ;
cấu hình chính dùng broker nguyên bản từ infra.
PostgreSQL là `auth_db`, tài khoản lấy từ `AUTH_DB_USERNAME`/`AUTH_DB_PASSWORD`
(mặc định local `poker`/`poker_dev`). Root Compose include cấu hình auth chính cùng
các service khác; không include file dev. Giữ nguyên tên project/volume khi tiếp tục
bộ dev đang có dữ liệu.

### Chuyển từ Compose dev cũ

Mặc định giữ nguyên ba volume: `poker-auth-dev_auth-db`,
`poker-auth-dev_auth-keys`, `poker-auth-dev_auth-rabbit`. Không xóa volume hay sao
chép DB để áp dụng thay đổi này. Giữ nguyên user/password DB/RabbitMQ trong `.env`.
Các biến khởi tạo không tự đổi tài khoản đã tồn tại trong dữ liệu.

Service PostgreSQL đổi tên từ `postgres` sang `auth-postgres`; trước lần chạy đầu,
xem container DB cũ và dừng nó nếu đang chạy:

```bash
docker ps -a --filter label=com.docker.compose.project=poker-auth-dev --filter label=com.docker.compose.service=postgres
# Khi dùng tên mặc định và container cũ đang chạy:
docker stop poker-auth-dev-postgres-1
```

Không chạy hai PostgreSQL cùng ghi vào volume `auth-db`. Nếu Mailpit cũ (`mailpit`)
đang chạy, dừng container đó trước khi bật Mailpit mới (`auth-mailpit`) để nhường port.
Không dùng `down -v` hay `--remove-orphans` để thực hiện chuyển đổi.
Nếu trước đây đã tự đặt tên project/volume khác, giữ `-p` như cũ hoặc cấu hình
`AUTH_DB_VOLUME_NAME`/`AUTH_JWT_VOLUME_NAME` cho đúng volume; không tự chuyển dữ liệu.
Việc dừng/tạo lại container chưa được thực hiện trong thay đổi code này.

## Gửi OTP vào hộp thư thật

Cấu hình auth chính truyền các biến `AUTH_MAIL_*` trong `.env` vào app; file dev kế thừa nguyên cấu hình SMTP.
Ví dụ Gmail:

```dotenv
AUTH_MAIL_HOST=smtp.gmail.com
AUTH_MAIL_PORT=587
AUTH_MAIL_FROM=your-email@gmail.com
AUTH_MAIL_USERNAME=your-email@gmail.com
AUTH_MAIL_PASSWORD=your-google-app-password
AUTH_MAIL_SMTP_AUTH=true
AUTH_MAIL_STARTTLS=true
```

Thay email bằng tài khoản gửi thật; `AUTH_MAIL_FROM` dùng cùng email với
`AUTH_MAIL_USERNAME`. Dùng [Google App Password](https://support.google.com/mail/answer/185833),
không dùng mật khẩu đăng nhập Gmail. Gmail SMTP dùng
[port 587 với STARTTLS](https://support.google.com/mail/answer/7104828).
OTP được gửi đến email trong request đăng ký; lấy mã ở hộp thư đó, kiểm tra cả Spam.
Spring Boot giữ timeout SMTP 5 giây; gửi thất bại trả `AUTH_MAIL_UNAVAILABLE` và rollback.

Khi chỉ sửa `.env` hoặc cấu hình Compose và app đã được build, tạo lại riêng
container auth để nạp cấu hình mới (hạ tầng phải đang chạy):

```bash
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f auth-service/compose.dev.yml up -d --no-deps --force-recreate auth-service
```

Không cần build lại image chỉ để đổi biến môi trường; `restart` đơn thuần không
nạp lại cấu hình Compose mới. Khanh đã xác nhận test đăng ký/OTP bằng mail thật thành công.

### Mailpit tùy chọn

Mailpit chỉ chạy khi chọn profile `mailpit` hoặc gọi đích danh service. Nếu muốn
test không gửi mail thật, đổi `.env` sang `AUTH_MAIL_HOST=auth-mailpit`,
`AUTH_MAIL_PORT=1025`, `AUTH_MAIL_FROM=no-reply@poker.local`, để trống username/password,
và đặt `AUTH_MAIL_SMTP_AUTH=false`, `AUTH_MAIL_STARTTLS=false`, rồi chạy:

```bash
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f auth-service/compose.dev.yml --profile mailpit up -d auth-service auth-mailpit
```

Mailpit UI: `http://localhost:8026`. Khi Java chạy trên máy, dùng host `localhost`
và port `1026` thay cho tên service/port nội bộ Docker ở trên.
Cấu hình chính và dev đều dùng service `auth-mailpit`, port nội bộ `1025` và profile `mailpit`.

## Chạy Java trên máy

```bash
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f auth-service/compose.dev.yml up -d auth-postgres rabbitmq
mvn -B -pl auth-service -am install -DskipTests
# Export AUTH_* và JWT_* trong .env vào môi trường Java trước khi chạy.
# Khi dùng dev override, AUTH_RABBIT_PORT mặc định 5673; export giá trị tùy chỉnh từ .env nếu có.
mvn -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local
```

Profile `local` dùng password mặc định của Compose dev. Ngoài local phải cung cấp
`AUTH_DB_PASSWORD` (hoặc `SPRING_DATASOURCE_PASSWORD`) và `AUTH_RABBIT_PASSWORD`.
`AUTH_DB_URL` và
`AUTH_DB_USERNAME` đổi kết nối; mặc định URL/user như trên. Spring Boot không tự đọc
file `.env`: export biến vào shell, cấu hình trong IDE hoặc dùng Compose `--env-file .env`.
Giá trị trong `.env.example` chỉ phục vụ local, không dùng làm secret triển khai.

## Test API bằng Swagger UI

Sau khi auth-service khởi động, mở **http://localhost:8090/swagger-ui.html**.
Khi đã chạy gateway theo [hướng dẫn](../api-gateway/README.md), dùng
**http://localhost:8080/swagger-ui.html** để toàn bộ request đi qua gateway.
OpenAPI JSON: `http://localhost:8090/v3/api-docs`.

1. Mở nhóm **Đăng ký và OTP**, chọn **1. Đăng ký tài khoản** → **Try it out**.
2. Sửa JSON mẫu, giữ `password` và `confirmPassword` khớp nhau → **Execute**. Thành công trả 201.
3. Mở hộp thư của email đăng ký để lấy OTP; nếu chọn Mailpit thì mở `http://localhost:8026`.
4. Chọn **2. Xác thực OTP đăng ký**, nhập cùng email và OTP dạng string 6 chữ số → **Execute**. Thành công trả ACTIVE; chưa cấp token.
5. Khi chưa xác thực và cần mã mới, dùng **3. Gửi lại OTP đăng ký** sau thời gian chờ (mặc định 60 giây).

Swagger có mô tả field, JSON mẫu, response schema và mã lỗi cho cả ba API;
mã `012345` chỉ là ví dụ, cần thay bằng OTP thật trong email. Các API hiện tại
public, chưa cần bấm Authorize. UI gọi auth-service ở địa chỉ đang mở.

Response thành công theo style StellarStay: `code`, `message`, `result`, `timestamp`,
`path`; dữ liệu nghiệp vụ nằm trong `result` (ví dụ `result.accountId`,
`result.accountStatus`). `code` thành công là `success_request`, timestamp UTC.
Đăng ký trả HTTP 201, xác thực/gửi lại trả 200. Swagger hiển thị envelope và DTO
trong `result`. Lỗi dùng format chung `code`, `message`, `timestamp`, `requestId`.

Mặc định Swagger bật. `AUTH_SWAGGER_ENABLED=false` tắt cả UI và OpenAPI JSON;
biến được chuyển vào app ở cả Compose dev và root Compose. Java trên máy cần
export/IDE env như các biến cấu hình khác. Nếu BE đang chạy trước khi thêm dependency,
khởi động lại để tải Swagger. Nguồn thư viện: [springdoc-openapi](https://springdoc.org/).

`auth-service/compose.yml` định nghĩa PostgreSQL `auth-postgres`, không publish
port DB; file dev chỉ bổ sung port host. Auth trong Docker luôn kết nối
`auth-postgres:5432`, không dùng URL localhost dành cho Java trên máy.
Cấu hình chính và dev dùng cùng database/volume. Không bật đồng thời app Java
trên host và container auth trên cùng port 8090.

Flyway áp dụng V1–V5 khi khởi động; V4 thêm `refresh_tokens`, V5 thêm
`password_reset_otps` riêng với OTP đăng ký; không sửa migration đã dùng.
V2 đổi Profile sang khóa chính `account_id` lấy từ Account, thêm field tài khoản/hồ sơ;
không sửa V1 đã commit. Nếu dữ liệu cũ có profile đã xóa nhưng Account còn hoạt động,
V2 dừng để review dữ liệu thay vì tự làm profile đó xuất hiện lại.
V3 tạo `registration_otps` và `account_registration_events`, không đổi V1/V2.
Hibernate chỉ `validate`, không tự sửa schema. Mọi thay đổi tiếp theo dùng migration mới.

Entity chỉ mapping dữ liệu; chuẩn hóa email và validation đăng ký nằm ở service/DTO.
Soft delete nghiệp vụ sẽ làm sau. Cờ xóa ở Account; Profile không có ID/cờ xóa riêng.
`cascade = ALL` ở Profile → Account có cả REMOVE: luồng soft delete cần cập nhật
`account.deleted`, không gọi `delete(profile)` hoặc `delete(account)`.

## Thử đăng ký và OTP

```bash
curl -i http://localhost:8090/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"khanh@example.com","password":"test-password","confirmPassword":"test-password","displayName":"Khanh"}'
```

Mở hộp thư email đăng ký (hoặc Mailpit nếu dùng cấu hình tùy chọn), lấy mã 6 chữ số
rồi thay `012345` bằng mã vừa nhận:

```bash
curl -i http://localhost:8090/api/auth/register/verify-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"khanh@example.com","otp":"012345"}'

curl -i http://localhost:8090/api/auth/register/resend-otp \
  -H 'Content-Type: application/json' \
  -d '{"email":"khanh@example.com"}'
```

Gửi lại dùng cho tài khoản chưa xác thực, cách lần gửi trước ít nhất 60 giây.
OTP mặc định hết hạn sau 5 phút, tối đa 5 lần nhập sai/mã. Các biến `AUTH_OTP_*`
nằm trong `.env.example`. Mật khẩu tối thiểu 8 ký tự, tối đa 72 byte UTF-8;
confirmPassword phải khớp, không lưu hoặc trim mật khẩu. OTP thành công trả ACTIVE;
FE chuyển về login, response chưa cấp token/cookie.

Email chưa xác thực đăng ký lại trả 409 `AUTH_EMAIL_VERIFICATION_PENDING`, không
thay password/profile; đã xác thực/đã xóa trả `AUTH_EMAIL_IN_USE`. SMTP lỗi trả 503
và rollback thao tác. Sai OTP vẫn lưu số lần thử dù trả lỗi HTTP.

Event được lưu cùng transaction xác thực rồi job phát sang topic exchange `poker.events`
với routing key `account.registered`. Consumer tạo queue durable/binding trước khi
tích hợp. Local chưa có queue thì event giữ pending để retry, không làm OTP thất bại.
Job mặc định 5000 ms/batch, tối đa 20 rows; timeout confirm 5 giây/event.
`AUTH_EVENTS_DISPATCH_ENABLED=false` tắt job; `AUTH_EVENTS_DISPATCH_DELAY` tính bằng ms.
ACK phải có ít nhất một queue nhận; không chứng minh tất cả consumer đã sẵn sàng.
Consumer chống trùng bằng `eventId`: crash sau ACK/trước DB commit có thể phát lại.
Không gọi ví/cấp quà trong phase 2.

## Quên/đặt lại mật khẩu — phase 5.3

Hai API public, không cần access cookie hay CSRF; gateway đã allowlist đúng hai path:

1. `POST /api/auth/password/forgot` với `{ "email": "khanh@example.com" }`.
2. Lấy OTP **đặt lại mật khẩu** trong email, gọi `POST /api/auth/password/reset`:

   ```json
   {
     "email": "khanh@example.com",
     "otp": "012345",
     "password": "NewPoker123!",
     "confirmPassword": "NewPoker123!"
   }
   ```

3. Thành công thì về màn login, đăng nhập bằng mật khẩu mới; OTP chỉ dùng một lần.

Swagger có nhóm **Khôi phục mật khẩu**. Gửi lại OTP bằng cách gọi lại `forgot` sau
cooldown. Dùng chung `AUTH_OTP_*`: 6 số, mặc định hạn 5 phút, 5 lần thử, gửi lại sau
60 giây; quy tắc mật khẩu giống đăng ký, BE kiểm tra cả `confirmPassword`.
OTP khôi phục lưu ở bảng riêng, không dùng OTP đăng ký.

`forgot` nhận email hợp lệ luôn trả HTTP 200/thông báo chung, kể cả email không tồn tại,
đang cooldown hoặc SMTP lỗi. Cooldown không gửi thêm mail; SMTP lỗi giữ nguyên mã
và bộ đếm cũ. Kiểm tra cấu hình SMTP/log chung nếu không nhận được mail. Gửi mail còn
đồng bộ, chưa bảo đảm thời gian phản hồi đồng nhất giữa email tồn tại và không tồn tại.

Reset thành công thu hồi mọi refresh token của đúng account và xóa access/refresh
cookie trình duyệt hiện tại, chưa tự login. Access JWT đã phát vẫn hợp lệ tới hạn;
chưa có blacklist. Không ảnh hưởng phiên của account khác.
Lỗi reset gồm `AUTH_OTP_INVALID`/`AUTH_OTP_EXPIRED` (400),
`AUTH_OTP_ATTEMPTS_EXCEEDED` (429), `AUTH_PASSWORD_MISMATCH`/`AUTH_PASSWORD_INVALID` (400).
Response thành công dùng `ApiResponse` không có `result`; lỗi dùng `ErrorResponse` chung.

Có migration V5 khi start bản mới. Test dùng PostgreSQL tạm và mail mock; chưa test SMTP
thật cho reset, chưa rebuild stack dev. Hợp đồng đầy đủ và hướng dẫn FE ở
[password-reset.md](../docs/auth/password-reset.md). SPEC giữ nguyên chờ Khanh review.

## Test login/refresh/logout bằng Swagger

1. Dùng tài khoản đã xác thực OTP. Mở nhóm **Phiên đăng nhập**.
2. Gọi `GET /api/auth/csrf`, copy `result.token` vào **Authorize → csrfToken**.
   Trình duyệt tự giữ cookie `XSRF-TOKEN`; khi mất cookie hoặc đổi trình duyệt, lấy lại CSRF token.
3. Gọi `POST /api/auth/login` với `email`, `password`. Trình duyệt giữ cookie
   `accessToken` và `refreshToken` HttpOnly; response chỉ chứa accountId/email/role và hạn token.
4. Gọi `GET /api/auth/me` để xem danh tính và profile cơ bản.
5. Gọi `POST /api/auth/refresh`: đổi cả hai token, giữ nguyên ngày hết hạn của phiên từ lần login.
6. Gọi `POST /api/auth/logout`: thu hồi refresh token tại BE và xóa cả hai cookie.
   `me` sau khi trình duyệt xóa cookie trả 401; thiết bị khác vẫn đăng nhập.

Không cần nhập access JWT vào Authorize; mục Authorize là **CSRF token**.
CSRF áp dụng cả login/refresh/logout; các API đăng ký/OTP vẫn public và không cần CSRF token.
Local dùng HTTP, `AUTH_COOKIE_SECURE=false`, SameSite=Lax; khi deploy HTTPS đặt Secure=true
và cấu hình CORS bằng origin FE thực tế. Không dùng wildcard CORS với cookie.

FE `http://localhost:5173` dùng `credentials: "include"` với fetch hoặc
`withCredentials: true` với Axios. Lấy CSRF token từ JSON, gửi header `X-XSRF-TOKEN`
trong login/refresh/logout. F5 gọi `me`; nếu 401 thì gọi refresh rồi thử lại `me` một lần.
Các request 401 đồng thời dùng chung một promise refresh; không chạy nhiều refresh song song.
Nếu refresh trả 401 thì chuyển về login. Refresh lỗi không xóa cookie tự động để tránh
request thua ghi đè cookie của request thắng. Hết 7 ngày từ login phải đăng nhập lại.
Giới hạn login sai để phase sau theo xác nhận của Khanh; OTP vẫn giữ giới hạn riêng.

`GET /.well-known/jwks.json` trả public RSA key, không trả private key.
JWT có `iss`, `aud`, UUID `sub`, `role` (USER/ADMIN), `iat`, `nbf`, `exp`, `jti` và ký RS256.
Issuer/audience mặc định `http://localhost:8090`/`poker`, cần thống nhất khi nối gateway/game ở phase 4.
`GET /api/auth/ws-token` dùng access cookie để lấy JWT hiện tại cho STOMP
`CONNECT` header `Authorization: Bearer <token>`. Giữ JWT trong bộ nhớ FE; không lưu localStorage.
Endpoint hoạt động cả trực tiếp lẫn qua gateway; chưa nối STOMP thật tới game.
Gateway xác thực access cookie và chuyển nguyên JWT/cookie tới auth.

Refresh token ngẫu nhiên 256 bit; DB chỉ lưu SHA-256 hash. Token cũ sau rotation bị từ chối,
không có cơ chế tự thu hồi cả chuỗi hoặc logout all. Logout không chặn access JWT đã cấp:
bản sao vẫn hợp lệ đến hạn, đúng phạm vi phase hiện tại.

Khóa ký được tạo một lần khi `AUTH_JWT_GENERATE_KEY=true`; Compose lưu trong named volume
`auth-keys` tại `/app/keys/jwt-private.pem`. Java trên host lưu ở `.local/auth/jwt-private.pem`,
đã loại khỏi Git và Docker build context. Restart/recreate giữ cùng khóa; cấu hình chính/dev dùng chung volume.
Deploy phải cấp khóa RSA PKCS#8 riêng, tắt tự sinh bằng `AUTH_JWT_GENERATE_KEY=false`;
chưa có xoay khóa hoặc nhiều khóa JWKS. Không xóa volume khóa để cập nhật app.

## Kiểm tra

```bash
mvn -B -pl auth-service -am verify
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml -f auth-service/compose.dev.yml config --quiet
docker compose --env-file .env -f docker/compose.infra.yml -f auth-service/compose.yml config --quiet
```

Test auth dùng PostgreSQL 17 thật qua Testcontainers; cần Docker daemon đang chạy
và user chạy Maven có quyền truy cập Docker. Không cần cài/chạy PostgreSQL trên máy,
không cần bật Compose dev và không dùng H2 trong module auth.

`PostgresTestConfiguration` tạo DB `auth_test` trong container riêng, port được Docker
cấp ngẫu nhiên, không mount volume dữ liệu. Spring Boot `@ServiceConnection` cấp URL,
user/password cho datasource và Flyway, không dùng kết nối DB local hay `.env`.
Container dùng chung trong test context và được dọn khi kết thúc; thiếu Docker thì
test báo lỗi, không tự bỏ qua hoặc chuyển sang database khác.

Test phase 2 bổ sung SMTP thật vào Mailpit và AMQP thật qua RabbitMQ, port ngẫu nhiên,
không volume. Test verify/resend điều khiển đồng hồ để kiểm tra đúng ranh giới hạn;
kiểm tra đồng thời, rollback SMTP và event retry. Không cần bật Compose dev cho test.
Luồng auth qua gateway và hướng dẫn chạy nằm trong [README gateway](../api-gateway/README.md).
Các kiểm tra phase 2–3 trên không có nghĩa đã chạy FE/game hay build lại container dev.

## Tổ chức nghiệp vụ trong auth-service

- `AuthService` / `AuthServiceImpl`: đăng ký, OTP, login, refresh, logout và người dùng hiện tại.
- `ProfileService` / `ProfileServiceImpl`: đọc/sửa hồ sơ, batch profile và avatar (preset, presign, xác nhận upload).
- `ProfileMapper`: chỉ chuyển entity/DTO; service nhờ `S3AvatarStorage` ký URL xem ảnh.
- `integration/messaging/RegistrationEventDispatcher`: phát lại outbox `account.registered` trong transaction riêng, được scheduler gọi sau khi dữ liệu đăng ký đã commit.

Các controller tách theo nhóm API/Swagger; tất cả vẫn thuộc cùng một microservice `auth-service`.
Storage, JWT, cookie và mail là thành phần kỹ thuật dùng bởi service nghiệp vụ.
