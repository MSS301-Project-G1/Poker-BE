package com.msspoker.gameservice.exception;

import java.util.NoSuchElementException;

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
}
