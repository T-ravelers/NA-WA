package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Component;

/** 균등 정산의 금액 계산과 참여자별 부담금 생성을 담당한다. */
@Component
@RequiredArgsConstructor
public class EqualSettlementCreator implements SettlementCreationHandler {
    private final SettlementMapper settlementMapper;

    @Override
    public String getType() { return "EQUAL"; }

    @Override
    public SettlementCreateResponse create(
        Long memberId,
        CreateSettlementRequest request,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint
    ) {
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId());
        Set<Long> requested = Set.copyOf(request.getParticipantAppointmentMemberIds());
        if (requested.size() != members.size() || !members.stream().map(SettlementMember::getAppointmentMemberId)
            .collect(java.util.stream.Collectors.toSet()).equals(requested))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        if (source.getCurrencyDecimalPlaces() == null || source.getCurrencyDecimalPlaces() < 0) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        members = members.stream()
            .sorted(Comparator.comparing(SettlementMember::getAppointmentMemberId))
            .toList();
        int scale = source.getCurrencyDecimalPlaces();
        BigDecimal unit = source.getAmount().divide(
            BigDecimal.valueOf(members.size()),
            scale,
            java.math.RoundingMode.DOWN
        );
        BigDecimal minimumUnit = BigDecimal.ONE.movePointLeft(scale);
        int remainderUnits;
        try {
            remainderUnits = source.getAmount()
                .subtract(unit.multiply(BigDecimal.valueOf(members.size())))
                .movePointRight(scale)
                .intValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID, exception);
        }
        for (int index = 0; index < members.size(); index++) {
            SettlementMember member = members.get(index);
            member.setShareAmount(index < remainderUnits ? unit.add(minimumUnit) : unit);
        }
        BigDecimal payerShare = members.stream()
            .filter(member -> source.getPayerMemberId().equals(member.getMemberId()))
            .map(SettlementMember::getShareAmount)
            .findFirst()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID));
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId())
            .idempotencyKey(idempotencyKey).requestFingerprint(requestFingerprint).settlementStatus("DRAFT")
            .splitMethod(getType()).totalAmount(source.getAmount()).payerShareAmount(payerShare)
            .receivableAmount(source.getAmount().subtract(payerShare)).requestedAt(null).build();
        settlementMapper.insertSettlement(settlement);
        members.forEach(member -> {
            member.setSettlementId(settlement.getSettlementId());
            member.setRequestStatus("NOT_REQUESTED");
        });
        settlementMapper.insertSettlementMembers(members);
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }
}
