package com.msspoker.competitionservice.exception;

import com.msspoker.competitionservice.enums.CompetitionError;

import org.springframework.http.HttpStatus;

public final class CompetitionExceptions {
    private CompetitionExceptions() {
    }

    public static IllegalArgumentException invalidEvent() {
        return new IllegalArgumentException("Event không hợp lệ.");
    }

    public static IllegalStateException roomCodeExhausted() {
        return new IllegalStateException("Không sinh được mã phòng mới. Vui lòng thử lại.");
    }

    public static IllegalStateException eventDeliveryFailed() {
        return new IllegalStateException("Event chưa được chuyển tới queue nhận. Hệ thống sẽ gửi lại.");
    }

    public static CompetitionApiException roomNotFound() {
        return new CompetitionApiException(CompetitionError.COMP_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy phòng.");
    }

    public static CompetitionApiException notFound() {
        return new CompetitionApiException(CompetitionError.COMP_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên.");
    }

    public static CompetitionApiException notMember() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không ở trong phòng này.");
    }

    public static CompetitionApiException ownerOnly() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Chỉ chủ phòng được thực hiện thao tác này.");
    }

    public static CompetitionApiException notFriends() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Chỉ mời được bạn bè.");
    }

    public static CompetitionApiException wrongPassword() {
        return new CompetitionApiException(CompetitionError.COMP_WRONG_PASSWORD, HttpStatus.FORBIDDEN, "Mật khẩu phòng không đúng.");
    }

    public static CompetitionApiException roomFull() {
        return new CompetitionApiException(CompetitionError.COMP_ROOM_FULL, HttpStatus.CONFLICT, "Phòng đã đủ người.");
    }

    public static CompetitionApiException alreadyInRoom() {
        return new CompetitionApiException(CompetitionError.COMP_ALREADY_IN_ROOM, HttpStatus.CONFLICT, "Bạn đang ở trong một phòng khác. Hãy rời phòng đó trước.");
    }

    public static CompetitionApiException alreadyMember() {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Người chơi đã ở trong phòng.");
    }

    public static CompetitionApiException roomNotWaiting() {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Phòng đang trong trận, vui lòng chờ trận kết thúc.");
    }

    public static CompetitionApiException notEnoughPlayers(int minPlayers) {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Cần ít nhất " + minPlayers + " người để bắt đầu.");
    }

    public static CompetitionApiException playersNotReady() {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Còn người chơi chưa sẵn sàng.");
    }

    public static CompetitionApiException invalidTarget() {
        return new CompetitionApiException(CompetitionError.COMP_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Người chơi không hợp lệ cho thao tác này.");
    }

    public static CompetitionApiException invalidRoomSettings() {
        return new CompetitionApiException(CompetitionError.COMP_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Số người hoặc mức blind của phòng không hợp lệ.");
    }

    public static CompetitionApiException playerBusy() {
        return new CompetitionApiException(CompetitionError.COMP_PLAYER_BUSY, HttpStatus.CONFLICT, "Một người chơi trong phòng đang ở bàn khác.");
    }

    public static CompetitionApiException playerRestricted() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Một người chơi trong phòng đang bị hạn chế chơi.");
    }

    public static CompetitionApiException sourceConflict() {
        return new CompetitionApiException(CompetitionError.COMP_SOURCE_CONFLICT, HttpStatus.CONFLICT, "Bàn chơi của phòng đã thay đổi. Vui lòng thử lại.");
    }

    public static CompetitionApiException gameUnavailable() {
        return new CompetitionApiException(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Không mở được bàn chơi lúc này. Vui lòng thử lại.");
    }

    public static CompetitionApiException socialUnavailable() {
        return new CompetitionApiException(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Không kiểm tra được quan hệ bạn bè lúc này. Vui lòng thử lại.");
    }

    public static IllegalArgumentException tableResultMismatch() {
        return new IllegalArgumentException("Kết quả trận không khớp danh sách người chơi của bàn giải đấu.");
    }

    public static CompetitionApiException tournamentNotFound() {
        return new CompetitionApiException(CompetitionError.COMP_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu.");
    }

    public static CompetitionApiException adminOnly() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Chỉ quản trị viên được thực hiện thao tác này.");
    }

    public static CompetitionApiException inviteOnly() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Giải Cao thủ chỉ dành cho người chơi được mời.");
    }

    public static CompetitionApiException notInvited() {
        return new CompetitionApiException(CompetitionError.COMP_FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có lời mời tham dự giải này.");
    }

    public static CompetitionApiException inviteExpired() {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Lời mời tham dự giải đã hết hạn.");
    }

    public static CompetitionApiException eliteStartTooSoon(int inviteLeadMinutes) {
        return new CompetitionApiException(CompetitionError.COMP_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Giải Cao thủ phải bắt đầu sau ít nhất " + inviteLeadMinutes + " phút để kịp gửi lời mời.");
    }

    public static CompetitionApiException rankingUnavailable() {
        return new CompetitionApiException(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Không lấy được bảng xếp hạng lúc này. Hệ thống sẽ thử lại.");
    }

    public static CompetitionApiException registrationClosed() {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Giải đấu đã đóng đăng ký.");
    }

    public static CompetitionApiException tournamentNotScheduled() {
        return new CompetitionApiException(CompetitionError.COMP_CONFLICT, HttpStatus.CONFLICT, "Giải đấu đã bắt đầu hoặc đã kết thúc.");
    }

    public static CompetitionApiException tournamentFull() {
        return new CompetitionApiException(CompetitionError.COMP_TOURNAMENT_FULL, HttpStatus.CONFLICT, "Giải đấu đã đủ người đăng ký.");
    }

    public static CompetitionApiException invalidTournamentSettings(int maxSlots) {
        return new CompetitionApiException(CompetitionError.COMP_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Số suất của giải phải từ 2 đến " + maxSlots + ".");
    }

    public static CompetitionApiException insufficientBalance() {
        return new CompetitionApiException(CompetitionError.COMP_INSUFFICIENT_BALANCE, HttpStatus.CONFLICT, "Số dư xu không đủ để đăng ký giải.");
    }

    public static CompetitionApiException walletUnavailable() {
        return new CompetitionApiException(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Ví xu tạm thời không khả dụng. Vui lòng thử lại.");
    }

    public static CompetitionApiException unauthorized() {
        return new CompetitionApiException(CompetitionError.COMP_UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
    }

    public static CompetitionApiException invalidRequest() {
        return new CompetitionApiException(CompetitionError.COMP_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ. Vui lòng kiểm tra thông tin gửi lên.");
    }

    public static CompetitionApiException internalError() {
        return new CompetitionApiException(CompetitionError.COMP_INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý yêu cầu lúc này. Vui lòng thử lại.");
    }
}
