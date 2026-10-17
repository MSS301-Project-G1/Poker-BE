package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.response.FriendshipResponse;
import com.msspoker.competitionservice.dto.response.PageResponse;
import com.msspoker.competitionservice.dto.response.RoomResponse;
import com.msspoker.competitionservice.dto.response.RoomSummaryResponse;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.entity.CustomRoomEntity;
import com.msspoker.competitionservice.entity.RoomMemberEntity;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.enums.NotificationType;
import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.enums.RoomVisibility;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.mapper.RoomMapper;
import com.msspoker.competitionservice.repository.CustomRoomRepository;
import com.msspoker.competitionservice.repository.RoomMemberRepository;
import com.msspoker.competitionservice.service.GameClient;
import com.msspoker.competitionservice.service.NotificationService;
import com.msspoker.competitionservice.service.RoomService;
import com.msspoker.competitionservice.service.SocialClient;
import com.msspoker.competitionservice.util.CompetitionIds;
import com.msspoker.competitionservice.validator.RoomAccessValidator;
import com.msspoker.competitionservice.validator.RoomSettingsValidator;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private static final int CODE_ATTEMPTS = 10;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CustomRoomRepository rooms;
    private final RoomMemberRepository members;
    private final RoomMapper mapper;
    private final RoomAccessValidator access;
    private final RoomSettingsValidator settings;
    private final PasswordEncoder passwords;
    private final NotificationService notifications;
    private final GameClient game;
    private final SocialClient social;
    private final CompetitionProperties properties;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    @Override
    public RoomResponse create(UUID accountId, CreateRoomRequest request) {
        settings.validate(request);
        requireNoOpenRoom(accountId);
        String hash = request.password() == null ? null : passwords.encode(request.password());
        Instant now = Instant.now();
        CustomRoomEntity room = rooms.save(mapper.newRoom(request, newCode(), accountId, hash, now));
        RoomMemberEntity owner = members.save(mapper.newMember(room.getId(), accountId, now));
        return view(room, List.of(owner));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<RoomSummaryResponse> publicRooms(int page, int size) {
        Page<CustomRoomEntity> result = rooms.findByVisibilityAndStatusAndIsDeletedFalse(RoomVisibility.PUBLIC,
                RoomStatus.WAITING, PageRequest.of(page, size, NEWEST_FIRST));
        List<RoomSummaryResponse> content = result.getContent().stream()
                .map(room -> mapper.summary(room, (int) members.countByRoomId(room.getId()))).toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<RoomResponse> mine(UUID accountId) {
        return rooms.findOpenRoomOf(accountId, RoomStatus.CLOSED).map(room -> view(room, roster(room)));
    }

    @Transactional(readOnly = true)
    @Override
    public RoomResponse get(String roomCode, UUID accountId) {
        CustomRoomEntity room = rooms.findByRoomCodeAndIsDeletedFalse(normalize(roomCode))
                .filter(found -> found.getStatus() != RoomStatus.CLOSED).orElseThrow(CompetitionExceptions::roomNotFound);
        List<RoomMemberEntity> roster = roster(room);
        access.member(roster, accountId);
        return view(room, roster);
    }

    @Transactional
    @Override
    public RoomResponse join(String roomCode, UUID accountId, String password) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = new ArrayList<>(roster(room));
        // Joining again is harmless, e.g. after a page reload.
        if (roster.stream().anyMatch(m -> m.getAccountId().equals(accountId))) return view(room, roster);
        access.waiting(room);
        requireNoOpenRoom(accountId);
        if (room.getPasswordHash() != null && (password == null || !passwords.matches(password, room.getPasswordHash()))) {
            throw CompetitionExceptions.wrongPassword();
        }
        if (roster.size() >= room.getMaxPlayers()) throw CompetitionExceptions.roomFull();
        roster.add(members.save(mapper.newMember(room.getId(), accountId, Instant.now())));
        updated(room, roster, "JOINED", List.of());
        return view(room, roster);
    }

    @Transactional
    @Override
    public void leave(String roomCode, UUID accountId) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = new ArrayList<>(roster(room));
        RoomMemberEntity member = access.member(roster, accountId);
        access.waiting(room);
        members.delete(member);
        roster.remove(member);
        if (roster.isEmpty()) {
            room.setStatus(RoomStatus.CLOSED);
            return;
        }
        // The longest-waiting member inherits the room.
        if (room.getOwnerId().equals(accountId)) room.setOwnerId(roster.getFirst().getAccountId());
        updated(room, roster, "LEFT", List.of());
    }

    @Transactional
    @Override
    public RoomResponse ready(String roomCode, UUID accountId, boolean ready) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = roster(room);
        RoomMemberEntity member = access.member(roster, accountId);
        access.waiting(room);
        member.setReady(ready);
        updated(room, roster, "READY", List.of());
        return view(room, roster);
    }

    @Transactional
    @Override
    public RoomResponse kick(String roomCode, UUID ownerId, UUID targetId) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = new ArrayList<>(roster(room));
        access.member(roster, ownerId);
        access.owner(room, ownerId);
        access.waiting(room);
        RoomMemberEntity target = otherMember(roster, ownerId, targetId);
        members.delete(target);
        roster.remove(target);
        updated(room, roster, "KICKED", List.of(targetId));
        return view(room, roster);
    }

    @Transactional
    @Override
    public RoomResponse transferOwner(String roomCode, UUID ownerId, UUID targetId) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = roster(room);
        access.member(roster, ownerId);
        access.owner(room, ownerId);
        otherMember(roster, ownerId, targetId);
        room.setOwnerId(targetId);
        updated(room, roster, "OWNER_CHANGED", List.of());
        return view(room, roster);
    }

    @Transactional
    @Override
    public void invite(String roomCode, UUID accountId, UUID targetId) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = roster(room);
        access.member(roster, accountId);
        if (accountId.equals(targetId)) throw CompetitionExceptions.invalidTarget();
        if (roster.stream().anyMatch(m -> m.getAccountId().equals(targetId))) throw CompetitionExceptions.alreadyMember();
        FriendshipResponse friendship = social.friendship(accountId, targetId);
        if (!friendship.isFriend() || friendship.isBlocked()) throw CompetitionExceptions.notFriends();
        notifications.notify(NotificationType.ROOM_INVITE, List.of(targetId), "Lời mời vào phòng",
                "Bạn được mời vào phòng " + room.getRoomCode() + ".",
                Map.of("roomId", room.getId(), "roomCode", room.getRoomCode(), "inviterId", accountId,
                        "hasPassword", room.getPasswordHash() != null));
    }

    /**
     * The sourceId is committed before calling game-service, so a retry after a timeout reuses it and
     * CreateTable returns the table that may already exist instead of opening a second one.
     */
    @Override
    public RoomResponse start(String roomCode, UUID accountId) {
        CreateTableRequest request = transactions.execute(status -> prepareStart(roomCode, accountId, false));
        TableCreatedResponse table;
        try {
            table = game.createTable(request);
        } catch (CompetitionApiException ex) {
            if (ex.getCode() != CompetitionError.COMP_SOURCE_CONFLICT) throw ex;
            // An earlier attempt reached game-service with another roster; this start needs a new source.
            request = transactions.execute(status -> prepareStart(roomCode, accountId, true));
            table = game.createTable(request);
        }
        UUID sourceId = request.sourceId();
        TableCreatedResponse created = table;
        return transactions.execute(status -> markStarted(roomCode, sourceId, created));
    }

    /** Idempotent: only the room whose current source matches is reset, once. */
    @Transactional
    @Override
    public void matchFinished(MatchFinishedEvent event) {
        if (event == null || event.mode() == null || event.sourceId() == null) throw CompetitionExceptions.invalidEvent();
        if (event.mode() != GameMode.CUSTOM) return;
        rooms.lockBySourceId(event.sourceId()).ifPresent(room -> {
            boolean wasPlaying = room.getStatus() == RoomStatus.PLAYING;
            room.setStatus(RoomStatus.WAITING);
            room.setCurrentSourceId(null);
            room.setTableId(null);
            List<RoomMemberEntity> roster = roster(room);
            roster.forEach(member -> member.setReady(false));
            if (wasPlaying) updated(room, roster, "MATCH_FINISHED", List.of());
        });
    }

    private CreateTableRequest prepareStart(String roomCode, UUID accountId, boolean newSource) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = roster(room);
        access.member(roster, accountId);
        access.owner(room, accountId);
        access.waiting(room);
        int minPlayers = properties.getRoom().getMinPlayers();
        if (roster.size() < minPlayers) throw CompetitionExceptions.notEnoughPlayers(minPlayers);
        if (roster.stream().anyMatch(m -> !m.getAccountId().equals(room.getOwnerId()) && !m.isReady())) {
            throw CompetitionExceptions.playersNotReady();
        }
        if (newSource || room.getCurrentSourceId() == null) room.setCurrentSourceId(CompetitionIds.next());
        CompetitionProperties.Room defaults = properties.getRoom();
        return new CreateTableRequest(GameMode.CUSTOM, room.getCurrentSourceId(), ids(roster),
                mapper.settings(room, defaults.getStartingChips(), defaults.getTurnTimeSeconds()), 0);
    }

    private RoomResponse markStarted(String roomCode, UUID sourceId, TableCreatedResponse table) {
        CustomRoomEntity room = lock(roomCode);
        List<RoomMemberEntity> roster = roster(room);
        if (room.getStatus() == RoomStatus.WAITING && sourceId.equals(room.getCurrentSourceId())) {
            room.setStatus(RoomStatus.PLAYING);
            room.setTableId(table.tableId());
            notifications.notify(NotificationType.ROOM_STARTED, ids(roster), "Trận đấu bắt đầu",
                    "Phòng " + room.getRoomCode() + " đã mở bàn chơi.",
                    Map.of("roomId", room.getId(), "roomCode", room.getRoomCode(), "tableId", table.tableId()));
        }
        return view(room, roster);
    }

    private void updated(CustomRoomEntity room, List<RoomMemberEntity> roster, String change, Collection<UUID> extra) {
        notifications.notify(NotificationType.ROOM_UPDATED, Stream.concat(ids(roster).stream(), extra.stream()).toList(),
                "Phòng " + room.getRoomCode() + " đã cập nhật", "Có thay đổi trong phòng chờ.",
                Map.of("roomId", room.getId(), "roomCode", room.getRoomCode(), "status", room.getStatus(),
                        "change", change));
    }

    private RoomMemberEntity otherMember(List<RoomMemberEntity> roster, UUID ownerId, UUID targetId) {
        if (ownerId.equals(targetId)) throw CompetitionExceptions.invalidTarget();
        return roster.stream().filter(m -> m.getAccountId().equals(targetId)).findFirst()
                .orElseThrow(CompetitionExceptions::invalidTarget);
    }

    private void requireNoOpenRoom(UUID accountId) {
        if (rooms.findOpenRoomOf(accountId, RoomStatus.CLOSED).isPresent()) throw CompetitionExceptions.alreadyInRoom();
    }

    private CustomRoomEntity lock(String roomCode) {
        return rooms.lockByRoomCode(normalize(roomCode)).filter(room -> room.getStatus() != RoomStatus.CLOSED)
                .orElseThrow(CompetitionExceptions::roomNotFound);
    }

    private List<RoomMemberEntity> roster(CustomRoomEntity room) {
        return members.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId());
    }

    private RoomResponse view(CustomRoomEntity room, List<RoomMemberEntity> roster) {
        return mapper.room(room, roster.stream()
                .map(member -> mapper.member(member, member.getAccountId().equals(room.getOwnerId()))).toList());
    }

    private List<UUID> ids(List<RoomMemberEntity> roster) {
        return roster.stream().map(RoomMemberEntity::getAccountId).toList();
    }

    private String newCode() {
        int length = properties.getRoom().getCodeLength();
        String alphabet = CompetitionConstants.ROOM_CODE_ALPHABET;
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(length);
            for (int i = 0; i < length; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
            if (!rooms.existsByRoomCode(code.toString())) return code.toString();
        }
        throw CompetitionExceptions.roomCodeExhausted();
    }

    private String normalize(String roomCode) {
        return roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
    }
}
