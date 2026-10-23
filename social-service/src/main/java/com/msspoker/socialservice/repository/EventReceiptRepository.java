package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventReceiptRepository extends JpaRepository<ProcessedEvent, UUID>, EventReceiptRepositoryCustom {
}
