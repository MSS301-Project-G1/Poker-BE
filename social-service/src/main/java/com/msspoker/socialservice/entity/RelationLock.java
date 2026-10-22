package com.msspoker.socialservice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "relation_locks")
@Getter
@NoArgsConstructor
public class RelationLock {
  @Id
  @Column(name = "pair_key")
  private String pairKey;
}
