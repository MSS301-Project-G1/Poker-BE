package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ChatLock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatLockRepository extends JpaRepository<ChatLock, String>, ChatLockRepositoryCustom {
}
