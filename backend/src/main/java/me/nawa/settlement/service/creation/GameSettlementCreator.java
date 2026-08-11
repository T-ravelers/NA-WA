package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementGame;
import me.nawa.settlement.domain.SettlementGameMember;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.GameCreateRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Component;

/** 전원 동의가 필요한 게임 정산 초안과 참여자 상태를 만든다. */
@Component
@RequiredArgsConstructor
public class GameSettlementCreator implements SettlementCreationHandler {
    private final SettlementMapper settlementMapper;

    @Override
    public String getType() { return "GAME"; }

    @Override
    public SettlementCreateResponse create(
        Long memberId,
        CreateSettlementRequest request,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint
    ) {
        GameCreateRequest gameRequest = request.getGame();
        List<SettlementMember> participants = settlementMapper.findActiveMembers(source.getAppointmentId());
        if (gameRequest == null || gameRequest.getType() == null || gameRequest.getType().isBlank() || gameRequest.getLiableCount() == null
            || gameRequest.getLiableCount() <= 0 || gameRequest.getLiableCount() >= participants.size()
            || !Set.copyOf(request.getParticipantAppointmentMemberIds()).equals(participants.stream()
                .map(SettlementMember::getAppointmentMemberId).collect(java.util.stream.Collectors.toSet())))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId())
            .idempotencyKey(idempotencyKey).requestFingerprint(requestFingerprint).settlementStatus("DRAFT")
            .splitMethod(getType()).totalAmount(source.getAmount()).payerShareAmount(source.getAmount())
            .receivableAmount(BigDecimal.ZERO).requestedAt(null).build();
        settlementMapper.insertSettlement(settlement);
        settlementMapper.insertSettlementGame(new SettlementGame(settlement.getSettlementId(), gameRequest.getType(),
            gameRequest.getLiableCount(), "WAITING_CONSENT", null));
        settlementMapper.insertSettlementGameMembers(participants.stream().map(member -> new SettlementGameMember(settlement.getSettlementId(),
            member.getAppointmentMemberId(), member.getMemberId(), "PENDING", false)).toList());
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }
}
