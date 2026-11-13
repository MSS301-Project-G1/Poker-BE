package com.msspoker.chatservice.dto.event;

import com.msspoker.chatservice.dto.response.MessageResponse;

import java.util.List;
import java.util.UUID;

public record MessageCommittedEvent(MessageResponse message, List<UUID> members) {}
