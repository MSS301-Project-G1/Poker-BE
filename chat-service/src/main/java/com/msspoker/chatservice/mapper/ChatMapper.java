package com.msspoker.chatservice.mapper;

import com.msspoker.chatservice.dto.response.ChatDtos.*;
import com.msspoker.chatservice.entity.*;
import java.util.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChatMapper {
  MessageView message(ChatMessage entity);

  @Mapping(target = "memberIds", source = "members")
  ConversationView conversation(Conversation entity, List<UUID> members);
}
