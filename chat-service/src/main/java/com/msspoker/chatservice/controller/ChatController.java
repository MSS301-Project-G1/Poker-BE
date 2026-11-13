package com.msspoker.chatservice.controller;

import com.msspoker.chatservice.constant.ChatHeaders;
import com.msspoker.chatservice.dto.request.CreateDirectConversationRequest;
import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.dto.response.ConversationResponse;
import com.msspoker.chatservice.dto.response.MessageHistoryResponse;
import com.msspoker.chatservice.dto.response.MessageResponse;
import com.msspoker.chatservice.dto.response.PageResponse;
import com.msspoker.chatservice.service.ConversationService;
import com.msspoker.chatservice.service.MessageService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REST transport for conversations and messages; services enforce membership. */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {
    private final ConversationService conversations;
    private final MessageService messages;

    /** Lấy hoặc tạo hội thoại trực tiếp với tài khoản trong body theo điều kiện quan hệ Social. */
    @PostMapping("/direct")
    public ConversationResponse direct(
            @RequestHeader(ChatHeaders.USER_ID) UUID actor,
            @Valid @RequestBody CreateDirectConversationRequest r) {
        return conversations.direct(actor, r.accountId());
    }

    /** Liệt kê hội thoại mà actor là thành viên, có phân trang. */
    @GetMapping
    public PageResponse<ConversationResponse> list(
            @RequestHeader(ChatHeaders.USER_ID) UUID actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        return conversations.list(actor, page, size, type);
    }

    /** Lấy hội thoại sau khi service kiểm tra quyền truy cập của actor. */
    @GetMapping("/{id}")
    public ConversationResponse get(
            @RequestHeader(ChatHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
        return conversations.get(actor, id);
    }

    /** Đọc lịch sử theo cursor before và limit; service kiểm tra thành viên và tham số. */
    @GetMapping("/{id}/messages")
    public MessageHistoryResponse history(
            @RequestHeader(ChatHeaders.USER_ID) UUID actor,
            @PathVariable UUID id,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "20") int limit) {
        return messages.history(actor, id, before, limit);
    }

    /** Gửi tin qua HTTP; dùng chung nghiệp vụ với WebSocket để kiểm tra quyền và chống trùng. */
    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(
            @RequestHeader(ChatHeaders.USER_ID) UUID actor,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest r) {
        return messages.send(actor, id, r);
    }
}
