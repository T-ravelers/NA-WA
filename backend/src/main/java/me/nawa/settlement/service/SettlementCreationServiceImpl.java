package me.nawa.settlement.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.SettlementCreationHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

/** 정산 생성 화면의 공통 검증과 방식별 creator 선택을 담당한다. */
@Service
@RequiredArgsConstructor
public class SettlementCreationServiceImpl implements SettlementCreationService {
    private final SettlementMapper settlementMapper;
    private final List<SettlementCreationHandler> handlers;

    @Override
    @Transactional
    public SettlementCreateResponse createSettlement(
        Long memberId,
        Long appointmentId,
        String idempotencyKey,
        CreateSettlementRequest request
    ) {
        if (appointmentId == null || idempotencyKey == null || idempotencyKey.isBlank()
            || idempotencyKey.length() > 100 || request == null || request.getSourceTransferId() == null
            || request.getParticipantAppointmentMemberIds() == null
            || request.getParticipantAppointmentMemberIds().isEmpty() || request.getType() == null
            || request.getType().isBlank() || request.getParticipantAppointmentMemberIds().stream().anyMatch(java.util.Objects::isNull)
            || new HashSet<>(request.getParticipantAppointmentMemberIds()).size()
                != request.getParticipantAppointmentMemberIds().size()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        String normalizedIdempotencyKey = idempotencyKey.trim();
        String requestFingerprint = fingerprint(appointmentId, request);
        Settlement existing = settlementMapper.findByCreatorAndIdempotencyKey(memberId, normalizedIdempotencyKey);
        if (existing != null) {
            return resolveIdempotentRetry(existing, requestFingerprint);
        }
        if (settlementMapper.findBySourceTransferId(request.getSourceTransferId()) != null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_ALREADY_USED);
        }
        SettlementSource source = settlementMapper.findSourceForCreate(request.getSourceTransferId(), memberId);
        if (source == null || !appointmentId.equals(source.getAppointmentId())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        }
        SettlementCreationHandler handler = handlers.stream()
            .filter(candidate -> candidate.getType().equals(request.getType()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID));
        try {
            return handler.create(
                memberId,
                request,
                source,
                normalizedIdempotencyKey,
                requestFingerprint
            );
        } catch (DuplicateKeyException exception) {
            Settlement concurrent = settlementMapper.findByCreatorAndIdempotencyKey(
                memberId,
                normalizedIdempotencyKey
            );
            if (concurrent != null) {
                return resolveIdempotentRetry(concurrent, requestFingerprint);
            }
            if (settlementMapper.findBySourceTransferId(request.getSourceTransferId()) != null) {
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_ALREADY_USED, exception);
            }
            throw exception;
        }
    }

    private SettlementCreateResponse resolveIdempotentRetry(
        Settlement existing,
        String requestFingerprint
    ) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_IDEMPOTENCY_CONFLICT);
        }
        return SettlementCreateResponse.builder().id(existing.getSettlementId()).build();
    }

    @Override
    @Transactional
    public void requestSettlement(Long memberId, Long settlementId) {
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        if (settlement == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        if (!memberId.equals(settlement.getCreatedByMemberId())
            || !"DRAFT".equals(settlement.getSettlementStatus())
            || settlementMapper.markSettlementRequested(settlementId, memberId) != 1) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_REQUEST_NOT_ALLOWED);
        }
        settlementMapper.markSettlementMembersRequested(settlementId, memberId);
    }

    private String fingerprint(Long appointmentId, CreateSettlementRequest request) {
        String participants = request.getParticipantAppointmentMemberIds().stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        String gameType = request.getGame() == null || request.getGame().getType() == null
            ? "" : request.getGame().getType().trim();
        String liableCount = request.getGame() == null || request.getGame().getLiableCount() == null
            ? "" : request.getGame().getLiableCount().toString();
        String canonical = String.join("|",
            appointmentId.toString(),
            request.getSourceTransferId().toString(),
            request.getType().trim(),
            participants,
            request.getReceiptAnalysisId() == null ? "" : request.getReceiptAnalysisId().toString(),
            gameType,
            liableCount
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
