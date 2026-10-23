package com.msspoker.socialservice.service;

import java.util.*;
import com.msspoker.socialservice.dto.response.*;

public interface SocialService {
  FriendshipResponse relation(UUID a, UUID b);
  FriendRequestResponse send(UUID actor, UUID target);
  FriendRequestResponse respond(UUID actor, UUID id, String action);
  void remove(UUID actor, UUID other);
  void block(UUID actor, UUID other);
  void unblock(UUID actor, UUID other);
  PageResponse<UUID> friends(UUID actor, int page, int size);
  List<UUID> internalFriends(UUID actor);
  PageResponse<UUID> blocks(UUID actor, int page, int size);
  PageResponse<FriendRequestResponse> requests(UUID actor, String direction, int page, int size);
}
