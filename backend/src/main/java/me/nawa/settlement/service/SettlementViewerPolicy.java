package me.nawa.settlement.service;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.settlement.domain.SettlementAllowedAction;
import me.nawa.settlement.domain.SettlementViewerContext;
import me.nawa.settlement.dto.response.SettlementViewerResponse;
import org.springframework.stereotype.Component;

/** 현재 사용자에게 노출할 금액과 변경 동작을 정산 상태에서 계산한다. */
@Component
public class SettlementViewerPolicy {

    public SettlementViewerResponse resolve(SettlementViewerContext context) {
        List<SettlementAllowedAction> actions = allowedActions(context);
        BigDecimal payableAmount = actions.contains(SettlementAllowedAction.PAY)
            ? context.getShareAmount()
            : BigDecimal.ZERO;
        return SettlementViewerResponse.builder()
            .role(context.getRole())
            .shareAmount(context.getShareAmount())
            .payableAmount(payableAmount)
            .requestStatus(defaultRequestStatus(context.getRequestStatus()))
            .allowedActions(List.copyOf(actions))
            .build();
    }

    private List<SettlementAllowedAction> allowedActions(SettlementViewerContext context) {
        if ("PARTICIPANT".equals(context.getRole())
            && "REQUESTED".equals(context.getSettlementStatus())
            && "PENDING".equals(context.getRequestStatus())
            && context.getShareAmount() != null
            && context.getShareAmount().signum() > 0) {
            return List.of(SettlementAllowedAction.PAY);
        }
        return List.of();
    }

    private String defaultRequestStatus(String requestStatus) {
        return requestStatus == null ? "NOT_REQUESTED" : requestStatus;
    }
}
