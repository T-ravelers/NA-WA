package me.nawa.settlement.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemAllocationRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 정산 생성의 공통 검증, 멱등성 확인과 방식별 creator 선택을 담당한다. */
@Service
@RequiredArgsConstructor
public class SettlementCreationServiceImpl implements SettlementCreationService {
    private final SettlementMapper settlementMapper;
    private final SettlementCreationAttemptService creationAttemptService;

    @Override
    public SettlementCreateResponse createSettlement(
        Long memberId,
        Long appointmentId,
        String idempotencyKey,
        CreateSettlementRequest request
    ) {
        validateRequest(appointmentId, idempotencyKey, request);
        String normalizedIdempotencyKey = idempotencyKey.trim();
        String requestFingerprint = fingerprint(appointmentId, request);
        Settlement existing = settlementMapper.findByCreatorAndIdempotencyKey(
            memberId, normalizedIdempotencyKey
        );
        if (existing != null) {
            return resolveIdempotentRetry(existing, requestFingerprint);
        }
        try {
            return creationAttemptService.create(
                memberId,
                appointmentId,
                normalizedIdempotencyKey,
                requestFingerprint,
                request
            );
        } catch (DuplicateKeyException exception) {
            Settlement concurrent = settlementMapper.findByCreatorAndIdempotencyKey(
                memberId, normalizedIdempotencyKey
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

    private void validateRequest(
        Long appointmentId,
        String idempotencyKey,
        CreateSettlementRequest request
    ) {
        if (appointmentId == null || idempotencyKey == null || idempotencyKey.isBlank()
            || idempotencyKey.trim().length() > 100 || request == null
            || request.getSourceTransferId() == null
            || request.getParticipantAppointmentMemberIds() == null
            || request.getParticipantAppointmentMemberIds().isEmpty()
            || request.getParticipantAppointmentMemberIds().stream().anyMatch(java.util.Objects::isNull)
            || new HashSet<>(request.getParticipantAppointmentMemberIds()).size()
                != request.getParticipantAppointmentMemberIds().size()
            || !("EQUAL".equals(request.getType()) || "ITEMIZED".equals(request.getType()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        if (("EQUAL".equals(request.getType())
                && request.getItems() != null && !request.getItems().isEmpty())
            || ("ITEMIZED".equals(request.getType())
                && (request.getItems() == null || request.getItems().isEmpty()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
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

    private String fingerprint(Long appointmentId, CreateSettlementRequest request) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, appointmentId);
        append(canonical, request.getSourceTransferId());
        append(canonical, request.getType());
        // 영수증만 다른 두 요청이 같은 지문으로 뭉뚱그려지지 않도록 함께 넣는다.
        append(canonical, request.getReceiptId());
        request.getParticipantAppointmentMemberIds().stream().sorted().forEach(id -> append(canonical, id));
        if ("ITEMIZED".equals(request.getType())) {
            request.getItems().forEach(item -> appendItem(canonical, item));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void appendItem(StringBuilder canonical, ItemizedSettlementItemRequest item) {
        append(canonical, item == null ? null : item.getName());
        append(canonical, item == null ? null : item.getUnitPrice());
        append(canonical, item == null ? null : item.getQuantity());
        if (item == null || item.getAllocations() == null) {
            append(canonical, null);
            return;
        }
        item.getAllocations().stream()
            .sorted(Comparator.comparing(allocation ->
                allocation == null ? null : allocation.getAppointmentMemberId(),
                Comparator.nullsFirst(Comparator.naturalOrder())
            ))
            .forEach(allocation -> {
                if (allocation == null) {
                    append(canonical, null);
                    append(canonical, null);
                    return;
                }
                append(canonical, allocation.getAppointmentMemberId());
                append(canonical, allocation.getQuantity());
            });
    }

    private void append(StringBuilder canonical, Object value) {
        String text = value == null ? "<null>" : value.toString();
        canonical.append(text.length()).append(':').append(text).append('|');
    }
}
