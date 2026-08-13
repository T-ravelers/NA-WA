package me.nawa.settlement.service;

import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;

/** 균등 또는 항목별 정산을 생성하는 작업을 정의한다. */
public interface SettlementCreationService {
    SettlementCreateResponse createSettlement(
        Long memberId,
        Long appointmentId,
        String idempotencyKey,
        CreateSettlementRequest request
    );
}
