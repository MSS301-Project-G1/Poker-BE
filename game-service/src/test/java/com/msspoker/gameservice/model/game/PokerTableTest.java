package com.msspoker.gameservice.model.game;

import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.Street;
import com.msspoker.gameservice.service.poker.HandEvaluator;
import com.msspoker.gameservice.service.poker.PotDistributor;
import com.msspoker.gameservice.service.impl.poker.HandEvaluatorImpl;
import com.msspoker.gameservice.service.impl.poker.PotDistributorImpl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PokerTableTest {
    private static final HandEvaluator HAND_EVALUATOR = new HandEvaluatorImpl();
    private static final PotDistributor POT_DISTRIBUTOR = new PotDistributorImpl();
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

    @Test
    void leavingOnYourTurnHeadsUpEndsTheMatchWithTheOpponentFirst() {
        PokerTable table = table(2, 11);
        Seat leaver = table.getSeats().get(table.getActorSeat());
        Seat stayer = table.getSeats().stream().filter(s -> s != leaver).findFirst().orElseThrow();
        table.leave(leaver.getAccountId());
        assertEquals(Street.FINISHED, table.getStreet());
        assertTrue(leaver.isLeftEarly());
        assertEquals(1, stayer.getPlace());
        assertEquals(2, leaver.getPlace());
        table.assertConserved();
    }

    @Test
    void leavingOutOfTurnFoldsWithoutTakingTheActorsTurnAndTheRestPlayOn() {
        PokerTable table = table(3, 12);
        int actor = table.getActorSeat();
        long sequence = table.getSequence();
        Seat leaver = table.getSeats().stream().filter(s -> s.getSeatIndex() != actor).findFirst().orElseThrow();
        table.leave(leaver.getAccountId());
        assertTrue(leaver.isFolded());
        assertTrue(leaver.isLeftEarly());
        assertEquals(actor, table.getActorSeat());
        assertEquals(sequence, table.getSequence());
        assertEquals(Street.PREFLOP, table.getStreet());
        table.assertConserved();
    }

    @Test
    void lastPlayerWhoStaysWinsAndLeaversArePlacedByChips() {
        PokerTable table = table(3, 13);
        while (table.getStreet() != Street.HAND_FINISHED && table.getStreet() != Street.FINISHED) {
            Seat actor = table.getSeats().get(table.getActorSeat());
            act(table, actor.getStreetBet() >= table.getCurrentBet() ? ActionType.CHECK : ActionType.CALL);
        }
        if (table.getStreet() == Street.FINISHED) return;
        List<Seat> seats = table.getSeats();
        table.leave(seats.get(0).getAccountId());
        assertEquals(Street.HAND_FINISHED, table.getStreet());
        table.leave(seats.get(1).getAccountId());
        assertEquals(Street.FINISHED, table.getStreet());
        assertEquals(1, seats.get(2).getPlace());
        Seat richer = seats.get(0).getChips() >= seats.get(1).getChips() ? seats.get(0) : seats.get(1);
        assertEquals(2, richer.getPlace());
        table.assertConserved();
    }

    @Test
    void leavingTwiceOrAfterTheMatchChangesNothing() {
        PokerTable table = table(3, 14);
        Seat leaver = table.getSeats().stream().filter(s -> s.getSeatIndex() != table.getActorSeat()).findFirst().orElseThrow();
        table.leave(leaver.getAccountId());
        long sequence = table.getSequence();
        table.leave(leaver.getAccountId());
        assertEquals(sequence, table.getSequence());
        PokerTable finished = table(2, 15);
        finished.leave(finished.getSeats().getFirst().getAccountId());
        int place = finished.getSeats().get(1).getPlace();
        finished.leave(finished.getSeats().get(1).getAccountId());
        assertEquals(place, finished.getSeats().get(1).getPlace());
        assertFalse(finished.getSeats().get(1).isLeftEarly());
    }

    @Test
    void headsUpAfkPlayerEndsTheMatchInsteadOfBleedingBlinds() {
        PokerTable table = table(2, 16);
        Seat afk = table.getSeats().get(table.getActorSeat());
        table.timeout(table.getSequence(), 1);
        while (table.getStreet() != Street.HAND_FINISHED && table.getStreet() != Street.FINISHED) {
            Seat actor = table.getSeats().get(table.getActorSeat());
            if (actor == afk) table.timeout(table.getSequence(), 1);
            else act(table, actor.getStreetBet() >= table.getCurrentBet() ? ActionType.CHECK : ActionType.CALL);
        }
        if (table.getStreet() == Street.HAND_FINISHED) table.startHand();
        assertEquals(Street.FINISHED, table.getStreet());
        assertNotEquals(1, afk.getPlace());
        table.assertConserved();
    }

    private static PokerTable table(int players, long seed) {
        List<UUID> ids = IntStream.range(0, players).mapToObj(i -> UUID.randomUUID()).toList();
        return new PokerTable(UUID.randomUUID(), UUID.randomUUID(), ids, RULES,
                new Random(seed), HAND_EVALUATOR, POT_DISTRIBUTOR);
    }

    private static void act(PokerTable table, ActionType action) {
        table.act(table.getSeats().get(table.getActorSeat()).getAccountId(), action, table.getSequence());
    }
}
