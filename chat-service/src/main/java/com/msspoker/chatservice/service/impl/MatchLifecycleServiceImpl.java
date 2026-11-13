package com.msspoker.chatservice.service.impl;

import com.msspoker.chatservice.entity.ConversationEntity;
import com.msspoker.chatservice.enums.ConversationType;
import com.msspoker.chatservice.repository.ConversationRepository;
import com.msspoker.chatservice.repository.MatchLifecycleRepository;
import com.msspoker.chatservice.repository.MemberRepository;
import com.msspoker.chatservice.service.ConversationService;
import com.msspoker.chatservice.service.MatchLifecycleService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchLifecycleServiceImpl implements MatchLifecycleService {
    private final MatchLifecycleRepository lifecycles;
    private final ConversationRepository conversations;
    private final MemberRepository members;
    private final ConversationService conversationsService;
    private final Clock clock;

    @Override
    public void started(UUID matchId, List<UUID> playerIds) {
        Instant finished = lifecycles.lock(matchId).getFinishedAt();
        var existing =
                conversations.findByTypeAndRefIdAndIsDeletedFalse(ConversationType.MATCH, matchId);
        if (existing.isPresent()) {
            var memberIds =
                    members.findByConversationIdOrderByAccountId(existing.get().getId()).stream()
                            .map(member -> member.getAccountId())
                            .collect(java.util.stream.Collectors.toSet());
            if (!memberIds.equals(new HashSet<>(playerIds)))
                throw new IllegalArgumentException("Conflicting match membership");
            return;
        }
        var conversation = new ConversationEntity();
        conversation.setId(UUID.randomUUID());
        conversation.setType(ConversationType.MATCH);
        conversation.setRefId(matchId);
        conversation.setClosedAt(finished);
        conversation.setCreatedAt(clock.instant());
        conversation.setUpdatedAt(clock.instant());
        conversations.saveAndFlush(conversation);
        playerIds.forEach(id -> conversationsService.addMember(conversation.getId(), id));
    }

    @Override
    public void finished(UUID matchId, Instant finishedAt) {
        lifecycles.lock(matchId).finish(finishedAt);
        conversations
                .findByTypeAndRefIdAndIsDeletedFalse(ConversationType.MATCH, matchId)
                .ifPresent(
                        conversation -> {
                            var lockedConversation =
                                    conversations.locked(conversation.getId()).orElseThrow();
                            if (lockedConversation.getClosedAt() == null) {
                                lockedConversation.setClosedAt(finishedAt);
                                lockedConversation.setUpdatedAt(clock.instant());
                            }
                        });
    }
}
