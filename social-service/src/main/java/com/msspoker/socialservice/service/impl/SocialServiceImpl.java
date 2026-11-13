package com.msspoker.socialservice.service.impl;

import com.msspoker.socialservice.dto.response.FriendRequestResponse;
import com.msspoker.socialservice.dto.response.FriendshipResponse;
import com.msspoker.socialservice.dto.response.PageResponse;
import com.msspoker.socialservice.entity.BlockEntity;
import com.msspoker.socialservice.entity.FriendRequestEntity;
import com.msspoker.socialservice.entity.FriendshipEntity;
import com.msspoker.socialservice.enums.FriendRequestStatus;
import com.msspoker.socialservice.exception.SocialApiException;
import com.msspoker.socialservice.mapper.SocialMapper;
import com.msspoker.socialservice.repository.BlockRepository;
import com.msspoker.socialservice.repository.FriendRequestRepository;
import com.msspoker.socialservice.repository.FriendshipRepository;
import com.msspoker.socialservice.repository.RelationLockRepository;
import com.msspoker.socialservice.service.NotificationOutbox;
import com.msspoker.socialservice.service.SocialService;
import com.msspoker.socialservice.validator.SocialAccessValidator;
import com.msspoker.socialservice.validator.SocialRequestValidator;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialServiceImpl implements SocialService {
    private final FriendRequestRepository requests;
    private final FriendshipRepository friends;
    private final BlockRepository blocks;
    private final RelationLockRepository locks;
    private final NotificationOutbox notifications;
    private final SocialMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SocialAccessValidator access;
    private final SocialRequestValidator validation;

    private void pair(UUID firstAccountId, UUID secondAccountId) {
        access.distinctAccounts(firstAccountId, secondAccountId);
        locks.lock(firstAccountId, secondAccountId);
    }

    @Transactional(readOnly = true)
    @Override
    public FriendshipResponse relation(UUID firstAccountId, UUID secondAccountId) {
        boolean blocked = blocked(firstAccountId, secondAccountId);
        return new FriendshipResponse(
                !blocked
                        && friends.existsByAccountIdAndFriendIdAndIsDeletedFalse(
                                firstAccountId, secondAccountId)
                        && friends.existsByAccountIdAndFriendIdAndIsDeletedFalse(
                                secondAccountId, firstAccountId),
                blocked);
    }

    private boolean blocked(UUID firstAccountId, UUID secondAccountId) {
        return blocks.existsByBlockerIdAndBlockedIdAndIsDeletedFalse(
                        firstAccountId, secondAccountId)
                || blocks.existsByBlockerIdAndBlockedIdAndIsDeletedFalse(
                        secondAccountId, firstAccountId);
    }

    @Override
    public FriendRequestResponse send(UUID actor, UUID target) {
        pair(actor, target);
        if (blocked(actor, target)) throw SocialApiException.forbidden();
        if (friends.existsByAccountIdAndFriendIdAndIsDeletedFalse(actor, target))
            throw SocialApiException.conflict("Hai tài khoản đã là bạn.");
        var pending =
                requests.findByPairKeyAndStatusAndIsDeletedFalse(
                        RelationLockRepository.key(actor, target), FriendRequestStatus.PENDING);
        if (pending.isPresent()) {
            var friendRequest = pending.get();
            if (friendRequest.getAddresseeId().equals(actor)) acceptLocked(friendRequest);
            return mapper.request(friendRequest);
        }
        var friendRequest = new FriendRequestEntity();
        friendRequest.setId(UUID.randomUUID());
        friendRequest.setRequesterId(actor);
        friendRequest.setAddresseeId(target);
        friendRequest.setPairKey(RelationLockRepository.key(actor, target));
        friendRequest.setStatus(FriendRequestStatus.PENDING);
        friendRequest.setCreatedAt(clock.instant());
        friendRequest.setUpdatedAt(clock.instant());
        requests.save(friendRequest);
        notifications.add(target, "FRIEND_REQUEST", actor, friendRequest.getId());
        return mapper.request(friendRequest);
    }

    @Override
    public FriendRequestResponse respond(UUID actor, UUID id, String action) {
        FriendRequestStatus actionStatus = FriendRequestStatus.valueOf(action);
        var friendRequest =
                requests.findById(id)
                        .filter(request -> !request.isDeleted())
                        .orElseThrow(SocialApiException::missing);
        pair(friendRequest.getRequesterId(), friendRequest.getAddresseeId());
        entityManager.refresh(friendRequest);
        if (!(action.equals("CANCELLED")
                        ? friendRequest.getRequesterId()
                        : friendRequest.getAddresseeId())
                .equals(actor)) throw SocialApiException.forbidden();
        if (friendRequest.getStatus() == actionStatus) return mapper.request(friendRequest);
        if (friendRequest.getStatus() != FriendRequestStatus.PENDING)
            throw SocialApiException.conflict("Lời mời không còn chờ xử lý.");
        if (action.equals("ACCEPTED")) {
            if (blocked(friendRequest.getRequesterId(), friendRequest.getAddresseeId()))
                throw SocialApiException.forbidden();
            acceptLocked(friendRequest);
        } else {
            friendRequest.setStatus(actionStatus);
            friendRequest.setUpdatedAt(clock.instant());
        }
        return mapper.request(friendRequest);
    }

    private void acceptLocked(FriendRequestEntity request) {
        request.setStatus(FriendRequestStatus.ACCEPTED);
        request.setUpdatedAt(clock.instant());
        link(request.getRequesterId(), request.getAddresseeId());
        link(request.getAddresseeId(), request.getRequesterId());
        notifications.add(
                request.getRequesterId(),
                "FRIEND_ACCEPTED",
                request.getAddresseeId(),
                request.getId());
    }

    private void link(UUID firstAccountId, UUID secondAccountId) {
        var friendship =
                friends.findByAccountIdAndFriendId(firstAccountId, secondAccountId)
                        .orElseGet(
                                () -> {
                                    var newRelation = new FriendshipEntity();
                                    newRelation.setId(UUID.randomUUID());
                                    newRelation.setAccountId(firstAccountId);
                                    newRelation.setFriendId(secondAccountId);
                                    newRelation.setCreatedAt(clock.instant());
                                    return newRelation;
                                });
        friendship.setDeleted(false);
        friendship.setUpdatedAt(clock.instant());
        friends.save(friendship);
    }

    private void unlink(UUID firstAccountId, UUID secondAccountId) {
        friends.findByAccountIdAndFriendId(firstAccountId, secondAccountId)
                .ifPresent(
                        friendship -> {
                            friendship.setDeleted(true);
                            friendship.setUpdatedAt(clock.instant());
                        });
    }

    @Override
    public void remove(UUID actor, UUID other) {
        pair(actor, other);
        unlink(actor, other);
        unlink(other, actor);
    }

    @Override
    public void block(UUID actor, UUID other) {
        pair(actor, other);
        var block =
                blocks.findByBlockerIdAndBlockedId(actor, other)
                        .orElseGet(
                                () -> {
                                    var newRelation = new BlockEntity();
                                    newRelation.setId(UUID.randomUUID());
                                    newRelation.setBlockerId(actor);
                                    newRelation.setBlockedId(other);
                                    newRelation.setCreatedAt(clock.instant());
                                    return newRelation;
                                });
        block.setDeleted(false);
        block.setUpdatedAt(clock.instant());
        blocks.save(block);
        unlink(actor, other);
        unlink(other, actor);
        requests.findByPairKeyAndStatusAndIsDeletedFalse(
                        RelationLockRepository.key(actor, other), FriendRequestStatus.PENDING)
                .ifPresent(
                        request -> {
                            request.setStatus(FriendRequestStatus.CANCELLED);
                            request.setUpdatedAt(clock.instant());
                        });
    }

    @Override
    public void unblock(UUID actor, UUID other) {
        pair(actor, other);
        blocks.findByBlockerIdAndBlockedId(actor, other)
                .ifPresent(
                        block -> {
                            block.setDeleted(true);
                            block.setUpdatedAt(clock.instant());
                        });
    }

    private Pageable page(int page, int size) {
        validation.page(page, size);
        return PageRequest.of(
                page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    }

    private <T> PageResponse<T> view(Page<?> result, List<T> data) {
        return new PageResponse<>(
                data,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<UUID> friends(UUID actor, int page, int size) {
        var result = friends.findByAccountIdAndIsDeletedFalse(actor, page(page, size));
        return view(result, result.stream().map(FriendshipEntity::getFriendId).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<UUID> internalFriends(UUID actor) {
        return friends.findByAccountIdAndIsDeletedFalse(actor).stream()
                .map(FriendshipEntity::getFriendId)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<UUID> blocks(UUID actor, int page, int size) {
        var result = blocks.findByBlockerIdAndIsDeletedFalse(actor, page(page, size));
        return view(result, result.stream().map(BlockEntity::getBlockedId).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<FriendRequestResponse> requests(
            UUID actor, String direction, int page, int size) {
        validation.requestDirection(direction);
        var result =
                direction.equals("INCOMING")
                        ? requests.findByAddresseeIdAndIsDeletedFalse(actor, page(page, size))
                        : requests.findByRequesterIdAndIsDeletedFalse(actor, page(page, size));
        return view(result, result.stream().map(mapper::request).toList());
    }
}
