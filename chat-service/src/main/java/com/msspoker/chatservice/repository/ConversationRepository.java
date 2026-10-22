package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.Conversation;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  Optional<Conversation> findByDirectKeyAndIsDeletedFalse(String key);

  Optional<Conversation> findByTypeAndRefIdAndIsDeletedFalse(String type, UUID ref);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Conversation c where c.id=:id and c.isDeleted=false")
  Optional<Conversation> locked(UUID id);

  @Query(
      "select c from Conversation c where c.isDeleted=false and c.id in (select m.conversationId"
          + " from ConversationMember m where m.accountId=:actor)")
  Page<Conversation> mine(UUID actor, Pageable page);
}
