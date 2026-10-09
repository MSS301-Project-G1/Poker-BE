# Debate nghiệp vụ auth và phiên đăng nhập

> Chủ sở hữu: Khanh. Cập nhật: 2026-10-07.
>
> Bro trả lời ngay dưới từng câu tại dòng `==> ....`. Có thể trả lời “đồng ý đề xuất”, chọn phương án khác hoặc ghi ý riêng. Dòng chưa trả lời vẫn là **chưa chốt**; các đề xuất bên dưới chưa được triển khai.
>
> Ưu tiên trả lời **phần 1** trước để chốt phiên đăng nhập. Các phần sau là câu hỏi cho nghiệp vụ của Khanh, có thể trả lời dần. Không cần thiết kế nghiệp vụ của ví, game, matchmaking hoặc ranking tại đây.

## Đã chốt, không cần trả lời lại

- Cho phép một tài khoản đăng nhập nhiều thiết bị cùng lúc.
- Đăng nhập thiết bị mới không tự đá phiên của thiết bị khác.
- Logout chỉ kết thúc phiên hiện tại.
- Chức năng logout tất cả thiết bị và quản lý danh sách thiết bị để phase sau.
- Làm auth đơn giản, phục vụ luồng hoàn chỉnh trước. Toàn bộ hồ sơ, điểm danh và các phần khác trong SPEC vẫn thuộc phạm vi Khanh.

## 1. Phiên đăng nhập — trả lời trước

### S1. Một phiên được hiểu thế nào?

Đề xuất: mỗi lần đăng nhập tạo một phiên độc lập. Các tab trong cùng trình duyệt dùng chung phiên; trình duyệt khác hoặc thiết bị khác có phiên riêng. Logout không xác định thiết bị bằng IP.

Bro đồng ý cách hiểu này không?

==> Phức tạp hóa quá =)) Đơn giản login logout trước đi đã =)) Mấy cái này tính sau, scope môn học thôi chứ không phải sản phẩm thực tế đâu fen 

### S2. Người chơi được giữ đăng nhập bao lâu?

Đề xuất ban đầu: access token có hạn 15 phút; phiên có thể tiếp tục bằng refresh token trong 7 ngày kể từ lúc đăng nhập. Hết 7 ngày thì phải đăng nhập lại. Các thời hạn nằm trong config.

Bro chọn thời hạn này hay muốn giá trị khác? Giai đoạn đầu có cần nút “Ghi nhớ đăng nhập” không? Tôi đề xuất chưa cần nút này.

==> Oce, cái này set trong .env nhé, sau này muốn thay đổi gì thì đổi config 

### S3. Đóng trình duyệt rồi mở lại có giữ đăng nhập không?

Đề xuất: có, nếu phiên vẫn còn hạn và người chơi chưa logout. Tải lại trang cũng không bắt đăng nhập lại.

Bro muốn như vậy hay chỉ giữ đăng nhập trong lần mở trình duyệt hiện tại?

==> Nếu phiên còn hạn và người chơi chưa logout thì vẫn giữ như thường nhé, F5 thì check refresh token còn hay hết là được, không bắt đăng nhập lại khi đã login và sau đó F5 nhé. 

### S4. Logout có cần vô hiệu hóa access token ngay lập tức không?

Hai mức xử lý cần chọn:

- Đơn giản: thu hồi refresh token của phiên hiện tại và xóa trạng thái đăng nhập ở FE. Access token đã cấp vẫn có thể được dùng nếu ai giữ bản sao, cho đến lúc hết hạn (ví dụ tối đa 15 phút theo S2).
- Thu hồi ngay: request dùng access token của phiên vừa logout bị từ chối ngay; cần thêm cơ chế kiểm tra phiên hoặc token đã thu hồi.

Bro chọn mức nào cho phase đầu? Logout của phiên này vẫn không ảnh hưởng các phiên khác.

