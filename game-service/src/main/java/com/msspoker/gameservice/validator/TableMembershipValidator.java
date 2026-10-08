package com.msspoker.gameservice.validator;

import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.model.game.ManagedTable;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TableMembershipValidator {
    public void validate(ManagedTable table, UUID accountId) {
        if (table.getEngine().getSeats().stream().noneMatch(s -> s.getAccountId().equals(accountId))) {
            throw GameExceptions.forbidden();
        }
    }
}
