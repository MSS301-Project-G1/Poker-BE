package com.msspoker.gameservice.model.game;

import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.Street;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PokerTableTest {
    private static final TableRules RULES = new TableRules(10, 20, 1000, 20, 2, 8);

    @Test
    void headsUpDealerIsSmallBlindAndActsFirstPreflopThenLastPostflop() {
        PokerTable table = table(2, 1);
        assertEquals(table.getDealerSeat(), table.getSmallBlindSeat());
        assertEquals(table.getDealerSeat(), table.getActorSeat());
        act(table, ActionType.CALL);
        act(table, ActionType.CHECK);
        assertEquals(Street.FLOP, table.getStreet());
        assertEquals(table.getBigBlindSeat(), table.getActorSeat());
        assertEquals(3, table.getBoard().size());
    }

    @Test
    void invalidAndStaleActionsDoNotMutateTheHand() {
        PokerTable table = table(2, 2);
        Seat actor = table.getSeats().get(table.getActorSeat());
        long sequence = table.getSequence();
        assertThrows(IllegalArgumentException.class,
                () -> table.act(actor.getAccountId(), ActionType.CHECK, sequence));
        assertThrows(IllegalArgumentException.class,
                () -> table.raise(actor.getAccountId(), 25, sequence));
        assertThrows(IllegalArgumentException.class,
                () -> table.act(UUID.randomUUID(), ActionType.CALL, sequence));
        assertThrows(IllegalStateException.class,
                () -> table.act(actor.getAccountId(), ActionType.CALL, sequence - 1));
        assertEquals(sequence, table.getSequence());
        assertEquals(30, table.pot());
        assertEquals(990, actor.getChips());
    }

    @Test
    void shortAllInDoesNotReopenRaiseForPlayersWhoAlreadyActed() {
        PokerTable table = table(3, 3);
        // Move chips between stacks without changing the table total.
        table.getSeats().get(2).chips = 5;
        table.getSeats().get(0).chips += 975;
        act(table, ActionType.CALL); // dealer
        act(table, ActionType.CALL); // small blind
        act(table, ActionType.ALL_IN); // BB raises only 5 to 25
        assertEquals(25, table.getCurrentBet());
        Seat dealer = table.getSeats().get(0);
        assertFalse(table.canRaise(dealer));
        assertThrows(IllegalArgumentException.class,
                () -> table.raise(dealer.getAccountId(), 45, table.getSequence()));
        act(table, ActionType.CALL);
        act(table, ActionType.CALL);
        assertEquals(Street.FLOP, table.getStreet());
        table.assertConserved();
    }

    @Test
    void shortBigBlindKeepsNominalCallAndReturnsUncalledChips() {
        PokerTable table = table(2, 4);
        act(table, ActionType.FOLD);
        // Dealer rotates; seat 0 is the new BB and has fewer than 20.
        table.getSeats().get(0).chips = 7;
        table.getSeats().get(1).chips = 1993;
        table.startHand();
        assertEquals(0, table.getBigBlindSeat());
        assertEquals(20, table.getCurrentBet());
        act(table, ActionType.CALL);
        assertTrue(table.getStreet() == Street.HAND_FINISHED || table.getStreet() == Street.FINISHED);
        assertEquals(5, table.getBoard().size());
        assertEquals(13L, table.getDistribution().refunds().get(1));
        table.assertConserved();
    }

    @Test
    void allInRunsOutTheBoardAndRanksSimultaneousEliminationsByStartingStack() {
        boolean observed = false;
        for (int seed = 0; seed < 200 && !observed; seed++) {
            PokerTable table = table(3, seed);
            table.getSeats().get(0).chips = 500;
            table.getSeats().get(1).chips = 990;
            table.getSeats().get(2).chips = 1480;
            // Include posted blinds in start-of-hand stack comparison.
            table.getSeats().get(0).handStartingChips = 500;
            table.getSeats().get(1).handStartingChips = 1000;
            table.getSeats().get(2).handStartingChips = 1500;
            act(table, ActionType.ALL_IN);
            act(table, ActionType.ALL_IN);
            act(table, ActionType.CALL);
            assertEquals(5, table.getBoard().size());
            table.assertConserved();
            if (table.getSeats().get(0).isEliminated() && table.getSeats().get(1).isEliminated()) {
                assertEquals(3, table.getSeats().get(0).getPlace());
                assertEquals(2, table.getSeats().get(1).getPlace());
                assertEquals(1, table.getSeats().get(2).getPlace());
                observed = true;
            }
        }
        assertTrue(observed);
    }

    @Test
    void oldTimeoutCannotActOnANewTurn() {
        PokerTable table = table(2, 5);
        long oldSequence = table.getSequence();
        act(table, ActionType.CALL);
        assertFalse(table.timeout(oldSequence, 3));
        assertEquals(oldSequence + 1, table.getSequence());
        assertTrue(table.timeout(table.getSequence(), 3));
        assertEquals(Street.FLOP, table.getStreet());
    }

    @Test
    void equalStartingStacksUseSeatOrderForUniqueEliminationPlaces() {
        boolean observed = false;
        for (int seed = 0; seed < 200 && !observed; seed++) {
            PokerTable table = table(3, seed);
            act(table, ActionType.ALL_IN);
            act(table, ActionType.CALL);
            act(table, ActionType.CALL);
            if (table.getStreet() == Street.FINISHED) {
                assertEquals(3, table.getSeats().stream().map(Seat::getPlace).distinct().count());
                List<Seat> eliminated = table.getSeats().stream().filter(Seat::isEliminated).toList();
                assertTrue(eliminated.getFirst().getPlace() < eliminated.getLast().getPlace());
                observed = true;
            }
        }
        assertTrue(observed);
    }

    @Test
    void afkPlayersKeepPostingBlindsWithoutLosingChipsOutsideThePot() {
        PokerTable table = table(3, 6);
        table.timeout(table.getSequence(), 1);
        assertTrue(table.getSeats().get(0).isLeftEarly());
        while (table.getStreet() != Street.HAND_FINISHED && table.getStreet() != Street.FINISHED) {
            Seat actor = table.getSeats().get(table.getActorSeat());
            act(table, actor.getStreetBet() >= table.getCurrentBet() ? ActionType.CHECK : ActionType.CALL);
        }
        if (table.getStreet() == Street.HAND_FINISHED) {
            table.getSeats().forEach(s -> s.leftEarly = true);
            table.startHand();
            assertTrue(table.getStreet() == Street.HAND_FINISHED || table.getStreet() == Street.FINISHED);
        }
        table.assertConserved();
    }

    @Test
    void seededBotsPlayThousandsOfHandsWithoutCreatingOrLosingChips() {
        Random decisions = new Random(20261008);
        int hands = 0;
        for (int seed = 0; seed < 1200; seed++) {
            PokerTable table = table(2 + seed % 7, seed);
            int actions = 0;
            while (table.getStreet() != Street.FINISHED && table.getHandNumber() < 40) {
                assertTrue(++actions < 10000, "Stuck at seed " + seed);
                if (table.getStreet() == Street.HAND_FINISHED) {
                    hands++;
                    table.startHand();
                    continue;
                }
                Seat actor = table.getSeats().get(table.getActorSeat());
                int decision = decisions.nextInt(10);
                if (decision == 0) act(table, ActionType.FOLD);
                else if (decision < 3 && table.canRaise(actor)) act(table, ActionType.ALL_IN);
                else if (decision < 5 && table.canRaise(actor)
                        && table.getCurrentBet() + table.getLastFullRaise() < actor.getStreetBet() + actor.getChips()) {
                    table.raise(actor.getAccountId(), table.getCurrentBet() + table.getLastFullRaise(), table.getSequence());
                } else act(table, actor.getStreetBet() >= table.getCurrentBet() ? ActionType.CHECK : ActionType.CALL);
                table.assertConserved();
            }
            hands++;
        }
        assertTrue(hands >= 3000, "Played " + hands + " hands");
    }

    private static PokerTable table(int players, long seed) {
        List<UUID> ids = IntStream.range(0, players).mapToObj(i -> UUID.randomUUID()).toList();
        return new PokerTable(UUID.randomUUID(), UUID.randomUUID(), ids, RULES, seed);
    }

    private static void act(PokerTable table, ActionType action) {
        table.act(table.getSeats().get(table.getActorSeat()).getAccountId(), action, table.getSequence());
    }
}
