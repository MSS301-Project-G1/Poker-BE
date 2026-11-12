package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.PlayerEloEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerEloRepository extends JpaRepository<PlayerEloEntity, UUID> {
    Optional<PlayerEloEntity> findBySeasonIdAndAccountIdAndIsDeletedFalse(UUID seasonId, UUID accountId);

    Page<PlayerEloEntity> findBySeasonIdAndIsDeletedFalse(UUID seasonId, Pageable pageable);

    /** Leaderboard page without the excluded (ADMIN) accounts. */
    Page<PlayerEloEntity> findBySeasonIdAndAccountIdNotInAndIsDeletedFalse(UUID seasonId, Collection<UUID> excluded,
                                                                            Pageable pageable);

    /** Whole season in leaderboard order, used once when the season closes. */
    List<PlayerEloEntity> findBySeasonIdAndIsDeletedFalse(UUID seasonId, Sort sort);

    List<PlayerEloEntity> findByAccountIdAndFinalTierIsNotNullAndIsDeletedFalse(UUID accountId);

    List<PlayerEloEntity> findBySeasonIdAndAccountIdInAndIsDeletedFalse(UUID seasonId, Collection<UUID> accountIds);

    /**
     * Season leaderboard positions of the given players in one query, by the same order as countAhead.
     * Excluded accounts get no position and are not counted ahead of anyone; the list must not be empty.
     * The id is returned as text because H2 projects native UUID columns as byte[].
     */
    @Query(value = """
            SELECT CAST(f.account_id AS VARCHAR) AS "accountId",
                   (SELECT COUNT(*) FROM player_elo p
                    WHERE p.season_id = f.season_id AND p.is_deleted = FALSE AND p.account_id NOT IN (:excluded)
                      AND (p.current_elo > f.current_elo
                           OR (p.current_elo = f.current_elo AND p.account_id < f.account_id))) + 1 AS "position"
            FROM player_elo f
            WHERE f.season_id = :seasonId AND f.is_deleted = FALSE AND f.account_id IN (:accountIds)
              AND f.account_id NOT IN (:excluded)
            """, nativeQuery = true)
    List<Position> positions(UUID seasonId, Collection<UUID> accountIds, Collection<UUID> excluded);

    interface Position {
        String getAccountId();

        long getPosition();
    }

    /**
     * Players ahead on the leaderboard order: Elo descending, then account id ascending. Excluded accounts
     * are not counted; the list must not be empty.
     */
    @Query(value = """
            SELECT COUNT(*) FROM player_elo p
            WHERE p.season_id = :seasonId AND p.is_deleted = FALSE AND p.account_id NOT IN (:excluded)
              AND (p.current_elo > :elo OR (p.current_elo = :elo AND p.account_id < :accountId))
            """, nativeQuery = true)
    long countAhead(UUID seasonId, int elo, UUID accountId, Collection<UUID> excluded);
}