==> Có, tôi nghĩ logout thì nó phải xóa hết cả 2 chứ nhỉ :v Sao lại để accessToken lại được ? Request thì dùng accessToken để check, nếu không có accessToken thì nó sẽ check refreshToken, rồi nếu mà refreshToken hết hạn nữa thì lúc đó mới đăng nhập lại chứ đúng không ?
Còn nếu refreshToken còn hạn thì nó sẽ refresh lại cả refreshToken và accessToken mới. Nên vì vậy khi logout thì tôi nghĩ xóa cả 2 chứ nhỉ.

> **Xác nhận thêm của Khanh ngày 2026-10-07:** logout xóa cả hai token phía trình duyệt và phải thu hồi refresh token tương ứng ở BE. Phase này chưa cần cơ chế chặn access token trước khi hết hạn; refresh token của các thiết bị khác không bị thu hồi. Cách cấp/đổi token khi refresh bàn khi làm tới, không chốt rotation tại đây.

### S5. Nếu đang đăng nhập rồi bấm đăng nhập lại trong cùng trình duyệt?

Đề xuất: phiên mới thay phiên cũ của trình duyệt đó; các thiết bị khác vẫn giữ phiên. FE chỉ giữ một tài khoản đang đăng nhập tại một thời điểm trong cùng trình duyệt.

Bro đồng ý không?

==> =))) Này chỉ có cố ý làm thông qua API thôi chứ FE thì nó có thiết kế để làm cái đó đâu fen =))) Thì nếu đăng nhập lại bằng API thì thay mới phiên cũ của trình duyệt đó là được. FE giữ bao nhiêu tài khoản trong cùng 1 trình duyệt cũng được, hiện tại chưa cần làm phức tạp nhé 

### S6. Đổi hoặc reset mật khẩu ảnh hưởng các phiên đang đăng nhập thế nào?

Đây là quy tắc của việc thay mật khẩu, khác với tính năng người dùng bấm “logout all” đã để sau.

Đề xuất: sau khi reset mật khẩu qua quên mật khẩu, thu hồi các phiên cũ và yêu cầu đăng nhập lại. Với đổi mật khẩu khi đang đăng nhập, bro muốn giữ phiên hiện tại hay cũng yêu cầu đăng nhập lại?

Mức vô hiệu hóa access token ngay hay chờ hết hạn cần thống nhất với S4.

==> À reset mật khẩu(quên mật khẩu) và đổi mật khẩu là 2 cái chức năng khác nhau nhé, cái này khi nào triển khai tới thì mình sẽ làm nhé. 


### S7. FE sẽ chạy ở đâu để chọn cách lưu/truyền token?

Bro cho biết framework FE nếu đã chọn, địa chỉ/port local dự kiến và khi deploy FE/BE có cùng domain không. Chưa biết thì ghi “chưa chốt”.

Tôi sẽ dựa vào đó đề xuất cách dùng cookie/body, CORS và xử lý refresh; bro không cần tự chọn thuật toán JWT hoặc các chi tiết thư viện ở bước này.

==> Hiện tại thì sẽ dùng cookie nhé, CORS và xử lý refresh, khi nào đụng tới thì ae debate tiếp nhé

## 2. Đăng ký, đăng nhập và khôi phục tài khoản

### A1. Đăng nhập bằng email hay có thêm username?

Đề xuất: email + mật khẩu; tên hiển thị trong game là field riêng, không dùng để đăng nhập. Google login để sau.

==> Chắc email + mật khẩu nhé, tên hiển thị thì riêng, dùng email tiện cho việc reset pass đồ 

### A2. Đăng ký cần nhập những thông tin nào?

Đề xuất: email, mật khẩu, xác nhận mật khẩu và tên hiển thị. Avatar, giới tính và ngày sinh bổ sung trong hồ sơ sau.
luôn
==> Sẽ bàn tới sau khi chốt debate nhé. 

### A3. Xác thực email là điều kiện bắt buộc để đăng nhập?

