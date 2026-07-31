package me.nawa.auth.refresh;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {
    void save(RefreshTokenSession session);

    Optional<RefreshTokenSession> findBySessionId(UUID sessionId);

    RefreshTokenRotationResult rotate(
            UUID sessionId,
            String currentTokenHash,
            RefreshTokenSession replacementSession);

    void deleteBySessionId(UUID sessionId);
}
