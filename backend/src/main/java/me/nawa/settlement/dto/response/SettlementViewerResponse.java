package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import me.nawa.settlement.domain.SettlementAllowedAction;

/** 현재 사용자에게 확정된 정산 금액, 상태와 허용 동작이다. */
@Getter
@Builder
public class SettlementViewerResponse {

    private final String role;
    private final BigDecimal shareAmount;
    private final BigDecimal payableAmount;
    private final String requestStatus;
    private final List<SettlementAllowedAction> allowedActions;
}
