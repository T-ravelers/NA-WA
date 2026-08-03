package me.nawa.auth.oauth.state;

import me.nawa.auth.oauth.OAuthProvider;
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
import java.util.Objects;
import java.util.Optional;

@Repository
public class RedisOAuthStateStore implements OAuthStateStore {
    private static final String OAUTH_STATE_KEY_PREFIX = "auth:oauth:state:";
    private static final int STORED_FIELD_COUNT = 6;

    private static final DefaultRedisScript<Long> SAVE_IF_ABSENT_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('EXISTS', KEYS[1]) == 1 then "
                            + "return 0; "
                            + "end; "
                            + "redis.call('HSET', KEYS[1], "
                            + "'provider', ARGV[1], "
                            + "'nonce', ARGV[2], "
                            + "'codeVerifier', ARGV[3], "
                            + "'returnPath', ARGV[4], "
                            + "'issuedAt', ARGV[5], "
                            + "'expiresAt', ARGV[6]); "
                            + "redis.call('PEXPIRE', KEYS[1], ARGV[7]); "
                            + "return 1;",
                    Long.class
            );

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CONSUME_SCRIPT =
            new DefaultRedisScript<>(
                    "local values = redis.call('HMGET', KEYS[1], "
                            + "'provider', "
                            + "'nonce', "
                            + "'codeVerifier', "
                            + "'returnPath', "
                            + "'issuedAt', "
                            + "'expiresAt'); "
                            + "if not values[1] then return {}; end; "
                            + "redis.call('DEL', KEYS[1]); "
                            + "return values;",
                    List.class
            );

    private final StringRedisTemplate redisTemplate;
    private final String redisKeyPrefix;
    private final Clock clock;

    @Autowired
    public RedisOAuthStateStore(
            StringRedisTemplate redisTemplate,
            @Value("${redis.key-prefix}") String redisKeyPrefix) {
        this(redisTemplate, redisKeyPrefix, Clock.systemUTC());
    }

    RedisOAuthStateStore(
            StringRedisTemplate redisTemplate,
            String redisKeyPrefix,
            Clock clock) {
        if (!StringUtils.hasText(redisKeyPrefix)) {
            throw new IllegalArgumentException(
                    "Redis key prefix must not be blank"
            );
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
    public boolean saveIfAbsent(OAuthStateSession session) {
        Objects.requireNonNull(session, "OAuth state session is required");
        long ttlMillis = remainingTtlMillis(session);

        Long result = redisTemplate.execute(
                SAVE_IF_ABSENT_SCRIPT,
                List.of(key(session.getState())),
                session.getProvider().getRegistrationId(),
                session.getNonce(),
                session.getCodeVerifier() == null
                        ? ""
                        : session.getCodeVerifier(),
                session.getReturnPath(),
                Long.toString(session.getIssuedAt().toEpochMilli()),
                Long.toString(session.getExpiresAt().toEpochMilli()),
                Long.toString(ttlMillis)
        );

        if (result == null) {
            throw new IllegalStateException("Failed to store OAuth state");
        }
        if (result.longValue() == 1L) {
            return true;
        }
        if (result.longValue() == 0L) {
            return false;
        }
        throw new IllegalStateException(
                "Unexpected OAuth state save result: " + result
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<OAuthStateSession> consume(String state) {
        if (!StringUtils.hasText(state)) {
            return Optional.empty();
        }

        List<String> fields = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key(state))
        );
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        if (fields.size() != STORED_FIELD_COUNT) {
            throw new IllegalStateException(
                    "Stored OAuth state has an invalid field count"
            );
        }

        try {
            return Optional.of(new OAuthStateSession(
                    state,
                    OAuthProvider.fromRegistrationId(fields.get(0)),
                    requiredField(fields.get(1), "nonce"),
                    nullableField(fields.get(2)),
                    requiredField(fields.get(3), "returnPath"),
                    Instant.ofEpochMilli(
                            Long.parseLong(
                                    requiredField(fields.get(4), "issuedAt")
                            )
                    ),
                    Instant.ofEpochMilli(
                            Long.parseLong(
                                    requiredField(fields.get(5), "expiresAt")
                            )
                    )
            ));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored OAuth state is invalid",
                    exception
            );
        }
    }

    private String key(String state) {
        return redisKeyPrefix + OAUTH_STATE_KEY_PREFIX + state;
    }

    private String requiredField(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Required Redis hash field is missing: " + fieldName
            );
        }
        return value;
    }

    private String nullableField(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private long remainingTtlMillis(OAuthStateSession session) {
        Duration ttl = Duration.between(
                clock.instant(),
                session.getExpiresAt()
        );
        long ttlMillis = ttl.toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(
                    "Cannot store an expired OAuth state"
            );
        }
        return ttlMillis;
    }
}
