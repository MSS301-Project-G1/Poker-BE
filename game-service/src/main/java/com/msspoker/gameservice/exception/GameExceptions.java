package com.msspoker.gameservice.exception;

import java.util.NoSuchElementException;

public final class GameExceptions {
    private GameExceptions() {
    }

    public static IllegalArgumentException invalidCard() {
        return new IllegalArgumentException("A card must have both a rank and a suit.");
    }

    public static NoSuchElementException emptyDeck() {
        return new NoSuchElementException("The deck is empty.");
    }

    public static IllegalArgumentException invalidShowdownHand() {
        return new IllegalArgumentException("Exactly seven distinct cards are required to evaluate a hand.");
    }

    public static IllegalArgumentException invalidHandRank() {
        return new IllegalArgumentException("Invalid hand rank.");
    }

    public static IllegalArgumentException invalidTable() {
        return new IllegalArgumentException("Invalid table size or dealer seat.");
    }

    public static IllegalArgumentException invalidContribution() {
        return new IllegalArgumentException("Invalid seat or chip contribution.");
    }

    public static IllegalArgumentException invalidPot() {
        return new IllegalArgumentException("The pot must contain chips and at least one active player.");
    }

    public static IllegalArgumentException invalidPotInput() {
        return new IllegalArgumentException("Invalid pot distribution input.");
    }

    public static IllegalArgumentException noEligiblePlayer() {
        return new IllegalArgumentException("No eligible player can receive this pot.");
    }

    public static IllegalArgumentException missingShowdownHand(int seat) {
        return new IllegalArgumentException("Missing showdown hand for seat " + seat + ".");
    }

    public static IllegalStateException chipConservationFailed() {
        return new IllegalStateException("Distributed chips do not match total contributions.");
    }
}
