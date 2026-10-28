package com.msspoker.gameservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Only the product id: the sound file is looked up server-side so a player cannot make the table play any URL. */
public record TauntRequest(@NotNull UUID productId) {
}
