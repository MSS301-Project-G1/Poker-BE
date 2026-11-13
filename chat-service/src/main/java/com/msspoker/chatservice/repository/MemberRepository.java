package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ConversationMemberEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<ConversationMemberEntity, UUID> {
    boolean existsByConversationIdAndAccountId(UUID conversation, UUID account);

    List<ConversationMemberEntity> findByConversationIdOrderByAccountId(UUID id);
}
