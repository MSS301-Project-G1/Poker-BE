package com.msspoker.socialservice.service;

import com.msspoker.socialservice.dto.response.SocialDtos.*;
import com.msspoker.socialservice.entity.*;
import com.msspoker.socialservice.exception.ServiceException;
import com.msspoker.socialservice.mapper.SocialMapper;
import com.msspoker.socialservice.repository.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialService {
  private final FriendRequestRepository requests;
  private final FriendshipRepository friends;
  private final BlockRepository blocks;
  private final RelationLockRepository locks;
  private final NotificationOutbox notifications;
  private final SocialMapper mapper;
  private final EntityManager em;
  private final Clock clock;

  private void pair(UUID a, UUID b) {
    if (a.equals(b)) throw ServiceException.invalid("Không thể thao tác với chính mình.");
    locks.lock(a, b);
  }

  @Transactional(readOnly = true)
  public RelationView relation(UUID a, UUID b) {
    boolean blocked = blocked(a, b);
    return new RelationView(
        !blocked
            && friends.existsByAccountIdAndFriendIdAndIsDeletedFalse(a, b)
            && friends.existsByAccountIdAndFriendIdAndIsDeletedFalse(b, a),
        blocked);
  }

  private boolean blocked(UUID a, UUID b) {
    return blocks.existsByBlockerIdAndBlockedIdAndIsDeletedFalse(a, b)
        || blocks.existsByBlockerIdAndBlockedIdAndIsDeletedFalse(b, a);
  }

  public FriendRequestView send(UUID actor, UUID target) {
    pair(actor, target);
    if (blocked(actor, target)) throw ServiceException.forbidden();
    if (friends.existsByAccountIdAndFriendIdAndIsDeletedFalse(actor, target))
      throw ServiceException.conflict("Hai tài khoản đã là bạn.");
    var pending =
        requests.findByPairKeyAndStatusAndIsDeletedFalse(
            RelationLockRepository.key(actor, target), "PENDING");
    if (pending.isPresent()) {
      var row = pending.get();
      if (row.getAddresseeId().equals(actor)) acceptLocked(row);
      return mapper.request(row);
    }
    var row = new FriendRequest();
    row.setId(UUID.randomUUID());
    row.setRequesterId(actor);
    row.setAddresseeId(target);
    row.setPairKey(RelationLockRepository.key(actor, target));
    row.setStatus("PENDING");
    row.setCreatedAt(clock.instant());
    row.setUpdatedAt(clock.instant());
    requests.save(row);
    notifications.add(target, "FRIEND_REQUEST", actor, row.getId());
    return mapper.request(row);
  }

  public FriendRequestView respond(UUID actor, UUID id, String action) {
    var row =
        requests.findById(id).filter(r -> !r.isDeleted()).orElseThrow(ServiceException::missing);
    pair(row.getRequesterId(), row.getAddresseeId());
    em.refresh(row);
    if (!(action.equals("CANCELLED") ? row.getRequesterId() : row.getAddresseeId()).equals(actor))
      throw ServiceException.forbidden();
    if (row.getStatus().equals(action)) return mapper.request(row);
    if (!row.getStatus().equals("PENDING"))
      throw ServiceException.conflict("Lời mời không còn chờ xử lý.");
    if (action.equals("ACCEPTED")) {
      if (blocked(row.getRequesterId(), row.getAddresseeId())) throw ServiceException.forbidden();
      acceptLocked(row);
    } else {
      row.setStatus(action);
      row.setUpdatedAt(clock.instant());
    }
    return mapper.request(row);
  }

  private void acceptLocked(FriendRequest r) {
    r.setStatus("ACCEPTED");
    r.setUpdatedAt(clock.instant());
    link(r.getRequesterId(), r.getAddresseeId());
    link(r.getAddresseeId(), r.getRequesterId());
    notifications.add(r.getRequesterId(), "FRIEND_ACCEPTED", r.getAddresseeId(), r.getId());
  }

  private void link(UUID a, UUID b) {
    var f =
        friends
            .findByAccountIdAndFriendId(a, b)
            .orElseGet(
                () -> {
                  var n = new Friendship();
                  n.setId(UUID.randomUUID());
                  n.setAccountId(a);
                  n.setFriendId(b);
                  n.setCreatedAt(clock.instant());
                  return n;
                });
    f.setDeleted(false);
    f.setUpdatedAt(clock.instant());
    friends.save(f);
  }

  private void unlink(UUID a, UUID b) {
    friends
        .findByAccountIdAndFriendId(a, b)
        .ifPresent(
            f -> {
              f.setDeleted(true);
              f.setUpdatedAt(clock.instant());
            });
  }

  public void remove(UUID actor, UUID other) {
    pair(actor, other);
    unlink(actor, other);
    unlink(other, actor);
  }

  public void block(UUID actor, UUID other) {
    pair(actor, other);
    var b =
        blocks
            .findByBlockerIdAndBlockedId(actor, other)
            .orElseGet(
                () -> {
                  var n = new Block();
                  n.setId(UUID.randomUUID());
                  n.setBlockerId(actor);
                  n.setBlockedId(other);
                  n.setCreatedAt(clock.instant());
                  return n;
                });
    b.setDeleted(false);
    b.setUpdatedAt(clock.instant());
    blocks.save(b);
    unlink(actor, other);
    unlink(other, actor);
    requests
        .findByPairKeyAndStatusAndIsDeletedFalse(
            RelationLockRepository.key(actor, other), "PENDING")
        .ifPresent(
            r -> {
              r.setStatus("CANCELLED");
              r.setUpdatedAt(clock.instant());
            });
  }

  public void unblock(UUID actor, UUID other) {
    pair(actor, other);
    blocks
        .findByBlockerIdAndBlockedId(actor, other)
        .ifPresent(
            b -> {
              b.setDeleted(true);
              b.setUpdatedAt(clock.instant());
            });
  }

  private Pageable page(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw ServiceException.invalid("page>=0, size từ 1 đến 100.");
    return PageRequest.of(
        page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
  }

  private <T> PageView<T> view(Page<?> p, List<T> data) {
    return new PageView<>(
        data, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public PageView<UUID> friends(UUID actor, int page, int size) {
    var p = friends.findByAccountIdAndIsDeletedFalse(actor, page(page, size));
    return view(p, p.stream().map(Friendship::getFriendId).toList());
  }

  @Transactional(readOnly = true)
  public List<UUID> internalFriends(UUID actor) {
    return friends.findByAccountIdAndIsDeletedFalse(actor).stream()
        .map(Friendship::getFriendId)
        .toList();
  }

  @Transactional(readOnly = true)
  public PageView<UUID> blocks(UUID actor, int page, int size) {
    var p = blocks.findByBlockerIdAndIsDeletedFalse(actor, page(page, size));
    return view(p, p.stream().map(Block::getBlockedId).toList());
  }

  @Transactional(readOnly = true)
  public PageView<FriendRequestView> requests(UUID actor, String direction, int page, int size) {
    if (!Set.of("INCOMING", "OUTGOING").contains(direction))
      throw ServiceException.invalid("direction phải là INCOMING hoặc OUTGOING.");
    var p =
        direction.equals("INCOMING")
            ? requests.findByAddresseeIdAndIsDeletedFalse(actor, page(page, size))
            : requests.findByRequesterIdAndIsDeletedFalse(actor, page(page, size));
    return view(p, p.stream().map(mapper::request).toList());
  }
}
