package me.nawa.wallet.domain.enums;

import me.nawa.wallet.dto.response.TopupMethodResponse;

public enum TopupMethodType {

    STRIPE_CARD("해외 카드 충전", true, true);

    private final String displayName;
    private final boolean testMode;
    private final boolean enabled;

    TopupMethodType(String displayName, boolean testMode, boolean enabled){
        this.displayName = displayName;
        this.testMode = testMode;
        this.enabled = enabled;
    }

    public boolean isEnabled(){
        return enabled;
    }

    public TopupMethodResponse toResponse(){
        return new TopupMethodResponse(name(), displayName, testMode, enabled);
    }
}
