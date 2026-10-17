package com.msspoker.gameservice.exception;

import com.msspoker.common.response.ErrorResponse;
import com.msspoker.gameservice.constant.GameSocketConstants;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.gameservice.mapper.ErrorMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** Business errors of a STOMP action go to the sender's private error queue; the socket stays open. */
@ControllerAdvice
@RequiredArgsConstructor
public class GameStompExceptionHandler {
    private final ErrorMapper errors;

    @MessageExceptionHandler(GameApiException.class)
    @SendToUser(destinations = GameSocketConstants.ERROR_QUEUE, broadcast = false)
    public ErrorResponse business(GameApiException ex) {
        return errors.error(ex, null);
    }

    @MessageExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @SendToUser(destinations = GameSocketConstants.ERROR_QUEUE, broadcast = false)
    public ErrorResponse action(RuntimeException ex) {
        return errors.error(GameError.GAME_INVALID_ACTION, ex.getMessage(), null);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = GameSocketConstants.ERROR_QUEUE, broadcast = false)
    public ErrorResponse invalid(Exception ex) {
        return errors.error(GameExceptions.invalidRequest(), null);
    }
}
