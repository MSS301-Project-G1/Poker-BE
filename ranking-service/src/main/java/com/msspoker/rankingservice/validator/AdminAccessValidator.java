package com.msspoker.rankingservice.validator;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.exception.RankingExceptions;

import org.springframework.stereotype.Component;

/** SPEC §5.1: /api/admin/** requires X-User-Role = ADMIN, set by the gateway. */
@Component
public class AdminAccessValidator {
    public void validate(String role) {
        if (!RankingConstants.ADMIN_ROLE.equals(role)) throw RankingExceptions.adminOnly();
    }
}
