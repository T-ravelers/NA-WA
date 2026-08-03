package me.nawa.auth.oauth.state;

import me.nawa.auth.oauth.OAuthProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthStateServiceTest {
    private static final Instant NOW = Instant.parse(
            "2026-08-03T00:00:00Z"
    );

    @Test
    void issue_line_generatesStateNonceAndPkceAndStoresSession()
            throws Exception {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        OAuthStateServiceImpl service = createService(store);

        OAuthAuthorizationRequestState issued = service.issue(
                OAuthProvider.LINE,
                "/"
        );

        OAuthStateSession stored = store.savedSessions.get(0);
        assertUrlSafeRandomValue(issued.getState());
        assertUrlSafeRandomValue(issued.getNonce());
        assertUrlSafeRandomValue(stored.getCodeVerifier());
        assertEquals("S256", issued.getCodeChallengeMethod());
        assertEquals(
                createCodeChallenge(stored.getCodeVerifier()),
                issued.getCodeChallenge()
        );
        assertEquals(issued.getState(), stored.getState());
        assertEquals(issued.getNonce(), stored.getNonce());
        assertEquals(OAuthProvider.LINE, stored.getProvider());
        assertEquals("/", stored.getReturnPath());
        assertEquals(NOW, stored.getIssuedAt());
        assertEquals(NOW.plusSeconds(600), stored.getExpiresAt());
        assertEquals(stored.getExpiresAt(), issued.getExpiresAt());
    }

    @Test
    void issue_google_generatesStateNonceAndPkce() throws Exception {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        OAuthStateServiceImpl service = createService(store);

        OAuthAuthorizationRequestState issued = service.issue(
                OAuthProvider.GOOGLE,
                null
        );

        OAuthStateSession stored = store.savedSessions.get(0);
        assertUrlSafeRandomValue(issued.getState());
        assertUrlSafeRandomValue(issued.getNonce());
        assertUrlSafeRandomValue(stored.getCodeVerifier());
        assertEquals("S256", issued.getCodeChallengeMethod());
        assertEquals(
                createCodeChallenge(stored.getCodeVerifier()),
                issued.getCodeChallenge()
        );
        assertEquals(OAuthProvider.GOOGLE, stored.getProvider());
        assertEquals("/", stored.getReturnPath());
    }

    @Test
    void issue_stateCollision_retriesWithNewState() {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        store.rejectSaveCount = 1;
        OAuthStateServiceImpl service = createService(store);

        OAuthAuthorizationRequestState issued = service.issue(
                OAuthProvider.GOOGLE,
                "/"
        );

        assertEquals(2, store.savedSessions.size());
        assertNotEquals(
                store.savedSessions.get(0).getState(),
                store.savedSessions.get(1).getState()
        );
        assertEquals(
                store.savedSessions.get(1).getState(),
                issued.getState()
        );
    }

    @Test
    void issue_repeatedStateCollision_throwsException() {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        store.rejectSaveCount = 3;
        OAuthStateServiceImpl service = createService(store);

        assertThrows(
                IllegalStateException.class,
                () -> service.issue(OAuthProvider.GOOGLE, "/")
        );
        assertEquals(3, store.savedSessions.size());
    }

    @Test
    void issue_disallowedReturnPath_doesNotStoreState() {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        OAuthStateServiceImpl service = createService(store);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.issue(OAuthProvider.GOOGLE, "/admin")
        );
        assertTrue(store.savedSessions.isEmpty());
    }

    @Test
    void consume_validState_delegatesToStore() {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        OAuthStateServiceImpl service = createService(store);
        OAuthAuthorizationRequestState issued = service.issue(
                OAuthProvider.GOOGLE,
                "/"
        );
        store.consumedSession = Optional.of(store.savedSessions.get(0));

        Optional<OAuthStateSession> consumed = service.consume(
                issued.getState()
        );

        assertTrue(consumed.isPresent());
        assertEquals(issued.getState(), store.consumedState);
    }

    @Test
    void consume_malformedState_doesNotQueryStore() {
        FakeOAuthStateStore store = new FakeOAuthStateStore();
        OAuthStateServiceImpl service = createService(store);

        assertFalse(service.consume("invalid state").isPresent());
        assertNull(store.consumedState);
    }

    @Test
    void constructor_nonPositiveTtl_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthStateServiceImpl(
                        new FakeOAuthStateStore(),
                        new OAuthReturnPathPolicy("/"),
                        0,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new SequenceSecureRandom()
                )
        );
    }

    private OAuthStateServiceImpl createService(
            FakeOAuthStateStore store) {
        return new OAuthStateServiceImpl(
                store,
                new OAuthReturnPathPolicy("/"),
                600,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SequenceSecureRandom()
        );
    }

    private String createCodeChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest);
    }

    private void assertUrlSafeRandomValue(String value) {
        assertEquals(43, value.length());
        assertTrue(value.matches("[A-Za-z0-9_-]+"));
    }

    private static final class FakeOAuthStateStore
            implements OAuthStateStore {
        private final List<OAuthStateSession> savedSessions =
                new ArrayList<>();
        private int rejectSaveCount;
        private String consumedState;
        private Optional<OAuthStateSession> consumedSession =
                Optional.empty();

        @Override
        public boolean saveIfAbsent(OAuthStateSession session) {
            savedSessions.add(session);
            if (rejectSaveCount > 0) {
                rejectSaveCount--;
                return false;
            }
            return true;
        }

        @Override
        public Optional<OAuthStateSession> consume(String state) {
            consumedState = state;
            return consumedSession;
        }
    }

    private static final class SequenceSecureRandom extends SecureRandom {
        private int nextValue;

        @Override
        public void nextBytes(byte[] bytes) {
            byte value = (byte) nextValue++;
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = value;
            }
        }
    }
}
