package com.msspoker.gameservice.realtime;

import com.msspoker.gameservice.api.GameExceptionHandler.ErrorResponse;
import com.msspoker.gameservice.exception.*;
import com.msspoker.gameservice.service.GameplayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameStompController {
    private final GameplayService gameplay;

    @MessageMapping("/tables/{tableId}/action")
    public void action(@DestinationVariable UUID tableId, @Valid ActionRequest request, Principal user) {
        gameplay.action(tableId, UUID.fromString(user.getName()), request);
    }

    @MessageMapping("/tables/{tableId}/sync")
    public void sync(@DestinationVariable UUID tableId, Principal user) {
        gameplay.sync(tableId, UUID.fromString(user.getName()));
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/game-errors", broadcast = false)
    public ErrorResponse error(Exception ex) {
        if (ex instanceof GameApiException business) {
            return new ErrorResponse(business.getCode(), business.getMessage(), Instant.now(), null);
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return new ErrorResponse(GameError.GAME_INVALID_ACTION, ex.getMessage(), Instant.now(), null);
        }
        return new ErrorResponse(GameError.GAME_INVALID_REQUEST, GameExceptions.invalidRequest().getMessage(), Instant.now(), null);
    }
}
