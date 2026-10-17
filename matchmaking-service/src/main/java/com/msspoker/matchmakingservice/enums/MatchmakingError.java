package com.msspoker.matchmakingservice.enums;

public enum MatchmakingError {
    // Request thiếu trường, sai định dạng hoặc có dữ liệu không hợp lệ.
    MM_INVALID_REQUEST,

    // Chưa đăng nhập hoặc thiếu thông tin xác thực người dùng.
    MM_UNAUTHORIZED,

    // Đã xác thực nhưng không có quyền thực hiện thao tác.
    MM_FORBIDDEN,

    // Không tìm thấy tài nguyên được yêu cầu.
    MM_NOT_FOUND,

    // Yêu cầu xung đột với trạng thái hiện tại của hệ thống.
    MM_CONFLICT,

    // Người chơi đã có mặt trong hàng chờ ghép trận.
    MM_ALREADY_QUEUED,

    // Người chơi đang có proposal chưa kết thúc.
    MM_ACTIVE_PROPOSAL,

    // Người chơi đang bị hạn chế tham gia chế độ Rank.
    MM_RANK_RESTRICTED,

    // Số dư không đủ để thanh toán phí tham gia trận.
    MM_INSUFFICIENT_BALANCE,

    // Proposal đã hết thời hạn chấp nhận.
    MM_PROPOSAL_EXPIRED,

    // Người chơi không phải thành viên của proposal.
    MM_NOT_PROPOSAL_MEMBER,

    // Proposal không còn ở trạng thái cho phép chấp nhận hoặc từ chối.
    MM_PROPOSAL_NOT_ACCEPTABLE,

    // Service phụ thuộc như Game, Wallet hoặc Ranking đang không khả dụng.
    MM_DEPENDENCY_UNAVAILABLE,

    // Chế độ ghép trận được yêu cầu chưa được triển khai hoặc đang tắt.
    MM_MODE_NOT_AVAILABLE,

    // Người chơi không có mặt trong hàng chờ.
    MM_NOT_QUEUED,

    // Người chơi đang được tạo bàn hoặc đang ở trong một trận.
    MM_MATCH_IN_PROGRESS,

    // Người chơi đã là thành viên của một party.
    MM_ALREADY_IN_PARTY,

    // Không tìm thấy party hoặc mã party không hợp lệ.
    MM_PARTY_NOT_FOUND,

    // Người chơi không phải thành viên của party.
    MM_NOT_PARTY_MEMBER,

    // Chỉ chủ party được thực hiện thao tác này.
    MM_NOT_PARTY_LEADER,

    // Party đã đạt số thành viên tối đa.
    MM_PARTY_FULL,

    // Người chơi chưa được mời vào party.
    MM_PARTY_INVITE_REQUIRED,

    // Lỗi nội bộ không xác định của Matchmaking service.
    MM_INTERNAL_ERROR
}
