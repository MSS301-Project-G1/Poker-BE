package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.event.PlacementEvent;
import com.msspoker.competitionservice.dto.request.CreateTournamentRequest;
import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.dto.response.PageResponse;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.dto.response.TournamentSummaryResponse;
import com.msspoker.competitionservice.dto.response.TournamentTableResponse;
import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.entity.TournamentEntryEntity;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.enums.EntryStatus;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.enums.TableStatus;
import com.msspoker.competitionservice.enums.TournamentRound;
import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.mapper.TournamentMapper;
import com.msspoker.competitionservice.repository.ItemGrantRepository;
import com.msspoker.competitionservice.repository.TournamentEntryRepository;
import com.msspoker.competitionservice.repository.TournamentRepository;
import com.msspoker.competitionservice.repository.TournamentSeatRepository;
import com.msspoker.competitionservice.repository.TournamentTableRepository;
import com.msspoker.competitionservice.repository.WalletCreditRepository;
import com.msspoker.competitionservice.service.PrizeCalculator;
import com.msspoker.competitionservice.service.TournamentService;
import com.msspoker.competitionservice.service.job.TournamentTableLauncher;
import com.msspoker.competitionservice.service.client.WalletClient;
import com.msspoker.competitionservice.validator.EliteInviteValidator;
import com.msspoker.competitionservice.validator.TournamentResultValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private static final Sort UPCOMING_FIRST = Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("id"));
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"));
    private static final Set<TournamentStatus> ENDED = EnumSet.of(TournamentStatus.FINISHED, TournamentStatus.CANCELLED);
    private static final Set<TournamentStatus> NOT_ENDED = EnumSet.complementOf(EnumSet.copyOf(ENDED));
    private static final Set<EntryStatus> SEATED = EnumSet.of(EntryStatus.REGISTERED, EntryStatus.CONFIRMED);

    private final TournamentRepository tournaments;
    private final TournamentEntryRepository entries;
    private final TournamentTableRepository tables;
    private final TournamentSeatRepository seats;
    private final WalletCreditRepository credits;
    private final ItemGrantRepository itemGrants;
    private final TournamentMapper mapper;
    private final TournamentResultValidator results;
    private final EliteInviteValidator invites;
    private final PrizeCalculator prizes;
    private final WalletClient wallet;
    private final TournamentTableLauncher launcher;
    private final CompetitionProperties properties;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    @Override
    public TournamentResponse create(UUID adminId, CreateTournamentRequest request) {
        CompetitionProperties.Tournament defaults = properties.getTournament();
        int slots = request.slots() == null ? defaults.getSlots() : request.slots();
        if (slots < 2 || slots > defaults.maxSlots()) throw CompetitionExceptions.invalidTournamentSettings(defaults.maxSlots());
        Instant now = Instant.now();
        if (request.type() == TournamentType.ELITE) invites.validateSchedule(request.startAt(), now);
        return view(tournaments.save(mapper.newTournament(request, slots, adminId, now)));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<TournamentSummaryResponse> list(boolean past, int page, int size) {
        Page<TournamentEntity> result = tournaments.findByStatusInAndIsDeletedFalse(past ? ENDED : NOT_ENDED,
                PageRequest.of(page, size, past ? LATEST_FIRST : UPCOMING_FIRST));
        List<TournamentSummaryResponse> content = result.getContent().stream()
                .map(t -> {
                    long held = heldPlaces(t.getId());
                    return mapper.summary(t, (int) held, prizePool(t, held));
                }).toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public TournamentResponse get(UUID tournamentId) {
        return view(tournaments.findByIdAndIsDeletedFalse(tournamentId).orElseThrow(CompetitionExceptions::tournamentNotFound));
    }

    /**
     * The fee is charged under the tournament lock before the entry is stored. If storing fails, a
     * repeated registration reuses the idempotency key, so the wallet does not charge twice.
     */
    @Transactional
    @Override
    public TournamentResponse register(UUID tournamentId, UUID accountId) {
        TournamentEntity tournament = lock(tournamentId);
        if (entries.findByTournamentIdAndAccountId(tournamentId, accountId).isPresent()) return view(tournament);
        if (tournament.getType() == TournamentType.ELITE) throw CompetitionExceptions.inviteOnly();
        if (tournament.getStatus() != TournamentStatus.SCHEDULED || !Instant.now().isBefore(tournament.getStartAt())) {
            throw CompetitionExceptions.registrationClosed();
        }
        if (entries.countByTournamentId(tournamentId) >= tournament.getSlots()) throw CompetitionExceptions.tournamentFull();
        if (tournament.getEntryFee() > 0) {
            wallet.debit(new WalletTransactionRequest(accountId, tournament.getEntryFee(),
                    CompetitionConstants.TOURNAMENT_ENTRY_FEE, tournamentId.toString(),
                    String.format(CompetitionConstants.TOURNAMENT_FEE_KEY_FORMAT, tournamentId, accountId)));
        }
        entries.save(mapper.newEntry(tournamentId, accountId, EntryStatus.REGISTERED, Instant.now()));
        return view(tournament);
    }

    @Transactional
    @Override
    public TournamentResponse confirm(UUID tournamentId, UUID accountId) {
        TournamentEntity tournament = lock(tournamentId);
        TournamentEntryEntity entry = entries.findByTournamentIdAndAccountId(tournamentId, accountId)
                .filter(found -> tournament.getType() == TournamentType.ELITE)
                .orElseThrow(CompetitionExceptions::notInvited);
        if (entry.getStatus() == EntryStatus.CONFIRMED) return view(tournament);
        if (tournament.getStatus() != TournamentStatus.SCHEDULED || !Instant.now().isBefore(tournament.getStartAt())) {
            throw CompetitionExceptions.registrationClosed();
        }
        if (entry.getStatus() != EntryStatus.INVITED) throw CompetitionExceptions.inviteExpired();
        invites.validateConfirm(tournament, entry);
        entry.setStatus(EntryStatus.CONFIRMED);
        return view(tournament);
    }

    /** Seats are committed first; tables then open through the launcher, which retries on failure. */
    @Override
    public TournamentResponse start(UUID tournamentId) {
        transactions.executeWithoutResult(status -> {
            TournamentEntity tournament = lock(tournamentId);
            if (tournament.getStatus() != TournamentStatus.SCHEDULED) throw CompetitionExceptions.tournamentNotScheduled();
            List<UUID> players = closeEntries(tournament);
            int minPlayers = properties.getTournament().getMinPlayers();
            if (players.size() < minPlayers) throw CompetitionExceptions.notEnoughPlayers(minPlayers);
            seatPlayers(tournament, players);
        });
        launcher.launchPending();
        return get(tournamentId);
    }

    @Override
    public void startDue(UUID tournamentId) {
        Boolean started = transactions.execute(status -> {
            TournamentEntity tournament = lock(tournamentId);
            if (tournament.getStatus() != TournamentStatus.SCHEDULED || tournament.getStartAt().isAfter(Instant.now())) {
                return false;
            }
            List<UUID> players = closeEntries(tournament);
            if (players.size() < properties.getTournament().getMinPlayers()) {
                log.info("Tournament {} cancelled at its start with {} players", tournamentId, players.size());
                cancelScheduled(tournament);
                return false;
            }
            seatPlayers(tournament, players);
            return true;
        });
        if (Boolean.TRUE.equals(started)) launcher.launchPending();
    }

    @Transactional
    @Override
    public TournamentResponse cancel(UUID tournamentId) {
        TournamentEntity tournament = lock(tournamentId);
        if (tournament.getStatus() != TournamentStatus.SCHEDULED) throw CompetitionExceptions.tournamentNotScheduled();
        cancelScheduled(tournament);
        return view(tournament);
    }

    /** Idempotent: a table records its result once; the last qualifier result seats the final. */
    @Transactional
    @Override
    public void matchFinished(MatchFinishedEvent event) {
        if (event == null || event.mode() == null || event.sourceId() == null) throw CompetitionExceptions.invalidEvent();
        if (event.mode() != GameMode.TOURNAMENT) return;
        TournamentTableEntity table = tables.lockById(event.sourceId()).orElse(null);
        if (table == null || table.getStatus() == TableStatus.FINISHED) return;
        TournamentEntity tournament = lock(table.getTournamentId());
        List<TournamentSeatEntity> roster = seats.findByTournamentTableIdOrderBySeatIndex(table.getId());
        results.validate(event, roster);
        Map<UUID, Integer> places = event.placements().stream()
                .collect(Collectors.toMap(PlacementEvent::accountId, PlacementEvent::place));
        roster.forEach(seat -> seat.setPlace(places.get(seat.getAccountId())));
        table.setStatus(TableStatus.FINISHED);
        if (table.getTableId() == null) table.setTableId(event.tableId());
        table.setMatchId(event.matchId());
        Map<UUID, TournamentEntryEntity> players = entries.findByTournamentIdAndAccountIdIn(tournament.getId(),
                places.keySet()).stream().collect(Collectors.toMap(TournamentEntryEntity::getAccountId, Function.identity()));

        if (table.getRound() == TournamentRound.FINAL) {
            roster.forEach(seat -> players.get(seat.getAccountId()).setFinalPlace(seat.getPlace()));
            tournament.setStatus(TournamentStatus.FINISHED);
            awardPrizes(tournament, roster);
            return;
        }
        int advance = properties.getTournament().getAdvancePerTable();
        roster.stream().filter(seat -> seat.getPlace() > advance)
                .forEach(seat -> players.get(seat.getAccountId()).setStatus(EntryStatus.ELIMINATED));
        List<TournamentTableEntity> qualifiers = tables.findByTournamentIdAndRoundOrderByTableIndexAsc(
                tournament.getId(), TournamentRound.QUALIFIER);
        if (qualifiers.stream().allMatch(q -> q.getStatus() == TableStatus.FINISHED)) {
            Map<UUID, List<TournamentSeatEntity>> qualifierSeats = seats.findByTournamentTableIdIn(
                    qualifiers.stream().map(TournamentTableEntity::getId).toList()).stream()
                    .collect(Collectors.groupingBy(TournamentSeatEntity::getTournamentTableId));
            // Final seats: table 1's winner and runner-up, then table 2's, and so on.
            List<UUID> finalists = qualifiers.stream().flatMap(q -> qualifierSeats.get(q.getId()).stream()
                            .filter(seat -> seat.getPlace() <= advance)
                            .sorted(Comparator.comparing(TournamentSeatEntity::getPlace)))
                    .map(TournamentSeatEntity::getAccountId).toList();
            createTable(tournament.getId(), TournamentRound.FINAL, 1, finalists, Instant.now());
            tournament.setStatus(TournamentStatus.FINAL);
        }
    }

    /** Unconfirmed ELITE invitations expire at the start; returns the players who take part. */
    private List<UUID> closeEntries(TournamentEntity tournament) {
        List<TournamentEntryEntity> all = entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournament.getId());
        all.stream().filter(entry -> entry.getStatus() == EntryStatus.INVITED)
                .forEach(entry -> entry.setStatus(EntryStatus.EXPIRED));
        return all.stream().filter(entry -> SEATED.contains(entry.getStatus())).map(TournamentEntryEntity::getAccountId).toList();
    }

    private void cancelScheduled(TournamentEntity tournament) {
        tournament.setStatus(TournamentStatus.CANCELLED);
        if (tournament.getEntryFee() <= 0) return;
        Instant now = Instant.now();
        UUID tournamentId = tournament.getId();
        for (TournamentEntryEntity entry : entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournamentId)) {
            credits.save(mapper.newCredit(entry.getAccountId(), tournament.getEntryFee(),
                    CompetitionConstants.TOURNAMENT_REFUND, tournamentId.toString(),
                    String.format(CompetitionConstants.TOURNAMENT_REFUND_KEY_FORMAT, tournamentId, entry.getAccountId()), now));
        }
    }

    /** Pool coins go to the podium (TOURNAMENT_PRIZE) and the exclusive skin to the champion. */
    private void awardPrizes(TournamentEntity tournament, List<TournamentSeatEntity> finalists) {
        List<UUID> podium = finalists.stream().sorted(Comparator.comparing(TournamentSeatEntity::getPlace))
                .map(TournamentSeatEntity::getAccountId).toList();
        List<Long> amounts = prizes.split(prizePool(tournament, heldPlaces(tournament.getId())), podium.size());
        UUID tournamentId = tournament.getId();
        Instant now = Instant.now();
        for (int i = 0; i < amounts.size(); i++) {
            if (amounts.get(i) <= 0) continue;
            UUID accountId = podium.get(i);
            credits.save(mapper.newCredit(accountId, amounts.get(i), CompetitionConstants.TOURNAMENT_PRIZE,
                    tournamentId.toString(), String.format(CompetitionConstants.TOURNAMENT_PRIZE_KEY_FORMAT, tournamentId, accountId), now));
        }
        if (tournament.getRewardProductId() != null) {
            UUID champion = podium.getFirst();
            itemGrants.save(mapper.newItemGrant(champion, tournament.getRewardProductId(), tournamentId.toString(),
                    String.format(CompetitionConstants.TOURNAMENT_ITEM_KEY_FORMAT, tournamentId, champion), now));
        }
    }

    private long heldPlaces(UUID tournamentId) {
        return entries.countByTournamentIdAndStatusNot(tournamentId, EntryStatus.EXPIRED);
    }

    /** Every entry holding a place paid the fee (ELITE is free); rewardCoins is added on top. */
    private long prizePool(TournamentEntity tournament, long heldPlaces) {
        return Math.addExact(Math.multiplyExact(tournament.getEntryFee(), heldPlaces), tournament.getRewardCoins());
    }

    /** Spreads shuffled players over ceil(n / tableSize) tables; one table means a direct final. */
    private void seatPlayers(TournamentEntity tournament, List<UUID> seated) {
        UUID tournamentId = tournament.getId();
        CompetitionProperties.Tournament defaults = properties.getTournament();
        List<UUID> players = new ArrayList<>(seated);
        Collections.shuffle(players, random);
        int tableCount = Math.ceilDiv(players.size(), defaults.getTableSize());
        TournamentRound round = tableCount == 1 ? TournamentRound.FINAL : TournamentRound.QUALIFIER;
        List<List<UUID>> groups = new ArrayList<>();
        for (int i = 0; i < tableCount; i++) groups.add(new ArrayList<>());
        for (int i = 0; i < players.size(); i++) groups.get(i % tableCount).add(players.get(i));
        Instant now = Instant.now();
        for (int i = 0; i < tableCount; i++) createTable(tournamentId, round, i + 1, groups.get(i), now);
        tournament.setStatus(round == TournamentRound.FINAL ? TournamentStatus.FINAL : TournamentStatus.QUALIFYING);
    }

    private void createTable(UUID tournamentId, TournamentRound round, int tableIndex, List<UUID> players, Instant now) {
        TournamentTableEntity table = tables.save(mapper.newTable(tournamentId, round, tableIndex, now));
        for (int seat = 0; seat < players.size(); seat++) {
            seats.save(mapper.newSeat(table.getId(), players.get(seat), seat));
        }
    }

    private TournamentEntity lock(UUID tournamentId) {
        return tournaments.lockById(tournamentId).orElseThrow(CompetitionExceptions::tournamentNotFound);
    }

    private TournamentResponse view(TournamentEntity tournament) {
        List<TournamentTableEntity> bracket = tables.findByTournamentIdOrderByRoundAscTableIndexAsc(tournament.getId());
        Map<UUID, List<TournamentSeatEntity>> seatsByTable = bracket.isEmpty() ? Map.of()
                : seats.findByTournamentTableIdIn(bracket.stream().map(TournamentTableEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(TournamentSeatEntity::getTournamentTableId));
        List<TournamentTableResponse> tableViews = bracket.stream().map(table -> mapper.table(table,
                seatsByTable.getOrDefault(table.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(TournamentSeatEntity::getSeatIndex)).map(mapper::seat).toList()))
                .toList();
        List<TournamentEntryEntity> all = entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournament.getId());
        long pool = prizePool(tournament, all.stream().filter(entry -> entry.getStatus() != EntryStatus.EXPIRED).count());
        return mapper.tournament(tournament, pool, prizes.split(pool, properties.getTournament().getPrizeShares().size()),
                all.stream().map(entry -> mapper.entry(entry, entry.getStatus() == EntryStatus.INVITED
                        ? invites.confirmBy(tournament, entry.getRegisteredAt()) : null)).toList(), tableViews);
    }
}
