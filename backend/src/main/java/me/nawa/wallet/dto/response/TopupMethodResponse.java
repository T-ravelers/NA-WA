package me.nawa.wallet.dto.response;

public record TopupMethodResponse(
    String type,
    String displayName,
    boolean testMode,
    boolean enabled
) {
}
