package com.msspoker.gameservice.realtime;

import com.msspoker.gameservice.mapper.SnapshotMapper;
import com.msspoker.gameservice.service.ManagedTable;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SnapshotPublisher {
    private final SnapshotMapper mapper;
    private final SimpMessagingTemplate messaging;

    public void allPlayers(ManagedTable table) {
        table.getEngine().getSeats().forEach(seat -> messaging.convertAndSendToUser(seat.getAccountId().toString(),
                "/queue/tables/" + table.getEngine().getTableId(), mapper.snapshot(table, seat.getAccountId())));
    }

    public void onePlayer(ManagedTable table, UUID accountId) {
        messaging.convertAndSendToUser(accountId.toString(), "/queue/tables/" + table.getEngine().getTableId(),
                mapper.snapshot(table, accountId));
    }
}
