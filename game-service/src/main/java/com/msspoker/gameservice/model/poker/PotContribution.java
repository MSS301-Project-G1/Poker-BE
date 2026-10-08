package com.msspoker.gameservice.model.poker;

import com.msspoker.gameservice.exception.GameExceptions;

public record PotContribution(int seat, long chips, boolean folded) {
    public PotContribution {
        if (seat < 0 || chips < 0) {
            throw GameExceptions.invalidContribution();
        }
    }
}
