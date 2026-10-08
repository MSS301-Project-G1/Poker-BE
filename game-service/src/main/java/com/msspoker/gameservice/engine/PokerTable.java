package com.msspoker.gameservice.engine;

import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.poker.Card;
import com.msspoker.gameservice.poker.Deck;
import com.msspoker.gameservice.poker.HandEvaluator;
import com.msspoker.gameservice.poker.HandRank;
import com.msspoker.gameservice.poker.PotDistributor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Mutable hand state. The application service owns the per-table lock. */
@Getter
public final class PokerTable {
    private final UUID tableId;
    private final UUID matchId;
    private final TableRules rules;
    private final List<Seat> seats;
    private final List<Card> board = new ArrayList<>(TableRules.BOARD_CARDS);
    private final Set<Integer> pending = new LinkedHashSet<>();
    private final Random shuffleSeeds;
    private Deck deck;
    private Street street;
    private int dealerSeat = TableRules.NO_SEAT;
    private int smallBlindSeat;
    private int bigBlindSeat;
    private int actorSeat = TableRules.NO_SEAT;
    private int handNumber;
    private long sequence;
    private long currentBet;
    private long lastFullRaise;
    private PotDistributor.Distribution distribution;
    private Map<Integer, HandRank> showdownHands = Map.of();

    public PokerTable(UUID tableId, UUID matchId, List<UUID> playerIds, TableRules rules, long seed) {
        if (playerIds == null || playerIds.size() < rules.minPlayers()
                || playerIds.size() > rules.maxPlayers() || playerIds.stream().anyMatch(java.util.Objects::isNull)
                || new java.util.HashSet<>(playerIds).size() != playerIds.size()) {
            throw GameExceptions.invalidPlayers();
        }
        this.tableId = tableId;
        this.matchId = matchId;
        this.rules = rules;
        this.shuffleSeeds = new Random(seed);
        List<Seat> players = new ArrayList<>();
        for (UUID id : playerIds) {
            Seat seat = new Seat(id, players.size());
            seat.chips = rules.startingChips();
            players.add(seat);
        }
        seats = List.copyOf(players);
        startHand();
    }

    public void startHand() {
        if (street != null && street != Street.HAND_FINISHED) {
            throw GameExceptions.handStillRunning();
        }
        handNumber++;
        sequence++;
        board.clear();
        pending.clear();
        distribution = null;
        showdownHands = Map.of();
        deck = new Deck(shuffleSeeds.nextLong());
        for (Seat seat : seats) {
            seat.streetBet = 0;
            seat.contribution = 0;
            seat.handStartingChips = seat.chips;
            seat.lastActedBet = TableRules.NOT_ACTED;
            seat.folded = seat.eliminated || seat.leftEarly;
            seat.lastAction = null;
            seat.holeCards.clear();
        }
        if (seats.stream().noneMatch(s -> !s.eliminated && !s.leftEarly)) {
            // With everyone AFK, keep the largest stack eligible to collect forced blinds.
            seats.stream().filter(s -> !s.eliminated).max(Comparator.comparingLong(Seat::getChips))
                    .orElseThrow(GameExceptions::invalidPlayers).folded = false;
        }
        dealerSeat = nextLive(dealerSeat);
        long live = seats.stream().filter(s -> !s.eliminated).count();
        smallBlindSeat = live == TableRules.HOLE_CARDS ? dealerSeat : nextLive(dealerSeat);
        bigBlindSeat = nextLive(smallBlindSeat);
        for (int round = 0; round < TableRules.HOLE_CARDS; round++) {
            int seatIndex = dealerSeat;
            for (int i = 0; i < live; i++) {
                seatIndex = nextLive(seatIndex);
                seats.get(seatIndex).holeCards.add(deck.draw());
            }
        }
        commit(seats.get(smallBlindSeat), Math.min(rules.smallBlind(), seats.get(smallBlindSeat).chips));
        commit(seats.get(bigBlindSeat), Math.min(rules.bigBlind(), seats.get(bigBlindSeat).chips));
        // A short BB does not lower the amount other players must call.
        currentBet = rules.bigBlind();
        lastFullRaise = rules.bigBlind();
        street = Street.PREFLOP;
        for (Seat seat : seats) {
            if (seat.canAct()) pending.add(seat.getSeatIndex());
        }
        advance(bigBlindSeat);
        assertConserved();
    }

