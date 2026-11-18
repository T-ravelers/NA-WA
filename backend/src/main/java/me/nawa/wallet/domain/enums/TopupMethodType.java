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
        return toResponse(true);
    }

    // 충전 기능 전체가 꺼진 환경(payment.enabled=false)에서는 수단 자체가 켜져 있어도 쓸 수 없다.
    // 수단을 목록에서 빼지 않고 enabled=false로 내려 줘야 화면이 "준비 중"을 판단할 수 있다.
    public TopupMethodResponse toResponse(boolean paymentEnabled){
        return new TopupMethodResponse(name(), displayName, testMode, enabled && paymentEnabled);
    }
}
