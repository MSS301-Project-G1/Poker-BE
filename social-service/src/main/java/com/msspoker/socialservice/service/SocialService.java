package com.msspoker.socialservice.service;

import com.msspoker.socialservice.dto.response.FriendRequestResponse;
import com.msspoker.socialservice.dto.response.FriendshipResponse;
import com.msspoker.socialservice.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface SocialService {
    FriendshipResponse relation(UUID a, UUID b);

    List<UUID> friendsAmong(UUID actor, List<UUID> ids);

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
