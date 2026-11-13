package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ChatMessageEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    Optional<ChatMessageEntity> findBySenderIdAndClientMessageId(UUID sender, UUID client);

    @Query(
            "select m from ChatMessageEntity m where m.conversationId=:conversation order by"
                + " m.createdAt desc,m.id desc")
    List<ChatMessageEntity> latest(UUID conversation, Pageable page);

    @Query(
            "select m from ChatMessageEntity m where m.conversationId=:conversation and"
                + " (m.createdAt<:before or (m.createdAt=:before and m.id<:id)) order by"
                + " m.createdAt desc,m.id desc")
    List<ChatMessageEntity> before(UUID conversation, Instant before, UUID id, Pageable page);
}
