package me.nawa.deposit.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.deposit.domain.AllocationType;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.domain.DepositPayout;
import me.nawa.deposit.domain.DepositPayoutBatch;
import me.nawa.deposit.mapper.DepositMapper;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import me.nawa.deposit.mapper.DepositPayoutMapper;
import me.nawa.settlement.service.SettlementAmountAllocator;
import me.nawa.wallet.domain.SystemWalletCode;
import me.nawa.wallet.domain.enums.TransferType;
import me.nawa.wallet.service.WalletTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보증금 정산 배치를 실제 지갑 이체로 처리한다.
 *
 * {@code confirmAttendance}가 `PENDING`으로 남겨둔 배치를 가져와, 출석한 회원에게는
 * 본인 보증금을 환급(`SELF_REFUND`)하고 노쇼 회원의 보증금은 출석 회원에게 균등
 * 분배(`NO_SHOW_SHARE`)한다. 분배 규칙은 SETTLEMENT.md의 EQUAL 분담과 같은
 * {@link SettlementAmountAllocator}를 그대로 재사용한다(참가 ID 오름차순, 최소
 * 단위 나머지 배분).
 *
 * 각 지급 건은 이체 전에 {@code DepositPayoutMapper.countByAllocation}으로 이미
 * 처리됐는지 확인한다 — 중간에 실패해 다음 tick이 같은 배치를 재처리하더라도
 * 이미 끝난 지급은 다시 이체하지 않는다(16절).
 */
@Service
@RequiredArgsConstructor
public class DepositPayoutBatchProcessor {

    private final DepositPayoutBatchMapper depositPayoutBatchMapper;
    private final DepositMapper depositMapper;
    private final DepositPayoutMapper depositPayoutMapper;
    private final AppointmentMapper appointmentMapper;
    private final WalletTransferService walletTransferService;
    private final SettlementAmountAllocator amountAllocator;

