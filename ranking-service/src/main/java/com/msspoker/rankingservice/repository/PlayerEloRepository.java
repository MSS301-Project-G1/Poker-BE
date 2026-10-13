package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.PlayerEloEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerEloRepository extends JpaRepository<PlayerEloEntity, UUID> {
    Optional<PlayerEloEntity> findBySeasonIdAndAccountIdAndIsDeletedFalse(UUID seasonId, UUID accountId);

    Page<PlayerEloEntity> findBySeasonIdAndIsDeletedFalse(UUID seasonId, Pageable pageable);

    /** Whole season in leaderboard order, used once when the season closes. */
    List<PlayerEloEntity> findBySeasonIdAndIsDeletedFalse(UUID seasonId, Sort sort);

    List<PlayerEloEntity> findByAccountIdAndFinalTierIsNotNullAndIsDeletedFalse(UUID accountId);

    /** Players ahead on the leaderboard order: Elo descending, then account id ascending. */
    @Query(value = """
            SELECT COUNT(*) FROM player_elo p
            WHERE p.season_id = :seasonId AND p.is_deleted = FALSE
              AND (p.current_elo > :elo OR (p.current_elo = :elo AND p.account_id < :accountId))
            """, nativeQuery = true)
    long countAhead(UUID seasonId, int elo, UUID accountId);
}
