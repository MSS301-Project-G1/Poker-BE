package com.msspoker.matchmakingservice.service.presence.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.presence.AbandonedPlayerService;
import com.msspoker.matchmakingservice.service.store.PresenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Người chơi đóng tab hoặc trình duyệt thì không còn ai bấm rời hàng chờ hay rời party cho họ.
 * Worker tìm tài khoản đã im lặng quá matchmaking.presence.timeout và dọn giúp; tải lại trang
 * chỉ im lặng vài giây nên không bị ảnh hưởng.
 */
@Component
@ConditionalOnProperty(name = "matchmaking.finder.enabled", havingValue = "true", matchIfMissing = true)
public class AbandonedPlayerWorker {
    private static final Logger log = LoggerFactory.getLogger(AbandonedPlayerWorker.class);

    private final PresenceStore presence;
    private final AbandonedPlayerService players;
    private final MatchmakingProperties properties;
    private final Clock clock;

    public AbandonedPlayerWorker(PresenceStore presence, AbandonedPlayerService players,
                                 MatchmakingProperties properties, Clock clock) {
        this.presence = presence;
        this.players = players;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${matchmaking.presence.sweep-delay-ms:10000}")
    public void sweep() {
        Instant cutoff = clock.instant().minus(properties.getPresence().getTimeout());
        for (UUID accountId : presence.seenBefore(cutoff, MatchmakingConstants.PRESENCE_SWEEP_BATCH)) {
            try {
                if (players.release(accountId)) presence.forget(accountId);
            } catch (RuntimeException exception) {
                log.error("Cannot release abandoned player accountId={}", accountId, exception);
            }
        }
    }
}
