package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventReceiptRepository extends JpaRepository<ProcessedEvent, UUID>, EventReceiptRepositoryCustom {
}
