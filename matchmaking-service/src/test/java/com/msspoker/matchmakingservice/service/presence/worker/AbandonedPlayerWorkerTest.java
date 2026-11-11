package com.msspoker.matchmakingservice.service.presence.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.presence.AbandonedPlayerService;
import com.msspoker.matchmakingservice.service.store.PresenceStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class AbandonedPlayerWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final PresenceStore presence = mock(PresenceStore.class);
    private final AbandonedPlayerService players = mock(AbandonedPlayerService.class);
    private final MatchmakingProperties properties = new MatchmakingProperties();
    private final AbandonedPlayerWorker worker =
            new AbandonedPlayerWorker(presence, players, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void releasesPlayersSilentLongerThanTheTimeoutAndForgetsOnlyThoseFullyReleased() {
        properties.getPresence().setTimeout(Duration.ofSeconds(60));
        UUID gone = UUID.randomUUID();
        UUID creatingTable = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        when(presence.seenBefore(NOW.minusSeconds(60), MatchmakingConstants.PRESENCE_SWEEP_BATCH))
                .thenReturn(List.of(broken, gone, creatingTable));
        when(players.release(broken)).thenThrow(new IllegalStateException("redis down"));
        when(players.release(gone)).thenReturn(true);
        when(players.release(creatingTable)).thenReturn(false);

        worker.sweep();

        verify(presence).forget(gone);
        verify(presence, never()).forget(creatingTable);
        verify(presence, never()).forget(broken);
    }
}
