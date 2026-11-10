package com.msspoker.gameservice.service.impl;

import com.msspoker.gameservice.client.ShopSoundClient;
import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.dto.request.TauntRequest;
import com.msspoker.gameservice.dto.response.TauntResponse;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.Seat;
import com.msspoker.gameservice.service.TableRegistry;
import com.msspoker.gameservice.service.TauntService;
import com.msspoker.gameservice.websocket.TauntPublisher;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Any seated player, folded or eliminated, may taunt; one who left the table may not. */
@Service
@RequiredArgsConstructor
public class TauntServiceImpl implements TauntService {
    private final TableRegistry registry;
    private final ShopSoundClient shop;
    private final TauntPublisher publisher;
    private final GameProperties properties;

    @Override
    public void taunt(UUID tableId, UUID accountId, TauntRequest request) {
        ManagedTable table = registry.require(tableId);
        table.getLock().lock();
        try {
            Seat seat = table.getEngine().getSeats().stream().filter(s -> s.getAccountId().equals(accountId)).findFirst()
                    .orElseThrow(GameExceptions::forbidden);
            if (seat.isLeftEarly()) throw GameExceptions.tauntAfterLeaving();
            Instant now = Instant.now();
            Instant last = table.getLastTaunts().get(accountId);
            if (last != null && now.isBefore(last.plusMillis(properties.getTauntCooldownMillis()))) {
                throw GameExceptions.tauntCooldown();
            }
            // Counted before the shop call, so a refused or failed lookup still waits out the cooldown.
            table.getLastTaunts().put(accountId, now);
        } finally {
            table.getLock().unlock();
        }
        // Outside the lock: a slow shop must not hold up the table's actions and timers.
        String soundUrl = shop.soundUrl(accountId, request.productId());
        publisher.allPlayers(table, new TauntResponse(accountId, request.productId(), soundUrl));
    }
}
