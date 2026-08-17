package me.nawa.auth.oauth.state;

import me.nawa.auth.oauth.OAuthProvider;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RedisConfig.class,
        RedisOAuthStateStore.class
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
class RedisOAuthStateStoreIntegrationTest {
    private static final String TEST_KEY_PREFIX =
            "nawa:test:auth:oauth:state:";

    @Autowired
    private OAuthStateStore stateStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<String> states = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String state : states) {
            redisTemplate.delete(TEST_KEY_PREFIX + state);
        }
    }

    @Test
    void saveIfAbsent_newState_storesHashWithTtl() {
        OAuthStateSession session = createSession(OAuthProvider.LINE);

        assertTrue(stateStore.saveIfAbsent(session));

        String redisKey = TEST_KEY_PREFIX + session.getState();
        Long ttlMillis = redisTemplate.getExpire(
                redisKey,
                TimeUnit.MILLISECONDS
        );
        assertNotNull(ttlMillis);
        assertTrue(ttlMillis > 0);
        assertTrue(ttlMillis <= 60_000);

        Map<Object, Object> stored = redisTemplate.opsForHash()
                .entries(redisKey);
        assertEquals("line", stored.get("provider"));
        assertEquals(session.getNonce(), stored.get("nonce"));
        assertEquals(session.getCodeVerifier(), stored.get("codeVerifier"));
        assertEquals(
                session.getBrowserBindingHash(),
                stored.get("browserBindingHash")
        );
        assertEquals("/", stored.get("returnPath"));
    }

    @Test
    void saveIfAbsent_existingState_keepsOriginalSession() {
        OAuthStateSession original = createSession(OAuthProvider.LINE);
        OAuthStateSession collision = new OAuthStateSession(
                original.getState(),
                OAuthProvider.LINE,
                "different-nonce",
                "different-verifier",
                "different-binding-hash",
                "/",
                original.getIssuedAt(),
                original.getExpiresAt()
        );
        assertTrue(stateStore.saveIfAbsent(original));

        assertFalse(stateStore.saveIfAbsent(collision));

        OAuthStateSession consumed = stateStore.consume(original.getState())
                .orElseThrow();
        assertEquals(original.getNonce(), consumed.getNonce());
        assertEquals(original.getCodeVerifier(), consumed.getCodeVerifier());
    }

    @Test
    void consume_existingState_returnsAndDeletesSessionAtomically() {
        OAuthStateSession session = createSession(OAuthProvider.GOOGLE);
        assertTrue(stateStore.saveIfAbsent(session));

        Optional<OAuthStateSession> first = stateStore.consume(
                session.getState()
        );
        Optional<OAuthStateSession> second = stateStore.consume(
                session.getState()
        );

        assertTrue(first.isPresent());
        OAuthStateSession consumed = first.orElseThrow();
        assertEquals(session.getProvider(), consumed.getProvider());
        assertEquals(session.getNonce(), consumed.getNonce());
        assertEquals(session.getCodeVerifier(), consumed.getCodeVerifier());
        assertEquals(
                session.getBrowserBindingHash(),
                consumed.getBrowserBindingHash()
        );
        assertEquals(session.getReturnPath(), consumed.getReturnPath());
        assertEquals(session.getIssuedAt(), consumed.getIssuedAt());
        assertEquals(session.getExpiresAt(), consumed.getExpiresAt());
        assertFalse(second.isPresent());
        assertFalse(redisTemplate.hasKey(
                TEST_KEY_PREFIX + session.getState()
        ));
    }

    @Test
    void consume_concurrentCallbacks_allowsExactlyOneConsumer()
            throws Exception {
        OAuthStateSession session = createSession(OAuthProvider.LINE);
        assertTrue(stateStore.saveIfAbsent(session));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<OAuthStateSession>> first = executor.submit(
                    consumeTask(ready, start, session.getState())
            );
            Future<Optional<OAuthStateSession>> second = executor.submit(
                    consumeTask(ready, start, session.getState())
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Optional<OAuthStateSession> firstResult =
                    first.get(5, TimeUnit.SECONDS);
            Optional<OAuthStateSession> secondResult =
                    second.get(5, TimeUnit.SECONDS);

            assertEquals(
                    1,
                    (firstResult.isPresent() ? 1 : 0)
                            + (secondResult.isPresent() ? 1 : 0)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saveIfAbsent_expiredState_throwsException() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OAuthStateSession expired = new OAuthStateSession(
                newState(),
                OAuthProvider.GOOGLE,
                "nonce",
                "verifier-" + UUID.randomUUID(),
                "binding-hash",
                "/",
                now.minusSeconds(60),
                now.minusSeconds(1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> stateStore.saveIfAbsent(expired)
        );
    }

    private OAuthStateSession createSession(OAuthProvider provider) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new OAuthStateSession(
                newState(),
                provider,
                "nonce-" + UUID.randomUUID(),
                provider.isPkceRequired()
                        ? "verifier-" + UUID.randomUUID()
                        : null,
                "binding-hash-" + UUID.randomUUID(),
                "/",
                issuedAt,
                issuedAt.plusSeconds(60)
        );
    }

    private String newState() {
        String state = UUID.randomUUID().toString();
        states.add(state);
        return state;
    }

    private Callable<Optional<OAuthStateSession>> consumeTask(
            CountDownLatch ready,
            CountDownLatch start,
            String state) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting to consume OAuth state"
                );
            }
            return stateStore.consume(state);
        };
    }
}
