package com.msspoker.chatservice.mapper;

import com.msspoker.chatservice.dto.response.ConversationResponse;
import com.msspoker.chatservice.dto.response.MessageResponse;
import com.msspoker.chatservice.entity.ChatMessageEntity;
import com.msspoker.chatservice.entity.ConversationEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ChatMapper {
    MessageResponse message(ChatMessageEntity entity);

    @Mapping(target = "memberIds", source = "members")
    ConversationResponse conversation(ConversationEntity entity, List<UUID> members);
}
