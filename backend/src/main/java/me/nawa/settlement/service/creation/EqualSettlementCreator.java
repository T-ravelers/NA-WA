package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.SettlementAmountAllocator;
import org.springframework.stereotype.Component;

/** 균등 정산을 통화 최소 단위까지 결정적으로 배분해 즉시 요청 상태로 생성한다. */
@Component
@RequiredArgsConstructor
public class EqualSettlementCreator implements SettlementCreationHandler {
    private final SettlementMapper settlementMapper;
    private final SettlementAmountAllocator amountAllocator;

    @Override
    public String getType() {
        return "EQUAL";
    }

    @Override
    public SettlementCreateResponse create(
        Long memberId,
        CreateSettlementRequest request,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint
    ) {
        List<SettlementMember> members = selectedActiveMembers(request, source);
        Map<Long, BigDecimal> allocations = amountAllocator.allocate(
            source.getAmount(),
            members.stream().map(SettlementMember::getAppointmentMemberId).toList(),
            source.getCurrencyDecimalPlaces()
        );
        members.forEach(member -> member.setShareAmount(
            allocations.get(member.getAppointmentMemberId())
        ));
        validatePayerAndPendingAmounts(members, source.getPayerMemberId());

        BigDecimal payerShare = members.stream()
            .filter(member -> source.getPayerMemberId().equals(member.getMemberId()))
            .map(SettlementMember::getShareAmount)
            .findFirst()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID));
        Settlement settlement = newSettlement(
            memberId, source, idempotencyKey, requestFingerprint, payerShare
        );
        settlementMapper.insertSettlement(settlement);
        members.forEach(member -> {
            member.setSettlementId(settlement.getSettlementId());
            member.setRequestStatus(source.getPayerMemberId().equals(member.getMemberId())
                ? "NOT_REQUESTED" : "PENDING");
        });
        settlementMapper.insertSettlementMembers(members);
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }

    private List<SettlementMember> selectedActiveMembers(
        CreateSettlementRequest request,
        SettlementSource source
    ) {
        if (source.getCurrencyDecimalPlaces() == null || source.getCurrencyDecimalPlaces() < 0) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        Set<Long> requested = Set.copyOf(request.getParticipantAppointmentMemberIds());
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId()).stream()
            .filter(member -> requested.contains(member.getAppointmentMemberId()))
            .sorted(Comparator.comparing(SettlementMember::getAppointmentMemberId))
            .toList();
        if (members.size() != requested.size()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        return members;
    }

    static void validatePayerAndPendingAmounts(
        List<SettlementMember> members,
        Long payerMemberId
    ) {
        if (members.stream().noneMatch(member -> payerMemberId.equals(member.getMemberId()))
            || members.stream().anyMatch(member -> !payerMemberId.equals(member.getMemberId())
                && (member.getShareAmount() == null || member.getShareAmount().signum() <= 0))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
    }

    static Settlement newSettlement(
        Long memberId,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint,
        BigDecimal payerShare
    ) {
        return Settlement.builder()
            .appointmentId(source.getAppointmentId())
            .createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId())
            .sourceTransferId(source.getTransferId())
            .idempotencyKey(idempotencyKey)
            .requestFingerprint(requestFingerprint)
            .settlementStatus("REQUESTED")
            .splitMethod("EQUAL")
            .totalAmount(source.getAmount())
            .payerShareAmount(payerShare)
            .receivableAmount(source.getAmount().subtract(payerShare))
            .build();
    }
}
