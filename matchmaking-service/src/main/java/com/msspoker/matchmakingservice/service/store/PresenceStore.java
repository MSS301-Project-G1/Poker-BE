package com.msspoker.matchmakingservice.service.store;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lần cuối mỗi tài khoản gọi API matchmaking; FE hỏi trạng thái mỗi giây nên đây là nhịp "tab còn
 * mở".
 */
public interface PresenceStore {
    void touch(UUID accountId, Instant seenAt);

    /** Tài khoản không gọi API nào kể từ cutoff, người im lặng lâu nhất đứng trước. */
    List<UUID> seenBefore(Instant cutoff, int limit);

    void forget(UUID accountId);
}