SPEC hiện đặt xác thực OTP trước đăng nhập. Đề xuất giữ quy tắc này: chưa xác thực thì chỉ được xác thực/gửi lại OTP, chưa được vào game.

Bro giữ quy tắc đó không? Khi xác thực thành công, muốn chuyển về màn đăng nhập hay tự đăng nhập luôn? Tôi đề xuất chuyển về màn đăng nhập.

==> Không nhé, đăng ký thì mới xác thực email thôi chứ đăng nhập mà xác thực email chi nữa :v Ngoại trừ việc gọi thư viện ngoài để làm auth. Email là để xác thực lúc đăng ký ấy và xác thực bằng OTP nhé. 

> **Xác nhận thêm của Khanh ngày 2026-10-07:** phải hoàn tất OTP email khi đăng ký trước khi được login. Login sau đó chỉ bằng email + mật khẩu, không yêu cầu OTP mỗi lần. Chuyển về màn login hay tự đăng nhập sau xác thực vẫn chưa được chốt.

### A4. OTP và gửi lại OTP dùng quy tắc nào?

Đề xuất ban đầu: OTP 6 chữ số, hạn 5 phút, tối đa 5 lần nhập sai cho mỗi mã; gửi lại cách nhau ít nhất 60 giây và mã mới thay mã cũ.

Bro đồng ý hay muốn thay các giá trị nào? Đây là đề xuất cấu hình, chưa phải hành vi đã có.

==> Um y như vậy đi, còn chi tiết logic flow như nào thì tính khi làm nhé

### A5. Đăng ký lại email chưa xác thực xử lý thế nào?

Đề xuất: không tạo tài khoản thứ hai và không tự thay mật khẩu/tên từ request mới; hướng dẫn tiếp tục xác thực hoặc gửi lại OTP. Nếu đã xác thực thì báo email đã được sử dụng.

==> Đúng rồi, sẽ hướng dẫn tiếp tục xác thực hoặc gửi lại OTP nhé, nếu xác thực rồi thì báo email đã được sử dụng.

### A6. Quy tắc mật khẩu và đăng nhập sai?

Bro muốn quy tắc mật khẩu như thế nào? Nếu chưa có yêu cầu, tôi sẽ đề xuất quy tắc tối thiểu cụ thể trước khi làm validation.

Với đăng nhập sai nhiều lần, bro muốn giới hạn thử và chờ tạm ngay ở phase đầu hay để cơ chế khóa tài khoản nâng cao sang phase sau? Không cần chọn thông số kỹ thuật ngay.

==> Hiện tại thì cứ giới hạn thử và chờ tạm trước, qua phase sau thì nâng cấp nó lên, nếu quá giới hạn thử thì khóa acc trong vòng 5 ngày hay j đó kiểu d :v 

### A7. Khôi phục mật khẩu bằng OTP email hay link reset?

Đề xuất dùng OTP email để cùng cách thao tác với xác thực đăng ký: nhập email → nhập OTP → đặt mật khẩu mới. Quy tắc phiên sau reset theo S6.

==> OTP email nhé như tôi nói ở trên

## 3. Quà lần đăng nhập đầu — trách nhiệm phía auth

### R1. Thời điểm tạo ví/Elo và mức quà lần đầu?

Đề xuất: auth phát `account.registered` sau khi xác thực email thành công, để các chủ service tạo ví/Elo; chỉ gọi cấp quà ở lần đăng nhập thành công đầu tiên. Mức quà giữ đề xuất hiện có là 500 xu.

Bro đồng ý thời điểm và mức quà không? Việc phát event cần thông báo cho Tùng/Hoài Anh; phần xử lý ví/Elo vẫn do họ làm.

+==> Đúng rồi, về việc quà lần đầu thì sau khi xác thực thành công, vào dashboard thì sẽ nhận quà lần đầu thôi ;v 

### R2. Ví chưa sẵn sàng hoặc cấp quà bị lỗi có chặn đăng nhập không?

