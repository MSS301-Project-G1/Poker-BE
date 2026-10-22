package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ChatMessage;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {
  Optional<ChatMessage> findBySenderIdAndClientMessageId(UUID sender, UUID client);

  @Query(
      "select m from ChatMessage m where m.conversationId=:conversation order by m.createdAt"
          + " desc,m.id desc")
  List<ChatMessage> latest(UUID conversation, Pageable page);

  @Query(
      "select m from ChatMessage m where m.conversationId=:conversation and (m.createdAt<:before or"
          + " (m.createdAt=:before and m.id<:id)) order by m.createdAt desc,m.id desc")
  List<ChatMessage> before(UUID conversation, Instant before, UUID id, Pageable page);
}
