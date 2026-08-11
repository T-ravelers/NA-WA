package me.nawa.settlement.service.creation;

import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;

/** 정산 방식 하나의 생성 규칙을 처리한다. 호출자는 공통 검증을 마친 원거래만 전달한다. */
public interface SettlementCreationHandler {
    String getType();
    SettlementCreateResponse create(
        Long memberId,
        CreateSettlementRequest request,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint
    );
}
