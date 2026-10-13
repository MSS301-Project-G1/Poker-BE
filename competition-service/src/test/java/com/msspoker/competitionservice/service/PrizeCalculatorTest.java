package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.service.impl.PrizeCalculatorImpl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrizeCalculatorTest {
    final CompetitionProperties properties = new CompetitionProperties();
    final PrizeCalculator calculator = new PrizeCalculatorImpl(properties);

    @Test
    void splitsThePoolFiftyThirtyTwentyAndGivesTheRemainderToTheChampion() {
        assertEquals(List.of(1600L, 960L, 640L), calculator.split(3200, 8));
        assertEquals(List.of(502L, 300L, 200L), calculator.split(1002, 8));
        assertEquals(List.of(1L, 0L, 0L), calculator.split(1, 3));
        assertEquals(List.of(0L, 0L, 0L), calculator.split(0, 8));
    }

    @Test
    void smallFinalsShareThePoolBetweenThePlacesThatExist() {
        assertEquals(List.of(625L, 375L), calculator.split(1000, 2));
        assertEquals(List.of(1000L), calculator.split(1000, 1));
        assertEquals(List.of(), calculator.split(1000, 0));
        properties.getTournament().setPrizeShares(List.of(60, 40));
        assertEquals(List.of(600L, 400L), calculator.split(1000, 8));
    }
}
