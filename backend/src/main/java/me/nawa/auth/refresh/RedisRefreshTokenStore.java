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
        Duration ttl = Duration.between(clock.instant(), session.getExpiresAt());
        long ttlMillis = ttl.toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(
                    "Cannot store an expired refresh token session"
            );
        }

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
}
