package com.msspoker.gameservice.exception;

import com.msspoker.gameservice.enums.GameError;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;

public final class GameExceptions {
    private GameExceptions() {
    }

    public static IllegalArgumentException invalidCard() {
        return new IllegalArgumentException("Lá bài phải có giá trị và chất.");
    }

    public static NoSuchElementException emptyDeck() {
        return new NoSuchElementException("Bộ bài đã hết.");
    }

    public static IllegalArgumentException invalidShowdownHand() {
        return new IllegalArgumentException("Cần đúng bảy lá bài khác nhau để so bài.");
    }

    public static IllegalArgumentException invalidHandRank() {
        return new IllegalArgumentException("Thứ hạng tay bài không hợp lệ.");
    }

    public static IllegalArgumentException invalidTable() {
        return new IllegalArgumentException("Số ghế hoặc vị trí người chia bài không hợp lệ.");
    }

    public static IllegalArgumentException invalidContribution() {
        return new IllegalArgumentException("Vị trí ghế hoặc lượng chip cược không hợp lệ.");
    }

    public static IllegalArgumentException invalidPot() {
        return new IllegalArgumentException("Pot phải có chip và ít nhất một người chưa bỏ bài.");
    }

    public static IllegalArgumentException invalidPotInput() {
        return new IllegalArgumentException("Dữ liệu chia pot không hợp lệ.");
    }

    public static IllegalArgumentException noEligiblePlayer() {
        return new IllegalArgumentException("Không có người chơi đủ điều kiện nhận pot.");
    }

    public static IllegalArgumentException missingShowdownHand(int seat) {
        return new IllegalArgumentException("Thiếu bài để so tại ghế " + seat + ".");
    }

    public static IllegalStateException chipConservationFailed() {
        return new IllegalStateException("Tổng chip sau khi chia không khớp với tổng cược.");
    }

    public static IllegalArgumentException invalidRules() {
        return new IllegalArgumentException("Cấu hình blind, chip hoặc số người không hợp lệ.");
    }

    public static IllegalArgumentException invalidPlayers() {
        return new IllegalArgumentException("Danh sách người chơi không hợp lệ hoặc có tài khoản trùng.");
    }

    public static IllegalStateException handStillRunning() {
        return new IllegalStateException("Ván hiện tại chưa kết thúc.");
    }

    public static IllegalStateException staleAction() {
        return new IllegalStateException("Lượt chơi đã thay đổi. Vui lòng đồng bộ lại bàn.");
    }

    public static IllegalArgumentException notYourTurn() {
        return new IllegalArgumentException("Chưa đến lượt của bạn.");
    }

    public static IllegalArgumentException invalidAction() {
        return new IllegalArgumentException("Hành động không hợp lệ.");
    }

    public static IllegalArgumentException cannotCheck() {
        return new IllegalArgumentException("Bạn cần theo cược hoặc bỏ bài.");
    }

    public static IllegalArgumentException invalidRaise() {
        return new IllegalArgumentException("Mức raise không hợp lệ hoặc quyền raise chưa được mở lại.");
    }

    public static GameApiException tableNotFound() {
        return new GameApiException(GameError.GAME_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy bàn chơi.");
    }

    public static GameApiException forbidden() {
        return new GameApiException(GameError.GAME_FORBIDDEN, HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập bàn hoặc chức năng này.");
    }

    public static GameApiException playerBusy() {
        return new GameApiException(GameError.GAME_PLAYER_BUSY, HttpStatus.CONFLICT, "Một người chơi đang ở bàn khác.");
    }

    public static GameApiException sourceConflict() {
        return new GameApiException(GameError.GAME_SOURCE_CONFLICT, HttpStatus.CONFLICT, "Nguồn tạo bàn đã được dùng với dữ liệu khác.");
    }

    public static GameApiException invalidRequest() {
        return new GameApiException(GameError.GAME_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ. Vui lòng kiểm tra thông tin gửi lên.");
    }

    public static GameApiException unauthorized() {
        return new GameApiException(GameError.GAME_UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
    }

    public static GameApiException dependencyUnavailable() {
        return new GameApiException(GameError.GAME_DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tài khoản tạm thời không khả dụng. Vui lòng thử lại.");
    }

    public static GameApiException internalError() {
        return new GameApiException(GameError.GAME_INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý yêu cầu lúc này. Vui lòng thử lại.");
    }

    public static IllegalStateException eventDeliveryFailed() {
        return new IllegalStateException("Event chưa được chuyển tới queue nhận. Hệ thống sẽ gửi lại.");
    }
}
