package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ConversationEntity;
import com.msspoker.chatservice.enums.ConversationType;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    Optional<ConversationEntity> findByDirectKeyAndIsDeletedFalse(String key);

    Optional<ConversationEntity> findByTypeAndRefIdAndIsDeletedFalse(
            ConversationType type, UUID ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.id=:id and c.isDeleted=false")
    Optional<ConversationEntity> locked(UUID id);

    @Query(
            "select c from ConversationEntity c where c.isDeleted=false and c.id in (select"
                + " m.conversationId from ConversationMemberEntity m where m.accountId=:actor)")
    Page<ConversationEntity> mine(UUID actor, Pageable page);

    @Query(
            "select c from ConversationEntity c where c.isDeleted=false and c.type=:type and c.id"
                + " in (select m.conversationId from ConversationMemberEntity m where"
                + " m.accountId=:actor)")
    Page<ConversationEntity> mineByType(UUID actor, ConversationType type, Pageable page);
}
