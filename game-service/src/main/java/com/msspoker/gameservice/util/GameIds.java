package com.msspoker.gameservice.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class GameIds {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERSION_SEVEN = 0x7000L;
    private static final long VARIANT_IETF = 0x8000000000000000L;
    private static final long RANDOM_LOW_MASK = 0x3fffffffffffffffL;
    private static final int RANDOM_HIGH_BOUND = 1 << 12;
    private static final int TIMESTAMP_SHIFT = 16;

    private GameIds() {
    }

    public static UUID next() {
        long high = (System.currentTimeMillis() << TIMESTAMP_SHIFT)
                | VERSION_SEVEN | RANDOM.nextInt(RANDOM_HIGH_BOUND);
        return new UUID(high, VARIANT_IETF | (RANDOM.nextLong() & RANDOM_LOW_MASK));
    }
}
