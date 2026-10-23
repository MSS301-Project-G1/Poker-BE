package com.msspoker.chatservice.service.impl;

import com.msspoker.chatservice.service.*;
import com.msspoker.chatservice.entity.Conversation;
import com.msspoker.chatservice.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchLifecycleServiceImpl implements MatchLifecycleService {
  private final ChatLockRepository locks;
  private final ConversationRepository conversations;
  private final MemberRepository members;
  private final ChatService chat;
  private final Clock clock;

  @Override
  public void started(UUID match, List<UUID> players) {
    Instant finished = locks.lifecycle(match);
    var existing = conversations.findByTypeAndRefIdAndIsDeletedFalse("MATCH", match);
    if (existing.isPresent()) {
      var actual =
          members.findByConversationIdOrderByAccountId(existing.get().getId()).stream()
              .map(m -> m.getAccountId())
              .collect(java.util.stream.Collectors.toSet());
      if (!actual.equals(new HashSet<>(players)))
        throw new IllegalArgumentException("Conflicting match membership");
      return;
    }
    var c = new Conversation();
    c.setId(UUID.randomUUID());
    c.setType("MATCH");
    c.setRefId(match);
    c.setClosedAt(finished);
    c.setCreatedAt(clock.instant());
    c.setUpdatedAt(clock.instant());
    conversations.saveAndFlush(c);
    players.forEach(id -> chat.addMember(c.getId(), id));
  }

  @Override
  public void finished(UUID match, Instant at) {
    locks.lifecycle(match);
    locks.finish(match, at);
    conversations
        .findByTypeAndRefIdAndIsDeletedFalse("MATCH", match)
        .ifPresent(
            c -> {
              var row = conversations.locked(c.getId()).orElseThrow();
              if (row.getClosedAt() == null) {
                row.setClosedAt(at);
                row.setUpdatedAt(clock.instant());
              }
            });
  }
}