    @Transactional
    public void processBatch(Long depositPayoutBatchId) {
        DepositPayoutBatch batch = depositPayoutBatchMapper
                .findByIdForUpdate(depositPayoutBatchId);
        if (batch == null || !(batch.isPending() || batch.isFailed())) {
            // 다른 tick이 이미 가져가 처리했거나 끝냈다 — 중복 실행 방지.
            return;
        }
        batch.startProcessing();
        if (depositPayoutBatchMapper.markProcessing(depositPayoutBatchId) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        List<AppointmentMember> activeMembers = appointmentMapper
                .findActiveMembersByAppointmentId(batch.getAppointmentId());
        List<AppointmentMember> attended = activeMembers.stream()
                .filter(member -> member.getAttendanceStatus() == AttendanceStatus.ATTENDED)
                .sorted(Comparator.comparing(AppointmentMember::getAppointmentMemberId))
                .toList();
        List<AppointmentMember> noShow = activeMembers.stream()
                .filter(member -> member.getAttendanceStatus() == AttendanceStatus.NO_SHOW)
                .toList();

        if (attended.isEmpty()) {
            // confirmAttendance가 출석자 0명인 요청은 이미 거부하므로, 여기 도달하면
            // 데이터 정합성이 깨진 것이다. 재시도해도 다시 성립하지 않으므로 그대로
            // 실패로 던져 markBatchFailed가 잡게 한다.
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal totalRefunded = payoutSelfRefunds(batch, attended, now);
        BigDecimal[] noShowTotals = payoutNoShowShares(batch, attended, noShow, now);

        batch.complete(totalRefunded, noShowTotals[0], noShowTotals[1], null, now);
        if (depositPayoutBatchMapper.markCompleted(batch) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 실패 표시는 실패를 일으킨 트랜잭션과 별도 트랜잭션으로 커밋해야 한다.
    // 같은 트랜잭션이면 롤백될 때 FAILED 반영 자체도 함께 사라진다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markBatchFailed(Long depositPayoutBatchId) {
        depositPayoutBatchMapper.markFailed(depositPayoutBatchId);
    }

    private BigDecimal payoutSelfRefunds(
            DepositPayoutBatch batch,
            List<AppointmentMember> attended,
            LocalDateTime now
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (AppointmentMember member : attended) {
            Deposit deposit = requireDeposit(member.getAppointmentMemberId());
            if (deposit.isHeld()) {
                long transferId = walletTransferService.transferFromSystemWallet(
                        null,
                        SystemWalletCode.DEPOSIT_POOL,
                        member.getMemberId(),
                        deposit.getAmount(),
                        TransferType.DEPOSIT_REFUND.name(),
                        "출석 확정 보증금 환급"
                );
                depositPayoutMapper.insert(DepositPayout.selfRefund(
                        deposit.getDepositId(),
                        member.getAppointmentMemberId(),
                        member.getAppointmentMemberId(),
                        transferId,
                        batch.getDepositPayoutBatchId(),
                        deposit.getAmount()
                ));
                deposit.refund(now);
                if (depositMapper.markRefunded(deposit.getDepositId(), now) != 1) {
                    throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
            total = total.add(deposit.getAmount());
        }
        return total;
    }

    private BigDecimal[] payoutNoShowShares(
            DepositPayoutBatch batch,
            List<AppointmentMember> attended,
            List<AppointmentMember> noShow,
            LocalDateTime now
    ) {
        BigDecimal totalNoShow = BigDecimal.ZERO;
        BigDecimal totalDistributed = BigDecimal.ZERO;
        Map<Long, AppointmentMember> attendedByAppointmentMemberId = attended.stream()
                .collect(Collectors.toMap(
                        AppointmentMember::getAppointmentMemberId, member -> member
                ));
        List<Long> attendedAppointmentMemberIds =
                List.copyOf(attendedByAppointmentMemberId.keySet());

        for (AppointmentMember member : noShow) {
            Deposit deposit = requireDeposit(member.getAppointmentMemberId());
            totalNoShow = totalNoShow.add(deposit.getAmount());
            if (deposit.isHeld()) {
                Map<Long, BigDecimal> shares = amountAllocator.allocate(
                        deposit.getAmount(), attendedAppointmentMemberIds, 0
                );
                for (Map.Entry<Long, BigDecimal> share : shares.entrySet()) {
                    payoutNoShowShare(
                            batch, deposit, member,
                            attendedByAppointmentMemberId.get(share.getKey()),
                            share.getValue()
                    );
                }
                deposit.distribute(now);
                if (depositMapper.markDistributed(deposit.getDepositId(), now) != 1) {
                    throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
            totalDistributed = totalDistributed.add(deposit.getAmount());
        }
        return new BigDecimal[] {totalNoShow, totalDistributed};
    }

    private void payoutNoShowShare(
            DepositPayoutBatch batch,
            Deposit noShowDeposit,
            AppointmentMember noShowMember,
            AppointmentMember recipient,
            BigDecimal shareAmount
    ) {
        if (shareAmount.signum() <= 0) {
            return;
        }
        if (depositPayoutMapper.countByAllocation(
                noShowDeposit.getDepositId(),
                recipient.getAppointmentMemberId(),
                AllocationType.NO_SHOW_SHARE
        ) > 0) {
            return;
        }
        long transferId = walletTransferService.transferFromSystemWallet(
                null,
                SystemWalletCode.DEPOSIT_POOL,
                recipient.getMemberId(),
                shareAmount,
                TransferType.DEPOSIT_NO_SHOW_DISTRIBUTION.name(),
                "노쇼 보증금 분배"
        );
        depositPayoutMapper.insert(DepositPayout.noShowShare(
                noShowDeposit.getDepositId(),
                noShowMember.getAppointmentMemberId(),
                recipient.getAppointmentMemberId(),
                transferId,
                batch.getDepositPayoutBatchId(),
                shareAmount
        ));
    }

    private Deposit requireDeposit(Long appointmentMemberId) {
        Deposit deposit = depositMapper.findByAppointmentMemberId(appointmentMemberId);
        if (deposit == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        return deposit;
    }
}
