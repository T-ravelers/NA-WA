package me.nawa.auth.oauth.state;

import java.util.Optional;

public interface OAuthStateStore {
    boolean saveIfAbsent(OAuthStateSession session);

    Optional<OAuthStateSession> consume(String state);
}
