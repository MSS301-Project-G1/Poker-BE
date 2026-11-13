package com.msspoker.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_locks")
@Getter
@NoArgsConstructor
public class ChatLockEntity {
    @Id
    @Column(name = "lock_key")
    private String lockKey;
}
