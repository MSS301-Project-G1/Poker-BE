package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;
import com.msspoker.rankingservice.dto.event.PlacementEvent;
import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.ShopGrantRequest;
import com.msspoker.rankingservice.dto.request.TierRewardRequest;
import com.msspoker.rankingservice.dto.request.UpdateSeasonRewardsRequest;
import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.dto.response.SeasonRewardResponse;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.entity.RewardGrantEntity;
import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.enums.*;
import com.msspoker.rankingservice.exception.RankingApiException;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.msspoker.rankingservice.service.SeasonTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = SeasonTestSupport.DATABASE)
@ActiveProfiles("test")
class SeasonLifecycleTest {
    @Autowired SeasonService seasons;
    @Autowired SeasonRewardService rewards;
    @Autowired RatingService rating;
    @Autowired GrantDispatcher dispatcher;
    @Autowired RankingMapper mapper;
    @Autowired EloCalculator calculator;
    @Autowired SeasonRepository seasonRepository;
    @Autowired SeasonTierRewardRepository tierRewards;
    @Autowired SeasonRewardClaimRepository claims;
    @Autowired RewardGrantRepository grants;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository credits;
    @Autowired OutboxRepository outbox;
    @Autowired JsonMapper json;
    @MockitoBean WalletClient wallet;
    @MockitoBean ShopClient shop;

    final UUID god = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    final UUID grand = UUID.fromString("00000000-0000-7000-8000-0000000000a2");
    final UUID diamond = UUID.fromString("00000000-0000-7000-8000-0000000000a3");
    final UUID newbie = UUID.fromString("00000000-0000-7000-8000-0000000000a4");

    @BeforeEach
    void setup() {
        reset(seasonRepository, tierRewards, claims, grants, players, history, credits, outbox);
        save(god, 1700);
        save(grand, 1650);
        save(diamond, 960);
        save(newbie, 15);
    }

    @Test
    void closingSnapshotsFinalTiersKeepsHalfEloAndActivatesTheNextSeason() {
        UUID next = seasons.create(nextSeason(List.of())).id();

        var ended = seasons.close();

        assertEquals(SeasonStatus.ENDED, ended.status());
        assertFalse(ended.endAt().isAfter(Instant.now()));
        SeasonEntity active = seasons.active();
        assertEquals(next, active.getId());
        assertFalse(active.getStartAt().isAfter(Instant.now()));
        Map<UUID, Tier> finalTiers = players.findBySeasonIdAndIsDeletedFalse(SEED_SEASON, Sort.unsorted())
                .stream().collect(Collectors.toMap(PlayerEloEntity::getAccountId, PlayerEloEntity::getFinalTier));
        assertEquals(Map.of(god, Tier.CARD_GOD, grand, Tier.CARD_GOD, diamond, Tier.DIAMOND, newbie, Tier.NEWBIE), finalTiers);
        assertEquals(850, elo(next, god));
        assertEquals(825, elo(next, grand));
        assertEquals(480, elo(next, diamond));
        assertEquals(7, elo(next, newbie));
        assertEquals(Tier.SILVER, players.findBySeasonIdAndAccountIdAndIsDeletedFalse(next, diamond).orElseThrow().getTier());

        JsonNode notice = json.readTree(outbox.findAll().getFirst().getBody()).get("payload");
        assertEquals(1, outbox.count());
        assertEquals("SEASON_REWARD_AVAILABLE", notice.get("type").asString());
        assertEquals(List.of(god.toString(), grand.toString(), diamond.toString()),
                notice.get("accountIds").valueStream().map(JsonNode::asString).toList());
        assertEquals(SEED_SEASON.toString(), notice.get("data").get("seasonId").asString());
    }

