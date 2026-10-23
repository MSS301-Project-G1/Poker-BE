package com.msspoker.chatservice.mapper;

import com.msspoker.chatservice.dto.response.*;
import com.msspoker.chatservice.entity.*;
import java.util.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChatMapper {
  MessageResponse message(ChatMessage entity);

  @Mapping(target = "memberIds", source = "members")
  ConversationResponse conversation(Conversation entity, List<UUID> members);
}
