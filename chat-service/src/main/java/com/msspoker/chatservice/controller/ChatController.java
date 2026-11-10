package com.msspoker.chatservice.controller;

import com.msspoker.chatservice.constant.ChatHeaders;
import com.msspoker.chatservice.dto.request.CreateDirectConversationRequest;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.dto.response.*;
import com.msspoker.chatservice.service.ChatService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** API hội thoại và tin nhắn; actor lấy từ header tin cậy, ChatService kiểm tra quyền thành viên. */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {
  private final ChatService chat;

  /** Lấy hoặc tạo hội thoại trực tiếp với tài khoản trong body theo điều kiện quan hệ Social. */
  @PostMapping("/direct")
  public ConversationResponse direct(
      @RequestHeader(ChatHeaders.USER_ID) UUID actor, @Valid @RequestBody CreateDirectConversationRequest r) {
    return chat.direct(actor, r.accountId());
  }

  /** Liệt kê hội thoại mà actor là thành viên, có phân trang. */
  @GetMapping
  public PageResponse<ConversationResponse> list(
      @RequestHeader(ChatHeaders.USER_ID) UUID actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String type) {
    return chat.list(actor, page, size, type);
  }

  /** Lấy hội thoại sau khi service kiểm tra quyền truy cập của actor. */
  @GetMapping("/{id}")
  public ConversationResponse get(@RequestHeader(ChatHeaders.USER_ID) UUID actor, @PathVariable UUID id) {
    return chat.get(actor, id);
  }

  /** Đọc lịch sử theo cursor before và limit; service kiểm tra thành viên và tham số. */
  @GetMapping("/{id}/messages")
  public MessageHistoryResponse history(
      @RequestHeader(ChatHeaders.USER_ID) UUID actor,
      @PathVariable UUID id,
      @RequestParam(required = false) String before,
      @RequestParam(defaultValue = "20") int limit) {
    return chat.history(actor, id, before, limit);
  }

  /** Gửi tin qua HTTP; dùng chung nghiệp vụ với WebSocket để kiểm tra quyền và chống trùng. */
  @PostMapping("/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public MessageResponse send(
      @RequestHeader(ChatHeaders.USER_ID) UUID actor,
      @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest r) {
    return chat.send(actor, id, r);
  }
}
