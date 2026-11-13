package com.msspoker.socialservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "relation_locks")
@Getter
@NoArgsConstructor
public class RelationLockEntity {
    @Id
    @Column(name = "pair_key")
    private String pairKey;
}
