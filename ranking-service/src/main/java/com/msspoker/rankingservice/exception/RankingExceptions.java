package com.msspoker.rankingservice.exception;

import com.msspoker.rankingservice.enums.RankingError;

import org.springframework.http.HttpStatus;

public final class RankingExceptions {
    private RankingExceptions() {
    }

    public static IllegalArgumentException invalidEvent() {
        return new IllegalArgumentException("Event không hợp lệ.");
    }

    public static IllegalArgumentException invalidMatchResult() {
        return new IllegalArgumentException("Kết quả trận không hợp lệ: thứ hạng phải duy nhất từ 1 đến số người chơi.");
    }

    public static IllegalArgumentException invalidPlace() {
        return new IllegalArgumentException("Thứ hạng phải bắt đầu từ 1.");
    }

    public static RankingApiException noActiveSeason() {
        return new RankingApiException(RankingError.RANK_NOT_FOUND, HttpStatus.NOT_FOUND, "Chưa có mùa giải đang diễn ra.");
    }

    public static IllegalStateException eventDeliveryFailed() {
        return new IllegalStateException("Event chưa được chuyển tới queue nhận. Hệ thống sẽ gửi lại.");
    }

    public static RankingApiException seasonNotFound() {
        return new RankingApiException(RankingError.RANK_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy mùa giải.");
    }

    public static RankingApiException noNextSeason() {
        return new RankingApiException(RankingError.RANK_CONFLICT, HttpStatus.CONFLICT, "Chưa có mùa kế tiếp. Hãy tạo mùa mới trước khi đóng mùa hiện tại.");
    }

    public static RankingApiException seasonNotEnded() {
        return new RankingApiException(RankingError.RANK_CONFLICT, HttpStatus.CONFLICT, "Mùa giải chưa kết thúc.");
    }

    public static RankingApiException seasonRewardsLocked() {
        return new RankingApiException(RankingError.RANK_CONFLICT, HttpStatus.CONFLICT, "Không thể đổi quà của mùa đã kết thúc.");
    }

    public static RankingApiException duplicateTierReward() {
        return new RankingApiException(RankingError.RANK_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Mỗi bậc chỉ có một mức quà xu và không lặp vật phẩm.");
    }

    public static RankingApiException noSeasonReward() {
        return new RankingApiException(RankingError.RANK_NOT_FOUND, HttpStatus.NOT_FOUND, "Bạn không có quà ở mùa giải này.");
    }

    public static RankingApiException adminOnly() {
        return new RankingApiException(RankingError.RANK_FORBIDDEN, HttpStatus.FORBIDDEN, "Chỉ quản trị viên được thực hiện thao tác này.");
    }

    public static RankingApiException notFound() {
        return new RankingApiException(RankingError.RANK_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên.");
    }

    public static RankingApiException socialUnavailable() {
        return new RankingApiException(RankingError.RANK_DEPENDENCY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Không lấy được danh sách bạn bè lúc này. Vui lòng thử lại.");
    }

    public static RankingApiException unauthorized() {
        return new RankingApiException(RankingError.RANK_UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
    }

    public static RankingApiException invalidRequest() {
        return new RankingApiException(RankingError.RANK_INVALID_REQUEST, HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ. Vui lòng kiểm tra thông tin gửi lên.");
    }

    public static RankingApiException internalError() {
        return new RankingApiException(RankingError.RANK_INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý yêu cầu lúc này. Vui lòng thử lại.");
    }
}
