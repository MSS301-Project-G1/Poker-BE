package com.msspoker.rankingservice.constant;

import org.springframework.data.domain.Sort;

public final class RankingConstants {
    public static final String RANK_REWARD_REASON = "RANK_REWARD";
    public static final String RANK_REWARD_KEY_FORMAT = "rank-reward:%s:%s";
    public static final String SEASON_REWARD_REASON = "SEASON_REWARD";
    /** SPEC §5.4 key format reason:sourceId:accountId; the source is the season plus tier or product. */
    public static final String SEASON_COIN_KEY_FORMAT = "season-reward:%s-%s:%s";
    public static final String SEASON_ITEM_KEY_FORMAT = "season-item:%s-%s:%s";
    public static final String ADMIN_ROLE = "ADMIN";
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_NAME_LENGTH = 100;
    /** Leaderboard order; PlayerEloRepository.countAhead must use the same order. */
    public static final Sort LEADERBOARD_SORT = Sort.by(Sort.Order.desc("currentElo"), Sort.Order.asc("accountId"));

    private RankingConstants() {
    }
}
