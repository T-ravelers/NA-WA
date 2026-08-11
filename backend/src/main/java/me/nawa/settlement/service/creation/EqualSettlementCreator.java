package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.stereotype.Component;

/** 균등 정산의 금액 계산과 참여자별 부담금 생성을 담당한다. */
@Component
@RequiredArgsConstructor
public class EqualSettlementCreator implements SettlementCreationHandler {
    private final SettlementMapper settlementMapper;

    @Override
    public String getType() { return "EQUAL"; }

    @Override
    public SettlementCreateResponse create(Long memberId, CreateSettlementRequest request, SettlementSource source) {
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId());
        Set<Long> requested = Set.copyOf(request.getParticipantIds());
        if (requested.size() != members.size() || !members.stream().map(SettlementMember::getMemberId)
            .collect(java.util.stream.Collectors.toSet()).equals(requested))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        BigDecimal unit = source.getAmount().divide(BigDecimal.valueOf(members.size()), 2, java.math.RoundingMode.DOWN);
        BigDecimal payerShare = source.getAmount().subtract(unit.multiply(BigDecimal.valueOf(members.size() - 1)));
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId()).settlementStatus("REQUESTED")
            .splitMethod(getType()).totalAmount(source.getAmount()).payerShareAmount(payerShare)
            .receivableAmount(source.getAmount().subtract(payerShare)).requestedAt(LocalDateTime.now()).build();
        settlementMapper.insertSettlement(settlement);
        members.forEach(member -> {
            boolean payer = memberId.equals(member.getMemberId());
            member.setSettlementId(settlement.getSettlementId()); member.setShareAmount(payer ? payerShare : unit);
            member.setRequestStatus(payer ? "NOT_REQUESTED" : "PENDING");
        });
        settlementMapper.insertSettlementMembers(members);
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }
}