    public void act(UUID accountId, ActionType type, long expectedSequence) {
        if (type == null || type == ActionType.RAISE) throw GameExceptions.invalidAction();
        apply(accountId, type, expectedSequence, seat -> {
            long owed = Math.max(0, currentBet - seat.streetBet);
            switch (type) {
                case FOLD -> seat.folded = true;
                case CHECK -> {
                    if (owed != 0) throw GameExceptions.cannotCheck();
                }
                case CALL -> {
                    if (owed == 0) throw GameExceptions.invalidAction();
                    commit(seat, Math.min(owed, seat.chips));
                }
                case ALL_IN -> {
                    long target = seat.streetBet + seat.chips;
                    if (target > currentBet) placeRaise(seat, target);
                    else commit(seat, seat.chips);
                }
                default -> throw GameExceptions.invalidAction();
            }
        });
    }

    public void raise(UUID accountId, long amount, long expectedSequence) {
        apply(accountId, ActionType.RAISE, expectedSequence, seat -> placeRaise(seat, amount));
    }

    private void apply(UUID accountId, ActionType type, long expectedSequence, Consumer<Seat> action) {
        if (street == Street.HAND_FINISHED || street == Street.FINISHED || expectedSequence != sequence) {
            throw GameExceptions.staleAction();
        }
        Seat seat = seats.get(actorSeat);
        if (!seat.getAccountId().equals(accountId)) throw GameExceptions.notYourTurn();
        action.accept(seat);
        seat.lastAction = type.name();
        seat.lastActedBet = currentBet;
        seat.consecutiveTimeouts = 0;
        pending.remove(seat.getSeatIndex());
        sequence++;
        advance(seat.getSeatIndex());
        assertConserved();
    }

    public boolean timeout(long expectedSequence, int afkLimit) {
        if (expectedSequence != sequence || actorSeat == TableRules.NO_SEAT) return false;
        Seat seat = seats.get(actorSeat);
        int timeouts = seat.consecutiveTimeouts + 1;
        act(seat.getAccountId(), seat.streetBet >= currentBet ? ActionType.CHECK : ActionType.FOLD, sequence);
        seat.consecutiveTimeouts = timeouts;
        if (timeouts >= afkLimit) seat.leftEarly = true;
        return true;
    }

    public boolean canRaise(Seat seat) {
        return seat.canAct() && seat.streetBet + seat.chips > currentBet
                && (seat.lastActedBet == TableRules.NOT_ACTED
                    || currentBet - seat.lastActedBet >= lastFullRaise)
                && seats.stream().anyMatch(other -> other != seat && other.canAct());
    }

    public long pot() {
        return seats.stream().mapToLong(Seat::getContribution).sum();
    }

    private void placeRaise(Seat seat, long target) {
        long max = seat.streetBet + seat.chips;
        long increment = target - currentBet;
        if (!canRaise(seat) || target <= currentBet || target > max
                || (increment < lastFullRaise && target != max)) {
            throw GameExceptions.invalidRaise();
        }
        commit(seat, target - seat.streetBet);
        currentBet = target;
        if (increment >= lastFullRaise) {
            lastFullRaise = increment;
            pending.clear();
            seats.stream().filter(Seat::canAct).forEach(s -> pending.add(s.getSeatIndex()));
        } else {
            seats.stream().filter(Seat::canAct).filter(s -> s.streetBet < currentBet)
                    .forEach(s -> pending.add(s.getSeatIndex()));
        }
    }

    private void commit(Seat seat, long chips) {
        seat.chips -= chips;
        seat.streetBet += chips;
        seat.contribution += chips;
    }

