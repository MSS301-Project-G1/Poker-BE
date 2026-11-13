package com.msspoker.chatservice.service.impl;

import com.msspoker.chatservice.dto.response.ConversationResponse;
import com.msspoker.chatservice.dto.response.PageResponse;
import com.msspoker.chatservice.entity.ConversationEntity;
import com.msspoker.chatservice.entity.ConversationMemberEntity;
import com.msspoker.chatservice.enums.ConversationType;
import com.msspoker.chatservice.exception.ChatApiException;
import com.msspoker.chatservice.mapper.ChatMapper;
import com.msspoker.chatservice.repository.ChatLockRepository;
import com.msspoker.chatservice.repository.ConversationRepository;
import com.msspoker.chatservice.repository.MemberRepository;
import com.msspoker.chatservice.service.ConversationService;
import com.msspoker.chatservice.validator.ChatAccessValidator;
import com.msspoker.chatservice.validator.ChatRequestValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationServiceImpl implements ConversationService {
    private final ConversationRepository conversations;
    private final MemberRepository members;
    private final ChatLockRepository locks;
    private final ChatAccessValidator access;
    private final ChatRequestValidator validation;
    private final ChatMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> memberIds(UUID conversationId) {
        return members.findByConversationIdOrderByAccountId(conversationId).stream()
                .map(ConversationMemberEntity::getAccountId)
                .toList();
    }

    private ConversationResponse view(ConversationEntity conversation) {
        return mapper.conversation(conversation,
                conversation.getType() == ConversationType.WORLD ? List.of() : memberIds(conversation.getId()));
    }

    @Override
    public ConversationResponse world(UUID actor) {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        locks.lock("world");
        var room = conversations.findById(id).orElseGet(() -> {
            var created = new ConversationEntity();
            created.setId(id);
            created.setType(ConversationType.WORLD);
            created.setCreatedAt(clock.instant());
            created.setUpdatedAt(clock.instant());
            return conversations.saveAndFlush(created);
        });
        if (!members.existsByConversationIdAndAccountId(id, actor)) addMember(id, actor);
        return view(room);
    }

    @Override
    public ConversationResponse direct(UUID accountId, UUID targetId) {
        if (accountId.equals(targetId))
            throw ChatApiException.invalid("Không chat DIRECT với chính mình.");
        access.friends(accountId, targetId);
        String key =
                accountId.toString().compareTo(targetId.toString()) < 0
                        ? accountId + ":" + targetId
                        : targetId + ":" + accountId;
        locks.lock("direct:" + key);
        var conversation =
                conversations
                        .findByDirectKeyAndIsDeletedFalse(key)
                        .orElseGet(
                                () -> {
                                    var newConversation = new ConversationEntity();
                                    newConversation.setId(UUID.randomUUID());
                                    newConversation.setType(ConversationType.DIRECT);
                                    newConversation.setDirectKey(key);
                                    newConversation.setCreatedAt(clock.instant());
                                    newConversation.setUpdatedAt(clock.instant());
                                    conversations.saveAndFlush(newConversation);
                                    addMember(newConversation.getId(), accountId);
                                    addMember(newConversation.getId(), targetId);
                                    return newConversation;
                                });
        return view(conversation);
    }

    @Override
    public void addMember(UUID conversation, UUID account) {
        var member = new ConversationMemberEntity();
        member.setId(UUID.randomUUID());
        member.setConversationId(conversation);
        member.setAccountId(account);
        member.setJoinedAt(clock.instant());
        members.saveAndFlush(member);
    }

    @Transactional(readOnly = true)
    @Override
    public ConversationResponse get(UUID accountId, UUID conversationId) {
        access.member(conversationId, accountId);
        return view(
                conversations
                        .findById(conversationId)
                        .filter(conversation -> !conversation.isDeleted())
                        .orElseThrow(ChatApiException::missing));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ConversationResponse> list(
            UUID accountId, int page, int size, String type) {
        validation.page(page, size);
        ConversationType conversationType = validation.conversationType(type);
        var pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        var result =
                type == null
                        ? conversations.mine(accountId, pageable)
                        : conversations.mineByType(accountId, conversationType, pageable);
        return new PageResponse<>(
                result.stream().map(this::view).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }
}
