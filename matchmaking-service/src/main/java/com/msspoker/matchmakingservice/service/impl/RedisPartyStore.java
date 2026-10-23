package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.service.PartyStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class RedisPartyStore implements PartyStore {
    private static final String DATA_PREFIX = "mm:v1:party:data:";
    private static final String MEMBERS_PREFIX = "mm:v1:party:members:";
    private static final String ACCOUNT_PREFIX = "mm:v1:party:account:";
    private static final String CODE_PREFIX = "mm:v1:party:code:";
    private static final String QUEUED_PREFIX = "mm:v1:party:queued:";

    private static final DefaultRedisScript<Long> CREATE = script("redis/party-create.lua");
    private static final DefaultRedisScript<Long> JOIN = script("redis/party-join.lua");
    private static final DefaultRedisScript<Long> KICK = script("redis/party-kick.lua");
    private static final DefaultRedisScript<Long> LEAVE = script("redis/party-leave.lua");

    private final StringRedisTemplate redis;

    public RedisPartyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public CreateResult create(UUID partyId, String partyCode, String password, UUID leaderId, Instant createdAt) {
        Long result = redis.execute(
                CREATE,
                List.of(accountKey(leaderId), codeKey(partyCode), dataKey(partyId), membersKey(partyId)),
                partyId.toString(), partyCode, leaderId.toString(), Long.toString(createdAt.toEpochMilli()), password
        );
        if (Long.valueOf(1).equals(result)) return CreateResult.CREATED;
        if (Long.valueOf(-1).equals(result)) return CreateResult.ALREADY_IN_PARTY;
        return CreateResult.CODE_CONFLICT;
    }

    @Override
    public Optional<PartySnapshot> findByAccountId(UUID accountId) {
        String partyId = redis.opsForValue().get(accountKey(accountId));
        if (partyId == null) return Optional.empty();
        return findById(UUID.fromString(partyId));
    }

    @Override
    public Optional<PartySnapshot> findByCode(String partyCode) {
        String partyId = redis.opsForValue().get(codeKey(partyCode));
        if (partyId == null) return Optional.empty();
        return findById(UUID.fromString(partyId));
    }

    @Override
    public JoinResult join(UUID accountId, String partyCode, String password, int maxMembers, Instant joinedAt) {
        Long result = redis.execute(
                JOIN,
                List.of(codeKey(partyCode), accountKey(accountId)),
                accountId.toString(), partyCode, Integer.toString(maxMembers),
                Long.toString(joinedAt.toEpochMilli()), DATA_PREFIX, MEMBERS_PREFIX, QUEUED_PREFIX, password
        );
        if (Long.valueOf(1).equals(result)) return JoinResult.JOINED;
        if (Long.valueOf(-1).equals(result)) return JoinResult.PARTY_NOT_FOUND;
        if (Long.valueOf(-2).equals(result)) return JoinResult.ALREADY_IN_PARTY;
        if (Long.valueOf(-3).equals(result)) return JoinResult.WRONG_PASSWORD;
        if (Long.valueOf(-4).equals(result)) return JoinResult.PARTY_FULL;
        return JoinResult.PARTY_QUEUED;
    }

    @Override
    public KickResult kick(UUID leaderId, UUID accountId, String newPassword) {
        Long result = redis.execute(
                KICK,
                List.of(accountKey(leaderId), accountKey(accountId)),
                leaderId.toString(), accountId.toString(), DATA_PREFIX, MEMBERS_PREFIX, QUEUED_PREFIX, newPassword
        );
        if (Long.valueOf(1).equals(result)) return KickResult.KICKED;
        if (Long.valueOf(-1).equals(result)) return KickResult.PARTY_NOT_FOUND;
        if (Long.valueOf(-2).equals(result)) return KickResult.NOT_LEADER;
        if (Long.valueOf(-3).equals(result)) return KickResult.TARGET_NOT_MEMBER;
        if (Long.valueOf(-4).equals(result)) return KickResult.CANNOT_KICK_LEADER;
        return KickResult.PARTY_QUEUED;
    }

    @Override
    public LeaveResult leave(UUID accountId) {
        Long result = redis.execute(
                LEAVE,
                List.of(accountKey(accountId)),
                accountId.toString(), DATA_PREFIX, MEMBERS_PREFIX, CODE_PREFIX, QUEUED_PREFIX
        );
        if (Long.valueOf(1).equals(result)) return LeaveResult.LEFT;
        if (Long.valueOf(2).equals(result)) return LeaveResult.DISBANDED;
        if (Long.valueOf(-1).equals(result)) return LeaveResult.NOT_MEMBER;
        return LeaveResult.PARTY_QUEUED;
    }

    private Optional<PartySnapshot> findById(UUID partyId) {
        Map<Object, Object> data = redis.opsForHash().entries(dataKey(partyId));
        if (data.isEmpty()) return Optional.empty();
        Set<String> members = redis.opsForZSet().range(membersKey(partyId), 0, -1);
        try {
            return Optional.of(new PartySnapshot(
                    partyId,
                    data.get("partyCode").toString(),
                    // Party tạo trước khi có mật khẩu không có trường này; vẫn đọc được để thành viên rời nhóm.
                    (String) data.get("password"),
                    UUID.fromString(data.get("leaderId").toString()),
                    members == null ? List.of() : members.stream().map(UUID::fromString).toList(),
                    Instant.ofEpochMilli(Long.parseLong(data.get("createdAt").toString()))
            ));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Dữ liệu party bị hỏng: " + partyId, exception);
        }
    }

    private static String dataKey(UUID partyId) { return DATA_PREFIX + partyId; }
    private static String membersKey(UUID partyId) { return MEMBERS_PREFIX + partyId; }
    private static String accountKey(UUID accountId) { return ACCOUNT_PREFIX + accountId; }
    private static String codeKey(String partyCode) { return CODE_PREFIX + partyCode; }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }
}