Đề xuất: vẫn cho đăng nhập, ghi trạng thái quà đang chờ và tiếp tục xử lý với cùng key `first-login:{accountId}`. Chưa xác định cộng xu thành công thì không báo đã nhận quà. Hai thiết bị đăng nhập cũng chỉ được nhận một phần quà.

Bro muốn cách này hay yêu cầu cấp quà xong mới cho đăng nhập thành công?

==> Hiện tại thì cứ làm như trên là đăng nhập xong rồi mới nhận quà nhé đã nhé, và tất nhiên là 2 thiết bị đăng nhập cũng chỉ được nhận một phần quà thôi nhé, không được nhận 2 lần đâu fen.

## 4. Hồ sơ và điểm danh — có thể trả lời sau

### P1. Tên hiển thị có được trùng nhau và đổi được không?

Đề xuất: cho trùng, vì tài khoản xác định bằng ID/email; được sửa trong hồ sơ. Bro có muốn giới hạn số lần đổi hoặc có yêu cầu độ dài/nội dung tên không?

==> Um tên trùng không sao nhé. 

### P2. Người chơi khác được xem những thông tin hồ sơ nào?

Đề xuất profiles batch chỉ trả ID, tên hiển thị và avatar. Email/ngày sinh/giới tính chỉ chủ tài khoản xem, trừ khi bro muốn công khai thêm field nào. Màn hồ sơ lấy Elo và lịch sử trận từ các service tương ứng.

==> Thì xem thông tin profile đồ thôi, không cho xem thông tin nhạy cảm là được =)) Cái đó tính sau khi làm tới nhé fen

### P3. Avatar dùng upload hay chỉ chọn hình có sẵn?

SPEC có upload avatar. Bro giữ upload ngay khi làm hồ sơ đầy đủ hay muốn dùng avatar mặc định/hình có sẵn trước? Nếu upload, tôi đề xuất ban đầu chỉ PNG/JPEG, tối đa 2 MB; nơi lưu file sẽ bàn kỹ thuật riêng.

==> Có 2 option luôn cho nóng :v Cho chọn có sẵn or up hình luôn

### C1. Điểm danh có cần chuỗi ngày/quà tăng dần không?

Đề xuất phase đầu: mỗi ngày theo giờ Việt Nam được 50 xu cố định, không cần ngày liên tiếp và chưa có quà theo mốc. Điểm danh bù để sau như thứ tự đã ghi.

==> Hmm, hiện tại thì cứ cố định trước đã nhé.

### C2. Điểm danh lại hoặc wallet chưa cấp thưởng xong thì hiển thị gì?

Đề xuất: gọi lại trong cùng ngày trả trạng thái hiện tại, không cộng thêm thưởng. Nếu wallet lỗi, hiển thị “đã điểm danh, thưởng đang chờ” và auth tiếp tục xử lý với key ổn định; không bắt người chơi điểm danh lại để nhận thưởng.

Bro đồng ý hay muốn hiển thị/báo lỗi khác?

==> Chưa tính tới nhé 

## Chưa cần debate ở lượt này

- Logout all, danh sách thiết bị, giới hạn thiết bị và chế độ một phiên.
- Điểm danh bù: phạm vi ngày, phí và xử lý các bước thất bại.
- Báo cáo, xử phạt, Admin và Google login.
- Thuật toán JWT, khóa xác minh, chi tiết bảng/token, refresh đồng thời và cơ chế phát lại event: tôi sẽ đề xuất thiết kế sau khi các lựa chọn nghiệp vụ ở trên được chốt; không yêu cầu bro tự thiết kế.
- Quyền chơi cùng một tài khoản trên nhiều bàn/thiết bị là hợp đồng với game/matchmaking, không tự chốt trong auth.

Các phần này vẫn được theo dõi trong [definition.md](../definition.md). Khi bro trả lời, mình sẽ cập nhật quyết định vào đó và [SPEC](../../SPEC.md), không coi câu hỏi chưa trả lời là đã được đồng ý.
