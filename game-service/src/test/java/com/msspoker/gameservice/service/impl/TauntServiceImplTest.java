package com.msspoker.gameservice.service.impl;

import com.msspoker.gameservice.client.ShopSoundClient;
import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.dto.request.TauntRequest;
import com.msspoker.gameservice.dto.response.TauntResponse;
import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.PokerTable;
import com.msspoker.gameservice.model.game.TableRules;
import com.msspoker.gameservice.service.TableRegistry;
import com.msspoker.gameservice.service.impl.poker.HandEvaluatorImpl;
import com.msspoker.gameservice.service.impl.poker.PotDistributorImpl;
import com.msspoker.gameservice.websocket.TauntPublisher;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TauntServiceImplTest {
    private static final TableRules RULES = new TableRules(10, 20, 1000, 20, 2, 6);
    private static final String SOUND_URL = "https://bucket.example/poker/shop/seed/sounds/x/sound.mp3?X-Amz-Signature=abc";

    private final TableRegistry registry = mock(TableRegistry.class);
    private final ShopSoundClient shop = mock(ShopSoundClient.class);
    private final TauntPublisher publisher = mock(TauntPublisher.class);
    private final GameProperties properties = new GameProperties();
    private final TauntServiceImpl taunts = new TauntServiceImpl(registry, shop, publisher, properties);
    private final UUID sound = UUID.randomUUID();

    @Test
    void ownedSoundIsSentToTheWholeTableWithTheUrlFromShop() {
        ManagedTable table = table();
        UUID player = player(table, 0);
        when(shop.soundUrl(player, sound)).thenReturn(SOUND_URL);

        taunts.taunt(table.getEngine().getTableId(), player, new TauntRequest(sound));

        verify(publisher).allPlayers(table, new TauntResponse(player, sound, SOUND_URL));
    }

    @Test
    void secondTauntInsideTheCooldownIsRefusedWithoutAskingShopAndAllowedOnceItPasses() {
        ManagedTable table = table();
        UUID player = player(table, 0);
        when(shop.soundUrl(player, sound)).thenReturn(SOUND_URL);
        taunts.taunt(table.getEngine().getTableId(), player, new TauntRequest(sound));

        GameApiException refused = assertThrows(GameApiException.class,
                () -> taunts.taunt(table.getEngine().getTableId(), player, new TauntRequest(sound)));
        assertEquals(GameError.GAME_TAUNT_COOLDOWN, refused.getCode());
        verify(shop, times(1)).soundUrl(player, sound);

        table.getLastTaunts().put(player, Instant.now().minusMillis(properties.getTauntCooldownMillis()));
        taunts.taunt(table.getEngine().getTableId(), player, new TauntRequest(sound));
        verify(publisher, times(2)).allPlayers(eq(table), any());
    }

    @Test
    void cooldownIsPerPlayerSoOthersMayAnswerAtOnce() {
        ManagedTable table = table();
        when(shop.soundUrl(any(), eq(sound))).thenReturn(SOUND_URL);

        taunts.taunt(table.getEngine().getTableId(), player(table, 0), new TauntRequest(sound));
        taunts.taunt(table.getEngine().getTableId(), player(table, 1), new TauntRequest(sound));

        verify(publisher, times(2)).allPlayers(eq(table), any());
    }

    @Test
    void foldedPlayerMayTauntButOneWhoLeftMayNot() {
        ManagedTable table = table();
        PokerTable engine = table.getEngine();
        UUID folded = engine.getSeats().get(engine.getActorSeat()).getAccountId();
        engine.act(folded, ActionType.FOLD, engine.getSequence());
        UUID leaver = engine.getSeats().stream().map(seat -> seat.getAccountId()).filter(id -> !id.equals(folded)).findFirst().orElseThrow();
        engine.leave(leaver);
        when(shop.soundUrl(folded, sound)).thenReturn(SOUND_URL);

        taunts.taunt(engine.getTableId(), folded, new TauntRequest(sound));

        GameApiException refused = assertThrows(GameApiException.class,
                () -> taunts.taunt(engine.getTableId(), leaver, new TauntRequest(sound)));
        assertEquals(GameError.GAME_TAUNT_UNAVAILABLE, refused.getCode());
        verify(shop, never()).soundUrl(eq(leaver), any());
    }

    @Test
    void outsiderIsForbidden() {
        ManagedTable table = table();

        GameApiException refused = assertThrows(GameApiException.class,
                () -> taunts.taunt(table.getEngine().getTableId(), UUID.randomUUID(), new TauntRequest(sound)));

        assertEquals(GameError.GAME_FORBIDDEN, refused.getCode());
        verifyNoInteractions(shop, publisher);
    }

    @Test
    void soundTheShopRefusesIsNotSentAndStillCountsTowardTheCooldown() {
        ManagedTable table = table();
        UUID player = player(table, 0);
        when(shop.soundUrl(player, sound)).thenThrow(GameExceptions.tauntSoundNotOwned());

        GameApiException refused = assertThrows(GameApiException.class,
                () -> taunts.taunt(table.getEngine().getTableId(), player, new TauntRequest(sound)));
        assertEquals(GameError.GAME_TAUNT_UNAVAILABLE, refused.getCode());
        verifyNoInteractions(publisher);

        GameApiException retried = assertThrows(GameApiException.class,
                () -> taunts.taunt(table.getEngine().getTableId(), player, new TauntRequest(sound)));
        assertEquals(GameError.GAME_TAUNT_COOLDOWN, retried.getCode());
        verify(shop, times(1)).soundUrl(player, sound);
    }

    private ManagedTable table() {
        PokerTable engine = new PokerTable(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()),
                RULES, new Random(7), new HandEvaluatorImpl(), new PotDistributorImpl());
        ManagedTable table = new ManagedTable(engine, new MatchEntity());
        when(registry.require(engine.getTableId())).thenReturn(table);
        return table;
    }

    private static UUID player(ManagedTable table, int seat) {
        return table.getEngine().getSeats().get(seat).getAccountId();
    }
}
