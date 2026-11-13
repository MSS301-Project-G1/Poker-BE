package com.msspoker.chatservice.validator;

import com.msspoker.chatservice.config.ChatProperties;
import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.enums.ConversationType;
import com.msspoker.chatservice.exception.ChatApiException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRequestValidator {
    private final ChatProperties properties;

    public void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ChatApiException.invalid("Phân trang không hợp lệ.");
        }
    }

    public ConversationType conversationType(String type) {
        if (type == null) return null;
        try {
            return ConversationType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            throw ChatApiException.invalid("Loại hội thoại không hợp lệ.");
        }
    }

    public String content(SendMessageRequest request) {
        if (request == null || !"TEXT".equals(request.messageType()) || request.content() == null) {
            throw ChatApiException.invalid("MVP chỉ hỗ trợ TEXT.");
        }
        String content = request.content().strip();
        if (content.isEmpty() || content.length() > properties.getMaxContentLength()) {
            throw ChatApiException.invalid("Nội dung rỗng hoặc vượt giới hạn.");
        }
        return content;
    }

    public void historyLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw ChatApiException.invalid("limit từ 1 đến 100.");
        }
    }
}
