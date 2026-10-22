package com.msspoker.socialservice.service;

import java.util.*;

public interface NotificationOutbox {
  void add(UUID recipient, String type, UUID actor, UUID request);
}
