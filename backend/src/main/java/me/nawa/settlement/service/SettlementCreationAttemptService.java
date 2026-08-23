package me.nawa.settlement.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.event.SettlementRequestedEvent;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.SettlementCreationHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 생성 1회를 독립 트랜잭션으로 실행해 고유키 충돌 시 스냅샷을 종료한다. */
@Service
@RequiredArgsConstructor
public class SettlementCreationAttemptService {

    private final SettlementMapper settlementMapper;
    private final List<SettlementCreationHandler> handlers;
    private final SettlementReceiptService settlementReceiptService;
    private final ApplicationEventPublisher eventPublisher;

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
        SettlementCreateResponse created = handler.create(
            memberId, request, source, idempotencyKey, requestFingerprint
        );
        // 정산과 영수증이 함께 남거나 함께 없어야 하므로 같은 트랜잭션 안에서 연결한다.
        settlementReceiptService.linkToSettlement(
            memberId, created.getId(), request.getReceiptId()
        );
        // 여기까지 온 요청만 정산을 새로 만든 것이다. 같은 멱등키로 다시 들어와 기존 정산을
        // 그대로 돌려주는 경로는 이 메서드에 닿지 않으므로, 재시도로 알림이 겹치지 않는다.
        eventPublisher.publishEvent(new SettlementRequestedEvent(created.getId()));
        return created;
    }
}
