package me.nawa.auth.refresh;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {
    void save(RefreshTokenSession session);

    Optional<RefreshTokenSession> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
