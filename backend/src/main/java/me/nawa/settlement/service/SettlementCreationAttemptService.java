package me.nawa.settlement.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.SettlementCreationHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 생성 1회를 독립 트랜잭션으로 실행해 고유키 충돌 시 스냅샷을 종료한다. */
@Service
@RequiredArgsConstructor
public class SettlementCreationAttemptService {

    private final SettlementMapper settlementMapper;
    private final List<SettlementCreationHandler> handlers;

    @Transactional
    public SettlementCreateResponse create(
        Long memberId,
        Long appointmentId,
        String idempotencyKey,
        String requestFingerprint,
        CreateSettlementRequest request
    ) {
        if (settlementMapper.findBySourceTransferId(request.getSourceTransferId()) != null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_ALREADY_USED);
        }
        SettlementSource source = settlementMapper.findSourceForCreate(
            request.getSourceTransferId(), memberId
        );
        if (source == null || !appointmentId.equals(source.getAppointmentId())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        }
        SettlementCreationHandler handler = handlers.stream()
            .filter(candidate -> candidate.getType().equals(request.getType()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                SettlementErrorCode.SETTLEMENT_CREATE_INVALID
            ));
        return handler.create(
            memberId, request, source, idempotencyKey, requestFingerprint
        );
    }
}
