package com.msspoker.gameservice.dto.response;

import java.util.UUID;

/** Sent to every seat at the table when a player plays a taunt sound. */
public record TauntResponse(UUID accountId, UUID productId, String soundUrl) {
}
