package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ConversationMember;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<ConversationMember, UUID> {
  boolean existsByConversationIdAndAccountId(UUID conversation, UUID account);

  List<ConversationMember> findByConversationIdOrderByAccountId(UUID id);
}