    @Test
    void matchesAfterClosingCountForTheNewSeason() {
        UUID next = seasons.create(nextSeason(List.of())).id();
        seasons.close();
        rating.matchFinished(new MatchFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), GameMode.RANK, UUID.randomUUID(), 0,
                List.of(new PlacementEvent(diamond, 1, 2000), new PlacementEvent(newbie, 2, 0)), Instant.now(), Instant.now()));
        assertEquals(500, elo(next, diamond));
        assertEquals(960, elo(SEED_SEASON, diamond));
    }

    @Test
    void schedulerClosesOnlyDueSeasonsWithASuccessorAndActivatesDueOnes() {
        assertError(RankingError.RANK_CONFLICT, () -> seasons.close());
        SeasonEntity seed = seasonRepository.findById(SEED_SEASON).orElseThrow();
        seed.setEndAt(Instant.now().minusSeconds(60));
        seasonRepository.save(seed);
        seasons.closeDueSeason();
        assertEquals(SEED_SEASON, seasons.active().getId());

        UUID next = seasons.create(nextSeason(List.of())).id();
        seasons.closeDueSeason();
        assertEquals(next, seasons.active().getId());
        assertEquals(SeasonStatus.ENDED, seasonRepository.findById(SEED_SEASON).orElseThrow().getStatus());

        SeasonEntity active = seasonRepository.findById(next).orElseThrow();
        active.setStatus(SeasonStatus.ENDED);
        seasonRepository.save(active);
        var later = nextSeason(List.of());
        UUID due = seasons.create(new CreateSeasonRequest("Mùa 3",
                Instant.now().minusSeconds(5), later.endAt(), 50, List.of())).id();
        seasons.closeDueSeason();
        assertEquals(due, seasons.active().getId());
    }

    @Test
    void claimsGrantEveryRewardUpToTheFinalTierOnce() {
        UUID skin = UUID.randomUUID();
        var ladder = new ArrayList<>(SEED_REWARDS.stream()
                .map(row -> new TierRewardRequest((Tier) row[0], RewardType.COIN, (Long) row[1], null)).toList());
        ladder.add(new TierRewardRequest(Tier.DIAMOND, RewardType.PRODUCT, null, skin));
        seasons.updateRewards(SEED_SEASON, new UpdateSeasonRewardsRequest(ladder));
        seasons.create(nextSeason(List.of()));
        assertError(RankingError.RANK_CONFLICT, () -> rewards.claim(SEED_SEASON, diamond));
        seasons.close();
        assertError(RankingError.RANK_CONFLICT, () -> seasons.updateRewards(SEED_SEASON, new UpdateSeasonRewardsRequest(List.of())));

        SeasonRewardResponse claimed = rewards.claim(SEED_SEASON, diamond);
        assertTrue(claimed.claimed());
        assertEquals(Tier.DIAMOND, claimed.finalTier());
        assertEquals(7, claimed.rewards().size());
        assertEquals(1200, claimed.rewards().stream().mapToLong(r -> r.amount()).sum());
        rewards.claim(SEED_SEASON, diamond);
        List<RewardGrantEntity> pending = grants.findByAccountIdOrderByCreatedAtAscIdAsc(diamond);
        assertEquals(7, pending.size());
        assertTrue(pending.stream().anyMatch(g -> g.getIdempotencyKey().equals("season-reward:" + SEED_SEASON + "-DIAMOND:" + diamond)));
        assertTrue(pending.stream().anyMatch(g -> g.getIdempotencyKey().equals("season-item:" + SEED_SEASON + "-" + skin + ":" + diamond)));

        List<SeasonRewardResponse> mine = rewards.mine(diamond);
        assertEquals(1, mine.size());
        assertTrue(mine.getFirst().claimed());
        assertTrue(rewards.mine(newbie).isEmpty());
        assertError(RankingError.RANK_NOT_FOUND, () -> rewards.claim(SEED_SEASON, newbie));
        assertError(RankingError.RANK_NOT_FOUND, () -> rewards.claim(SEED_SEASON, UUID.randomUUID()));
        assertError(RankingError.RANK_NOT_FOUND, () -> rewards.claim(UUID.randomUUID(), diamond));

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();
        ArgumentCaptor<WalletCreditRequest> coins = ArgumentCaptor.forClass(WalletCreditRequest.class);
        verify(wallet, times(6)).credit(coins.capture());
        assertTrue(coins.getAllValues().stream().allMatch(c -> c.reason().equals("SEASON_REWARD") && c.refId().equals(SEED_SEASON.toString())));
        verify(shop).grant(new ShopGrantRequest(diamond, skin, "season-item:" + SEED_SEASON + "-" + skin + ":" + diamond));
        assertTrue(grants.findAll().stream().allMatch(g -> g.getGrantedAt() != null));
    }

    @Test
    void failedGrantsStayPendingForTheNextRun() {
        seasons.create(nextSeason(List.of()));
        seasons.close();
        rewards.claim(SEED_SEASON, grand);
        doThrow(new IllegalStateException("wallet down")).when(wallet).credit(any());
        dispatcher.dispatchPending();
        assertTrue(grants.findAll().stream().allMatch(g -> g.getGrantedAt() == null));
        reset(wallet);
        dispatcher.dispatchPending();
        assertTrue(grants.findAll().stream().allMatch(g -> g.getGrantedAt() != null));
        assertEquals(9, grants.count());
    }

    private void save(UUID accountId, int elo) {
        players.save(mapper.player(SEED_SEASON, accountId, elo, calculator.tier(elo)));
    }

    private int elo(UUID seasonId, UUID accountId) {
        return players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId).orElseThrow().getCurrentElo();
    }

    private void assertError(RankingError code, Executable action) {
        RankingApiException ex = assertThrows(RankingApiException.class, action);
        assertEquals(code, ex.getCode());
    }
}
