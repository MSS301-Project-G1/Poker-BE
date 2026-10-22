package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.Block;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BlockRepository extends JpaRepository<Block, UUID> {
  Optional<Block> findByBlockerIdAndBlockedId(UUID a, UUID b);

  boolean existsByBlockerIdAndBlockedIdAndIsDeletedFalse(UUID a, UUID b);

  Page<Block> findByBlockerIdAndIsDeletedFalse(UUID id, Pageable page);
}
