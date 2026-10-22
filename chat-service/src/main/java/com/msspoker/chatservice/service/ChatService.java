package com.msspoker.chatservice.service;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.dto.response.ChatDtos.*;
import com.msspoker.chatservice.entity.*;
import com.msspoker.chatservice.exception.ServiceException;
import com.msspoker.chatservice.mapper.ChatMapper;
import com.msspoker.chatservice.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChatService {
  private final ConversationRepository conversations;
  private final MemberRepository members;
  private final MessageRepository messages;
  private final ChatLockRepository locks;
  private final SocialClient social;
  private final ChatMapper mapper;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private final int maxLength;

  public ChatService(
      ConversationRepository c,
      MemberRepository m,
      MessageRepository msg,
      ChatLockRepository locks,
      SocialClient social,
      ChatMapper mapper,
      Clock clock,
      ApplicationEventPublisher events,
      @Value("${chat.max-content-length:2000}") int maxLength) {
    conversations = c;
    members = m;
    messages = msg;
    this.locks = locks;
    this.social = social;
    this.mapper = mapper;
    this.clock = clock;
    this.events = events;
    this.maxLength = maxLength;
  }

  public record MessageCommitted(MessageView message, List<UUID> members) {}

  private List<UUID> memberIds(UUID id) {
    return members.findByConversationIdOrderByAccountId(id).stream()
        .map(ConversationMember::getAccountId)
        .toList();
  }

  private void requireMember(UUID id, UUID actor) {
    if (!members.existsByConversationIdAndAccountId(id, actor)) throw ServiceException.forbidden();
  }

  private void requireFriend(UUID a, UUID b) {
    var relation = social.relation(a, b);
    if (!relation.isFriend() || relation.isBlocked()) throw ServiceException.forbidden();
  }

  private ConversationView view(Conversation c) {
    return mapper.conversation(c, memberIds(c.getId()));
  }

  public ConversationView direct(UUID actor, UUID target) {
    if (actor.equals(target)) throw ServiceException.invalid("Không chat DIRECT với chính mình.");
    requireFriend(actor, target);
    String key =
        actor.toString().compareTo(target.toString()) < 0
            ? actor + ":" + target
            : target + ":" + actor;
    locks.lock("direct:" + key);
    var c =
        conversations
            .findByDirectKeyAndIsDeletedFalse(key)
            .orElseGet(
                () -> {
                  var n = new Conversation();
                  n.setId(UUID.randomUUID());
                  n.setType("DIRECT");
                  n.setDirectKey(key);
                  n.setCreatedAt(clock.instant());
                  n.setUpdatedAt(clock.instant());
                  conversations.saveAndFlush(n);
                  addMember(n.getId(), actor);
                  addMember(n.getId(), target);
                  return n;
                });
    return view(c);
  }

  public void addMember(UUID conversation, UUID account) {
    var m = new ConversationMember();
    m.setId(UUID.randomUUID());
    m.setConversationId(conversation);
    m.setAccountId(account);
    m.setJoinedAt(clock.instant());
    members.saveAndFlush(m);
  }

  @Transactional(readOnly = true)
  public ConversationView get(UUID actor, UUID id) {
    requireMember(id, actor);
    return view(
        conversations
            .findById(id)
            .filter(c -> !c.isDeleted())
            .orElseThrow(ServiceException::missing));
  }

  @Transactional(readOnly = true)
  public PageView<ConversationView> list(UUID actor, int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw ServiceException.invalid("Phân trang không hợp lệ.");
    var p =
        conversations.mine(
            actor,
            PageRequest.of(
                page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    return new PageView<>(
        p.stream().map(this::view).toList(), page, size, p.getTotalElements(), p.getTotalPages());
  }

  public MessageView send(UUID actor, UUID id, SendMessageRequest request) {
    if (request == null || !"TEXT".equals(request.messageType()) || request.content() == null)
      throw ServiceException.invalid("MVP chỉ hỗ trợ TEXT.");
    String content = request.content().strip();
    if (content.isEmpty() || content.length() > maxLength)
      throw ServiceException.invalid("Nội dung rỗng hoặc vượt giới hạn.");
    if (request.clientMessageId() != null) {
      locks.lock("message:" + actor + ":" + request.clientMessageId());
      var old = messages.findBySenderIdAndClientMessageId(actor, request.clientMessageId());
      if (old.isPresent()) {
        var previous = old.get();
        if (!previous.getConversationId().equals(id) || !previous.getContent().equals(content))
          throw ServiceException.conflict("clientMessageId đã dùng cho nội dung khác.");
        return mapper.message(previous);
      }
    }
    var c = conversations.locked(id).orElseThrow(ServiceException::missing);
    requireMember(id, actor);
    if (c.getClosedAt() != null) throw ServiceException.conflict("Phòng chat đã đóng.");
    var recipients = memberIds(id);
    if (c.getType().equals("DIRECT")) {
      if (recipients.size() != 2) throw new IllegalStateException("Invalid DIRECT members");
      requireFriend(
          actor, recipients.stream().filter(a -> !a.equals(actor)).findFirst().orElseThrow());
    }
    var m = new ChatMessage();
    m.setId(UUID.randomUUID());
    m.setConversationId(id);
    m.setSenderId(actor);
    m.setMessageType("TEXT");
    m.setContent(content);
    m.setClientMessageId(request.clientMessageId());
    m.setCreatedAt(clock.instant());
    messages.saveAndFlush(m);
    c.setUpdatedAt(clock.instant());
    var dto = mapper.message(m);
    events.publishEvent(new MessageCommitted(dto, recipients));
    return dto;
  }

  @Transactional(readOnly = true)
  public History history(UUID actor, UUID id, String before, int limit) {
    requireMember(id, actor);
    if (limit < 1 || limit > 100) throw ServiceException.invalid("limit từ 1 đến 100.");
    var page = PageRequest.of(0, limit + 1);
    List<ChatMessage> rows;
    if (before == null) rows = messages.latest(id, page);
    else {
      try {
        String[] cursor =
            new String(Base64.getUrlDecoder().decode(before), StandardCharsets.UTF_8)
                .split("[|]", -1);
        if (cursor.length != 2) throw new IllegalArgumentException();
        rows = messages.before(id, Instant.parse(cursor[0]), UUID.fromString(cursor[1]), page);
      } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
        throw ServiceException.invalid("Cursor không hợp lệ.");
      }
    }
    boolean more = rows.size() > limit;
    var items = rows.stream().limit(limit).map(mapper::message).toList();
    String next = null;
    if (more) {
      var last = items.getLast();
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (last.createdAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
    }
    return new History(items, next);
  }
}
