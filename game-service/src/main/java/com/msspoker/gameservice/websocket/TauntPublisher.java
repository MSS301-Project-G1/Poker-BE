package com.msspoker.gameservice.websocket;

import com.msspoker.gameservice.constant.GameSocketConstants;
import com.msspoker.gameservice.dto.response.TauntResponse;
import com.msspoker.gameservice.model.game.ManagedTable;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TauntPublisher {
    private final SimpMessagingTemplate messaging;

    public void allPlayers(ManagedTable table, TauntResponse taunt) {
        String destination = GameSocketConstants.TABLE_QUEUE + table.getEngine().getTableId() + GameSocketConstants.TAUNT_QUEUE_SUFFIX;
        table.getEngine().getSeats().forEach(seat -> messaging.convertAndSendToUser(seat.getAccountId().toString(), destination, taunt));
    }
}
