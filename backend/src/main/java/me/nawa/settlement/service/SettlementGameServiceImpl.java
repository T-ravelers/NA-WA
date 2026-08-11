package me.nawa.settlement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementGame;
import me.nawa.settlement.domain.SettlementGameMember;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.response.SettlementGameResponse;
import me.nawa.settlement.dto.response.SettlementGameResultResponse;
import me.nawa.settlement.dto.response.SettlementParticipantResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게임 정산 화면의 동의, 시작, 진행 상태와 결과 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class SettlementGameServiceImpl implements SettlementGameService {
    private final SettlementMapper settlementMapper;

    @Override @Transactional
    public void submitGameConsent(Long memberId, Long settlementId, GameConsentRequest request) {
        if (request == null || !("AGREED".equals(request.getStatus()) || "DECLINED".equals(request.getStatus())))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        SettlementGame game = settlementMapper.findSettlementGameForUpdate(settlementId);
        if (game == null || !"WAITING_CONSENT".equals(game.getGameStatus())
            || settlementMapper.updateGameConsent(settlementId, memberId, request.getStatus()) != 1)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
    }

    @Override @Transactional
    public void startGame(Long memberId, Long settlementId) {
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        SettlementGame game = settlementMapper.findSettlementGameForUpdate(settlementId);
        if (settlement == null || game == null || !memberId.equals(settlement.getCreatedByMemberId())
            || !"DRAFT".equals(settlement.getSettlementStatus()) || !"WAITING_CONSENT".equals(game.getGameStatus()))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        List<SettlementGameMember> members = settlementMapper.findGameMembersForUpdate(settlementId);
        if (members.stream().anyMatch(member -> !"AGREED".equals(member.getConsentStatus())))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        List<SettlementGameMember> candidates = new ArrayList<>(members.stream()
            .filter(member -> !settlement.getPayerMemberId().equals(member.getMemberId())).toList());
        if (game.getLiableCount() > candidates.size()) throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        String seed = UUID.randomUUID().toString(); Collections.shuffle(candidates, new java.util.Random(seed.hashCode()));
        List<SettlementGameMember> liable = candidates.subList(0, game.getLiableCount());
        BigDecimal amount = settlement.getTotalAmount().divide(BigDecimal.valueOf(game.getLiableCount()), 2, java.math.RoundingMode.DOWN);
        BigDecimal receivable = amount.multiply(BigDecimal.valueOf(game.getLiableCount()));
        BigDecimal payerShare = settlement.getTotalAmount().subtract(receivable);
        settlementMapper.assignGameLiables(settlementId, liable.stream().map(SettlementGameMember::getAppointmentMemberId).toList());
        List<SettlementMember> payments = members.stream().map(member -> {
            boolean isLiable = liable.stream().anyMatch(selected -> selected.getMemberId().equals(member.getMemberId()));
            boolean isPayer = settlement.getPayerMemberId().equals(member.getMemberId());
            return new SettlementMember(null, settlementId, member.getAppointmentMemberId(), member.getMemberId(),
                isLiable ? amount : (isPayer ? payerShare : BigDecimal.ZERO), isLiable ? "PENDING" : "NOT_REQUESTED", null);
        }).toList();
        settlementMapper.insertSettlementMembers(payments);
        settlementMapper.activateGameSettlement(settlementId, payerShare, receivable);
        settlementMapper.completeGame(settlementId, seed);
    }

    @Override @Transactional(readOnly = true)
    public SettlementGameResponse getGame(Long memberId, Long settlementId) {
        Settlement settlement = settlementMapper.findById(settlementId);
        SettlementGame game = settlementMapper.findSettlementGame(settlementId);
        List<SettlementGameMember> members = settlementMapper.findGameMembers(settlementId);
        requireParticipant(memberId, settlement, game, members);
        Map<Long, String> names = participantNames(settlement);
        List<SettlementParticipantResponse> participants = members.stream().map(member -> participant(member, names)).toList();
        List<SettlementParticipantResponse> liable = members.stream().filter(member -> Boolean.TRUE.equals(member.getLiable()))
            .map(member -> participant(member, names)).toList();
        String declinedBy = members.stream().filter(member -> "DECLINED".equals(member.getConsentStatus())).map(member -> names.get(member.getMemberId()))
            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return SettlementGameResponse.builder().id(settlementId).gameType(game.getGameType()).amount(settlement.getTotalAmount())
            .liableCount(game.getLiableCount()).participants(participants).agreementCount((int) members.stream()
                .filter(member -> "AGREED".equals(member.getConsentStatus())).count()).lifecycle(game.getGameStatus())
            .viewerRole(memberId.equals(settlement.getCreatedByMemberId()) ? "CREATOR" : "PARTICIPANT").declinedBy(declinedBy)
            .journeyName(null).merchantName(null).originalPayer(names.get(settlement.getPayerMemberId())).liableParticipants(liable)
            .transactionId(null).currentParticipantName(names.get(memberId)).build();
    }

    @Override @Transactional(readOnly = true)
    public SettlementGameResultResponse getGameResult(Long memberId, Long settlementId) {
        Settlement settlement = settlementMapper.findById(settlementId);
        SettlementGame game = settlementMapper.findSettlementGame(settlementId);
        List<SettlementGameMember> members = settlementMapper.findGameMembers(settlementId);
        requireParticipant(memberId, settlement, game, members);
        if (!"COMPLETED".equals(game.getGameStatus())) throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        Map<Long, String> names = participantNames(settlement);
        List<SettlementParticipantResponse> liable = members.stream().filter(member -> Boolean.TRUE.equals(member.getLiable()))
            .map(member -> participant(member, names)).toList();
        return SettlementGameResultResponse.builder().settlementId(settlementId)
            .amount(settlement.getTotalAmount().divide(BigDecimal.valueOf(game.getLiableCount()), 2, java.math.RoundingMode.DOWN))
            .liableParticipants(liable).build();
    }

    private void requireParticipant(Long memberId, Settlement settlement, SettlementGame game, List<SettlementGameMember> members) {
        if (settlement == null || game == null || members.stream().noneMatch(member -> memberId.equals(member.getMemberId())))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
    }

    private Map<Long, String> participantNames(Settlement settlement) {
        return settlementMapper.findParticipants(settlement.getAppointmentId()).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementParticipant::getMemberId, SettlementParticipant::getDisplayName));
    }

    private SettlementParticipantResponse participant(SettlementGameMember member, Map<Long, String> names) {
        String name = names.get(member.getMemberId());
        return SettlementParticipantResponse.builder().id(member.getMemberId()).name(name)
            .initials(name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase())
            .consentStatus(member.getConsentStatus()).build();
    }
}
