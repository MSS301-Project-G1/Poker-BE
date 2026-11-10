package com.msspoker.walletservice.dto.event;

import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Builder
public record VipCoinTopupSuccessEvent(
        UUID orderId,
        UUID accountId,
        UUID bundleId,
        long vipCoinAmount,
        long payosOrderCode,
        String source,
        Instant timestamp
) implements Serializable {}
