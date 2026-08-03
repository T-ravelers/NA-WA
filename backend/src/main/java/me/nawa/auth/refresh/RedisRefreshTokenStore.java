package me.nawa.auth.refresh;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String MEMBER_ID_FIELD = "memberId";
    private static final String TOKEN_HASH_FIELD = "tokenHash";
    private static final String ISSUED_AT_FIELD = "issuedAt";
    private static final String EXPIRES_AT_FIELD = "expiresAt";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private static final DefaultRedisScript<Long> SAVE_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('HSET', KEYS[1], "
                            + "'memberId', ARGV[1], "
                            + "'tokenHash', ARGV[2], "
                            + "'issuedAt', ARGV[3], "
                            + "'expiresAt', ARGV[4]); "
                            + "redis.call('PEXPIRE', KEYS[1], ARGV[5]); "
                            + "return 1;",
                    Long.class
            );

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
            new DefaultRedisScript<>(
                    "local currentHash = redis.call('HGET', KEYS[1], 'tokenHash'); "
                            + "if not currentHash then return 0; end; "
                            + "if currentHash ~= ARGV[1] then "
                            + "redis.call('DEL', KEYS[1]); "
                            + "return -1; "
                            + "end; "
                            + "redis.call('HSET', KEYS[1], "
                            + "'memberId', ARGV[2], "
                            + "'tokenHash', ARGV[3], "
                            + "'issuedAt', ARGV[4], "
                            + "'expiresAt', ARGV[5]); "
                            + "redis.call('PEXPIRE', KEYS[1], ARGV[6]); "
                            + "return 1;",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final String redisKeyPrefix;
    private final Clock clock;

    @Autowired
    public RedisRefreshTokenStore(
            StringRedisTemplate redisTemplate,
            @Value("${redis.key-prefix}") String redisKeyPrefix) {
        this(redisTemplate, redisKeyPrefix, Clock.systemUTC());
    }

    RedisRefreshTokenStore(
            StringRedisTemplate redisTemplate,
            String redisKeyPrefix,
            Clock clock) {
        if (!StringUtils.hasText(redisKeyPrefix)) {
            throw new IllegalArgumentException("Redis key prefix must not be blank");
        }

        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "StringRedisTemplate is required"
        );
        String normalizedPrefix = redisKeyPrefix.trim();
        this.redisKeyPrefix = normalizedPrefix.endsWith(":")
                ? normalizedPrefix
                : normalizedPrefix + ":";
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public void save(RefreshTokenSession session) {
        Objects.requireNonNull(session, "Refresh token session is required");
        long ttlMillis = remainingTtlMillis(session);

        Long result = redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(key(session.getSessionId())),
                Long.toString(session.getMemberId()),
                session.getTokenHash(),
                Long.toString(session.getIssuedAt().getEpochSecond()),
                Long.toString(session.getExpiresAt().getEpochSecond()),
                Long.toString(ttlMillis)
        );

        if (result == null || result.longValue() != 1L) {
            throw new IllegalStateException("Failed to store refresh token session");
        }
    }

    @Override
    public RefreshTokenRotationResult rotate(
            UUID sessionId,
            String currentTokenHash,
            RefreshTokenSession replacementSession) {
        Objects.requireNonNull(sessionId, "Session ID is required");
        if (!StringUtils.hasText(currentTokenHash)) {
            throw new IllegalArgumentException(
                    "Current refresh token hash must not be blank"
            );
        }
        Objects.requireNonNull(
                replacementSession,
                "Replacement refresh token session is required"
        );
        if (!sessionId.equals(replacementSession.getSessionId())) {
            throw new IllegalArgumentException(
                    "Replacement session ID must match the current session ID"
            );
        }

        long ttlMillis = remainingTtlMillis(replacementSession);
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(sessionId)),
                currentTokenHash,
                Long.toString(replacementSession.getMemberId()),
                replacementSession.getTokenHash(),
                Long.toString(replacementSession.getIssuedAt().getEpochSecond()),
                Long.toString(replacementSession.getExpiresAt().getEpochSecond()),
                Long.toString(ttlMillis)
        );

        if (result == null) {
            throw new IllegalStateException("Failed to rotate refresh token session");
        }
        if (result.longValue() == 1L) {
            return RefreshTokenRotationResult.ROTATED;
        }
        if (result.longValue() == 0L) {
            return RefreshTokenRotationResult.NOT_FOUND;
        }
        if (result.longValue() == -1L) {
            return RefreshTokenRotationResult.REUSE_DETECTED;
        }
        throw new IllegalStateException(
                "Unexpected refresh token rotation result: " + result
        );
    }

    @Override
    public Optional<RefreshTokenSession> findBySessionId(UUID sessionId) {
        Objects.requireNonNull(sessionId, "Session ID is required");
        Map<Object, Object> fields = redisTemplate.opsForHash()
                .entries(key(sessionId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new RefreshTokenSession(
                    sessionId,
                    Long.parseLong(requiredField(fields, MEMBER_ID_FIELD)),
                    requiredField(fields, TOKEN_HASH_FIELD),
                    Instant.ofEpochSecond(
                            Long.parseLong(requiredField(fields, ISSUED_AT_FIELD))
                    ),
                    Instant.ofEpochSecond(
                            Long.parseLong(requiredField(fields, EXPIRES_AT_FIELD))
                    )
            ));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored refresh token session is invalid",
                    exception
            );
        }
    }

    @Override
    public void deleteBySessionId(UUID sessionId) {
        Objects.requireNonNull(sessionId, "Session ID is required");
        redisTemplate.delete(key(sessionId));
    }

    private String key(UUID sessionId) {
        return redisKeyPrefix
                + REFRESH_TOKEN_KEY_PREFIX
                + sessionId;
    }

    private String requiredField(Map<Object, Object> fields, String fieldName) {
        Object value = fields.get(fieldName);
        if (value == null || !StringUtils.hasText(value.toString())) {
            throw new IllegalStateException(
                    "Required Redis hash field is missing: " + fieldName
            );
        }
        return value.toString();
    }

    private long remainingTtlMillis(RefreshTokenSession session) {
        Duration ttl = Duration.between(clock.instant(), session.getExpiresAt());
        long ttlMillis = ttl.toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(
                    "Cannot store an expired refresh token session"
            );
        }
        return ttlMillis;
    }
}
