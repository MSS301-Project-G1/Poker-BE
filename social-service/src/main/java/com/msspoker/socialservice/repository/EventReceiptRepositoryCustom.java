package com.msspoker.socialservice.repository;

import java.util.UUID;

public interface EventReceiptRepositoryCustom {
  boolean claim(UUID id, String type);
}
