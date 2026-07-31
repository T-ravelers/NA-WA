package me.nawa.auth.refresh;

import me.nawa.config.RedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RedisConfig.class,
        RedisRefreshTokenStore.class
})
@TestPropertySource(properties = {
        "redis.host=127.0.0.1",
        "redis.port=6379",
        "redis.username=",
        "redis.password=",
        "redis.ssl-enabled=false",
        "redis.timeout-millis=2000",
        "redis.key-prefix=nawa:test:"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_REDIS_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class RedisRefreshTokenStoreIntegrationTest {
    private static final String TEST_KEY_PREFIX = "nawa:test:auth:refresh:";

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID sessionId;

    @AfterEach
    void cleanUp() {
        if (sessionId != null) {
            refreshTokenStore.deleteBySessionId(sessionId);
        }
    }

    @Test
    void saveAndFind_validSession_persistsHashWithTtl() {
        RefreshTokenProvider provider = new RefreshTokenProvider(
                60,
                Clock.systemUTC(),
                new SecureRandom()
        );
        RefreshToken token = provider.issueRefreshToken();
        sessionId = token.getSessionId();
        RefreshTokenSession session = new RefreshTokenSession(
                sessionId,
                42L,
                provider.hashToken(token.getValue()),
                token.getIssuedAt(),
                token.getExpiresAt()
        );

        refreshTokenStore.save(session);

        Optional<RefreshTokenSession> found =
                refreshTokenStore.findBySessionId(sessionId);
        assertTrue(found.isPresent());
        RefreshTokenSession storedSession = found.orElseThrow();
        assertEquals(42L, storedSession.getMemberId());
        assertEquals(session.getTokenHash(), storedSession.getTokenHash());
        assertEquals(session.getExpiresAt(), storedSession.getExpiresAt());

        String redisKey = TEST_KEY_PREFIX + sessionId;
        Long ttlMillis = redisTemplate.getExpire(
                redisKey,
                TimeUnit.MILLISECONDS
        );
        assertNotNull(ttlMillis);
        assertTrue(ttlMillis > 0);
        assertTrue(ttlMillis <= 60_000);

        Map<Object, Object> storedValues = redisTemplate.opsForHash()
                .entries(redisKey);
        assertFalse(storedValues.containsValue(token.getValue()));
    }

    @Test
    void deleteBySessionId_existingSession_removesSession() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        sessionId = UUID.randomUUID();
        RefreshTokenSession session = new RefreshTokenSession(
                sessionId,
                42L,
                "hashed-token",
                now,
                now.plusSeconds(60)
        );
        refreshTokenStore.save(session);

        refreshTokenStore.deleteBySessionId(sessionId);

        assertTrue(refreshTokenStore.findBySessionId(sessionId).isEmpty());
    }
}