    private void advance(int afterSeat) {
        pending.removeIf(index -> !seats.get(index).canAct());
        if (seats.stream().filter(s -> !s.folded && !s.eliminated).count() <= 1) {
            settle();
            return;
        }
        List<Seat> actionable = seats.stream().filter(Seat::canAct).toList();
        if (actionable.size() <= 1) {
            pending.removeIf(index -> seats.get(index).streetBet >= currentBet);
        }
        if (!pending.isEmpty()) {
            actorSeat = nextPending(afterSeat);
            return;
        }
        if (street == Street.RIVER) {
            settle();
            return;
        }
        deck.draw(); // burn before each street
        if (street == Street.PREFLOP) {
            street = Street.FLOP;
            for (int i = 0; i < TableRules.FLOP_CARDS; i++) board.add(deck.draw());
        } else {
            street = street == Street.FLOP ? Street.TURN : Street.RIVER;
            board.add(deck.draw());
        }
        currentBet = 0;
        lastFullRaise = rules.bigBlind();
        for (Seat seat : seats) {
            seat.streetBet = 0;
            seat.lastActedBet = TableRules.NOT_ACTED;
            if (seat.canAct()) pending.add(seat.getSeatIndex());
        }
        advance(dealerSeat);
    }

    private void settle() {
        Map<Integer, HandRank> hands = new HashMap<>();
        if (board.size() == TableRules.BOARD_CARDS) {
            for (Seat seat : seats) {
                if (!seat.folded && !seat.eliminated) {
                    List<Card> seven = new ArrayList<>(board);
                    seven.addAll(seat.holeCards);
                    hands.put(seat.getSeatIndex(), HandEvaluator.bestOfSeven(seven));
                }
            }
        }
        showdownHands = Map.copyOf(hands);
        distribution = PotDistributor.distribute(seats.stream().filter(s -> !s.eliminated)
                .map(s -> new PotDistributor.Contribution(s.getSeatIndex(), s.contribution, s.folded)).toList(),
                hands, dealerSeat, seats.size());
        for (Seat seat : seats) {
            seat.chips += distribution.winnings().getOrDefault(seat.getSeatIndex(), 0L)
                    + distribution.refunds().getOrDefault(seat.getSeatIndex(), 0L);
            seat.contribution = 0;
        }
        List<Seat> busted = seats.stream().filter(s -> !s.eliminated && s.chips == 0)
                .sorted(Comparator.comparingLong(Seat::getHandStartingChips)).toList();
        int remaining = (int) seats.stream().filter(s -> !s.eliminated).count();
        long previousStack = TableRules.NOT_ACTED;
        int sharedPlace = remaining;
        for (Seat seat : busted) {
            if (seat.handStartingChips != previousStack) sharedPlace = remaining;
            seat.place = sharedPlace;
            previousStack = seat.handStartingChips;
            seat.eliminated = true;
            remaining--;
        }
        street = remaining == 1 ? Street.FINISHED : Street.HAND_FINISHED;
        if (remaining == 1) seats.stream().filter(s -> !s.eliminated).forEach(s -> s.place = 1);
        pending.clear();
        actorSeat = TableRules.NO_SEAT;
    }

    private int nextLive(int afterSeat) {
        for (int step = 1; step <= seats.size(); step++) {
            int index = Math.floorMod(afterSeat + step, seats.size());
            if (!seats.get(index).eliminated) return index;
        }
        throw GameExceptions.invalidPlayers();
    }

    private int nextPending(int afterSeat) {
        for (int step = 1; step <= seats.size(); step++) {
            int index = Math.floorMod(afterSeat + step, seats.size());
            if (pending.contains(index)) return index;
        }
        throw GameExceptions.invalidAction();
    }

    public void assertConserved() {
        long total = seats.stream().mapToLong(s -> s.chips + s.contribution).sum();
        if (total != rules.startingChips() * seats.size()
                || seats.stream().anyMatch(s -> s.chips < 0 || s.contribution < 0)) {
            throw GameExceptions.chipConservationFailed();
        }
    }
}
