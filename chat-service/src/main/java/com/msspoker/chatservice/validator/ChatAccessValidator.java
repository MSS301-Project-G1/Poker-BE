package com.msspoker.chatservice.validator;

import com.msspoker.chatservice.exception.ServiceException;
import com.msspoker.chatservice.repository.MemberRepository;
import com.msspoker.chatservice.service.client.SocialClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatAccessValidator {
  private final MemberRepository members;
  private final SocialClient social;

  public void member(UUID conversation, UUID actor) {
    if (!members.existsByConversationIdAndAccountId(conversation, actor))
      throw ServiceException.forbidden();
  }

  public void friends(UUID a, UUID b) {
    var relation = social.relation(a, b);
    if (!relation.isFriend() || relation.isBlocked()) throw ServiceException.forbidden();
  }
}
