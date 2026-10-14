package com.msspoker.competitionservice.validator;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.exception.CompetitionExceptions;

import org.springframework.stereotype.Component;

/** SPEC §5.1: /api/admin/** requires X-User-Role = ADMIN, set by the gateway. */
@Component
public class AdminAccessValidator {
    public void validate(String role) {
        if (!CompetitionConstants.ADMIN_ROLE.equals(role)) throw CompetitionExceptions.adminOnly();
    }
}
