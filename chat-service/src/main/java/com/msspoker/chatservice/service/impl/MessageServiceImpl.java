package com.msspoker.chatservice.service.impl;

import com.msspoker.chatservice.dto.event.MessageCommittedEvent;
import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.dto.response.MessageHistoryResponse;
import com.msspoker.chatservice.dto.response.MessageResponse;
import com.msspoker.chatservice.entity.ChatMessageEntity;
import com.msspoker.chatservice.enums.ConversationType;
import com.msspoker.chatservice.enums.MessageType;
import com.msspoker.chatservice.exception.ChatApiException;
import com.msspoker.chatservice.mapper.ChatMapper;
import com.msspoker.chatservice.repository.ChatLockRepository;
import com.msspoker.chatservice.repository.ConversationRepository;
import com.msspoker.chatservice.repository.MessageRepository;
import com.msspoker.chatservice.service.ConversationService;
import com.msspoker.chatservice.service.MessageService;
import com.msspoker.chatservice.validator.ChatAccessValidator;
import com.msspoker.chatservice.validator.ChatRequestValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ChatLockRepository locks;
    private final ChatAccessValidator access;
    private final ChatMapper mapper;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final ChatRequestValidator validation;
    private final ConversationService conversationService;

    @Override
    public MessageResponse send(UUID accountId, UUID conversationId, SendMessageRequest request) {
        String content = validation.content(request);
        if (request.clientMessageId() != null) {
            locks.lock("message:" + accountId + ":" + request.clientMessageId());
            var existingMessage =
                    messages.findBySenderIdAndClientMessageId(accountId, request.clientMessageId());
            if (existingMessage.isPresent()) {
                var previousMessage = existingMessage.get();
                if (!previousMessage.getConversationId().equals(conversationId)
                        || !previousMessage.getContent().equals(content))
                    throw ChatApiException.conflict("clientMessageId đã dùng cho nội dung khác.");
                return mapper.message(previousMessage);
            }
        }
        var conversation =
                conversations.locked(conversationId).orElseThrow(ChatApiException::missing);
        access.member(conversationId, accountId);
        if (conversation.getClosedAt() != null)
            throw ChatApiException.conflict("Phòng chat đã đóng.");
        var recipients = conversationService.memberIds(conversationId);
        if (conversation.getType() == ConversationType.DIRECT) {
            if (recipients.size() != 2) throw new IllegalStateException("Invalid DIRECT members");
            access.friends(
                    accountId,
                    recipients.stream()
                            .filter(memberId -> !memberId.equals(accountId))
                            .findFirst()
                            .orElseThrow());
        }
        var message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setSenderId(accountId);
        message.setMessageType(MessageType.TEXT);
        message.setContent(content);
        message.setClientMessageId(request.clientMessageId());
        message.setCreatedAt(clock.instant());
        messages.saveAndFlush(message);
        conversation.setUpdatedAt(clock.instant());
        var response = mapper.message(message);
        events.publishEvent(new MessageCommittedEvent(response, recipients));
        return response;
    }

    @Transactional(readOnly = true)
    @Override
    public MessageHistoryResponse history(
            UUID accountId, UUID conversationId, String before, int limit) {
        access.member(conversationId, accountId);
        validation.historyLimit(limit);
        var page = PageRequest.of(0, limit + 1);
        List<ChatMessageEntity> rows;
        if (before == null) rows = messages.latest(conversationId, page);
        else {
            try {
                String[] cursor =
                        new String(Base64.getUrlDecoder().decode(before), StandardCharsets.UTF_8)
                                .split("[|]", -1);
                if (cursor.length != 2) throw new IllegalArgumentException();
                rows =
                        messages.before(
                                conversationId,
                                Instant.parse(cursor[0]),
                                UUID.fromString(cursor[1]),
                                page);
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
                throw ChatApiException.invalid("Cursor không hợp lệ.");
            }
        }
        boolean more = rows.size() > limit;
        var items = rows.stream().limit(limit).map(mapper::message).toList();
        String next = null;
        if (more) {
            var last = items.getLast();
            next =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    (last.createdAt() + "|" + last.id())
                                            .getBytes(StandardCharsets.UTF_8));
        }
        return new MessageHistoryResponse(items, next);
    }
}
