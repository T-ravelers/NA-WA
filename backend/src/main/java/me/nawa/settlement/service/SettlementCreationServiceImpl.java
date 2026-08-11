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

/** 정산 생성 화면의 공통 검증과 방식별 creator 선택을 담당한다. */
@Service
@RequiredArgsConstructor
public class SettlementCreationServiceImpl implements SettlementCreationService {
    private final SettlementMapper settlementMapper;
    private final List<SettlementCreationHandler> handlers;

    @Override
    @Transactional
    public SettlementCreateResponse createSettlement(Long memberId, CreateSettlementRequest request) {
        if (request == null || request.getSourceTransferId() == null || request.getParticipantIds() == null
            || request.getType() == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        SettlementSource source = settlementMapper.findSourceForCreate(request.getSourceTransferId(), memberId);
        if (source == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        }
        SettlementCreationHandler handler = handlers.stream()
            .filter(candidate -> candidate.getType().equals(request.getType()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID));
        return handler.create(memberId, request, source);
    }
}
