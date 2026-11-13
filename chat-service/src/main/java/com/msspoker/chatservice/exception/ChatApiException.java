package com.msspoker.chatservice.exception;

import com.msspoker.chatservice.enums.ChatError;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
public class ChatApiException extends RuntimeException {
    private final ChatError code;
    private final HttpStatus status;

    public ChatApiException(ChatError code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ChatApiException invalid(String message) {
        return new ChatApiException(
                ChatError.CHAT_INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
    }

    public static ChatApiException forbidden() {
        return new ChatApiException(
                ChatError.CHAT_FORBIDDEN,
                HttpStatus.FORBIDDEN,
                "Không có quyền thực hiện thao tác.");
    }

    public static ChatApiException contentNotAllowed() {
        return new ChatApiException(
                ChatError.CHAT_CONTENT_NOT_ALLOWED,
                HttpStatus.BAD_REQUEST,
                "Tin nhắn có từ ngữ không phù hợp. Vui lòng chỉnh sửa.");
    }

    public static ChatApiException missing() {
        return new ChatApiException(
                ChatError.CHAT_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên.");
    }

    public static ChatApiException conflict(String message) {
        return new ChatApiException(ChatError.CHAT_CONFLICT, HttpStatus.CONFLICT, message);
    }
}
