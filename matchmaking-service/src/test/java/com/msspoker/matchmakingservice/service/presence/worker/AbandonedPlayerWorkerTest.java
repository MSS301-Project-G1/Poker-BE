package com.msspoker.matchmakingservice.service.presence.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.presence.AbandonedPlayerService;
import com.msspoker.matchmakingservice.service.store.PresenceStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AbandonedPlayerWorkerTest {
    private static final Instant STARTED = Instant.parse("2026-11-11T10:00:00Z");

    private final PresenceStore presence = mock(PresenceStore.class);
    private final AbandonedPlayerService players = mock(AbandonedPlayerService.class);
    private final MatchmakingProperties properties = new MatchmakingProperties();
    private final Clock clock = mock(Clock.class);
    private final AbandonedPlayerWorker worker = startWorker();

    private AbandonedPlayerWorker startWorker() {
        properties.getPresence().setTimeout(Duration.ofSeconds(10));
        when(clock.instant()).thenReturn(STARTED);
        return new AbandonedPlayerWorker(presence, players, properties, clock);
    }

    @Test
    void releasesPlayersSilentLongerThanTheTimeoutAndForgetsOnlyThoseFullyReleased() {
        Instant now = STARTED.plusSeconds(60);
        when(clock.instant()).thenReturn(now);
        UUID gone = UUID.randomUUID();
        UUID creatingTable = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        when(presence.seenBefore(now.minusSeconds(10), MatchmakingConstants.PRESENCE_SWEEP_BATCH))
                .thenReturn(List.of(broken, gone, creatingTable));
        when(players.release(broken)).thenThrow(new IllegalStateException("redis down"));
        when(players.release(gone)).thenReturn(true);
        when(players.release(creatingTable)).thenReturn(false);

        worker.sweep();

        verify(presence).forget(gone);
        verify(presence, never()).forget(creatingTable);
        verify(presence, never()).forget(broken);
    }

    @Test
    void waitsOneTimeoutAfterStartupSoPlayersCanPollAgainBeforeAnyoneIsReleased() {
        // Sau khi deploy, dấu thời gian trong Redis đã cũ vài phút dù người chơi vẫn mở tab.
        when(clock.instant()).thenReturn(STARTED.plusSeconds(9));

        worker.sweep();

        verify(presence, never()).seenBefore(any(), anyInt());
        verifyNoInteractions(players);
    }
}
