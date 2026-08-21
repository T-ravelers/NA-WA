package me.nawa.deposit.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.deposit.domain.AllocationType;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.domain.DepositPayoutBatch;
import me.nawa.deposit.mapper.DepositMapper;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import me.nawa.deposit.mapper.DepositPayoutMapper;
import me.nawa.settlement.service.SettlementAmountAllocator;
import me.nawa.wallet.domain.SystemWalletCode;
import me.nawa.wallet.domain.enums.TransferType;
import me.nawa.wallet.service.WalletTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositPayoutBatchProcessorTest {

    @Mock
    private DepositPayoutBatchMapper depositPayoutBatchMapper;
    @Mock
    private DepositMapper depositMapper;
    @Mock
    private DepositPayoutMapper depositPayoutMapper;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private WalletTransferService walletTransferService;
    @Mock
    private SettlementAmountAllocator amountAllocator;
    @InjectMocks
    private DepositPayoutBatchProcessor processor;

    @Test
    void processBatch_refundsAttendeeAndDistributesNoShowDeposit() {
        DepositPayoutBatch batch = mock(DepositPayoutBatch.class);
        when(batch.isPending()).thenReturn(true);
        when(batch.getAppointmentId()).thenReturn(10L);
        when(batch.getDepositPayoutBatchId()).thenReturn(100L);
        when(depositPayoutBatchMapper.findByIdForUpdate(100L)).thenReturn(batch);
        when(depositPayoutBatchMapper.markProcessing(100L)).thenReturn(1);
        when(depositPayoutBatchMapper.markCompleted(batch)).thenReturn(1);

        AppointmentMember attendee = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .memberId(1L)
                .attendanceStatus(AttendanceStatus.ATTENDED)
                .build();
        AppointmentMember noShow = AppointmentMember.builder()
                .appointmentMemberId(21L)
                .memberId(2L)
                .attendanceStatus(AttendanceStatus.NO_SHOW)
                .build();
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(attendee, noShow));

        Deposit attendeeDeposit = mock(Deposit.class);
        when(attendeeDeposit.isHeld()).thenReturn(true);
        when(attendeeDeposit.getDepositId()).thenReturn(200L);
        when(attendeeDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        Deposit noShowDeposit = mock(Deposit.class);
        when(noShowDeposit.isHeld()).thenReturn(true);
        when(noShowDeposit.getDepositId()).thenReturn(201L);
        when(noShowDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(depositMapper.findByAppointmentMemberId(20L)).thenReturn(attendeeDeposit);
        when(depositMapper.findByAppointmentMemberId(21L)).thenReturn(noShowDeposit);
        when(depositMapper.markRefunded(eq(200L), any())).thenReturn(1);
        when(depositMapper.markDistributed(eq(201L), any())).thenReturn(1);

        when(amountAllocator.allocate(BigDecimal.valueOf(10_000), List.of(20L), 0))
                .thenReturn(Map.of(20L, BigDecimal.valueOf(10_000)));

        when(walletTransferService.transferFromSystemWallet(
                isNull(), eq(SystemWalletCode.DEPOSIT_POOL), eq(1L),
                eq(BigDecimal.valueOf(10_000)), eq(TransferType.DEPOSIT_REFUND.name()),
                anyString()
        )).thenReturn(500L);
        when(walletTransferService.transferFromSystemWallet(
                isNull(), eq(SystemWalletCode.DEPOSIT_POOL), eq(1L),
                eq(BigDecimal.valueOf(10_000)), eq(TransferType.DEPOSIT_NO_SHOW_DISTRIBUTION.name()),
                anyString()
        )).thenReturn(501L);

        processor.processBatch(100L);

        verify(batch).startProcessing();
        verify(depositPayoutBatchMapper).markProcessing(100L);
        verify(attendeeDeposit).refund(any());
        verify(noShowDeposit).distribute(any());
        verify(depositPayoutMapper).insert(argThat(
                payout -> payout.isSelfRefund() && payout.getAmount()
                        .compareTo(BigDecimal.valueOf(10_000)) == 0
        ));
        verify(depositPayoutMapper).insert(argThat(
                payout -> payout.isNoShowShare() && payout.getAmount()
                        .compareTo(BigDecimal.valueOf(10_000)) == 0
        ));
        verify(batch).complete(
                eq(BigDecimal.valueOf(10_000)),
                eq(BigDecimal.valueOf(10_000)),
                eq(BigDecimal.valueOf(10_000)),
                isNull(),
                any()
        );
        verify(depositPayoutBatchMapper).markCompleted(batch);
    }

    // 활동 중에 나가 노쇼로 굳은 LEFT 회원은 ACTIVE 목록에 없다. 그 보증금도
    // 출석 회원에게 분배돼야 한다 — 빠지면 HELD인 채 DEPOSIT_POOL에 영영 남는다.
    @Test
    void processBatch_leftNoShowDeposit_distributedToAttendees() {
        DepositPayoutBatch batch = mock(DepositPayoutBatch.class);
        when(batch.isPending()).thenReturn(true);
        when(batch.getAppointmentId()).thenReturn(10L);
        when(batch.getDepositPayoutBatchId()).thenReturn(100L);
        when(depositPayoutBatchMapper.findByIdForUpdate(100L)).thenReturn(batch);
        when(depositPayoutBatchMapper.markProcessing(100L)).thenReturn(1);
        when(depositPayoutBatchMapper.markCompleted(batch)).thenReturn(1);

        AppointmentMember attendee = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .memberId(1L)
                .attendanceStatus(AttendanceStatus.ATTENDED)
                .build();
        AppointmentMember leftNoShow = AppointmentMember.builder()
                .appointmentMemberId(22L)
                .memberId(3L)
                .attendanceStatus(AttendanceStatus.NO_SHOW)
                .build();
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(attendee));
        when(appointmentMapper.findLeftNoShowMembersByAppointmentId(10L))
                .thenReturn(List.of(leftNoShow));

        Deposit attendeeDeposit = mock(Deposit.class);
        when(attendeeDeposit.isHeld()).thenReturn(true);
        when(attendeeDeposit.getDepositId()).thenReturn(200L);
        when(attendeeDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        Deposit leftDeposit = mock(Deposit.class);
        when(leftDeposit.isHeld()).thenReturn(true);
        when(leftDeposit.getDepositId()).thenReturn(202L);
        when(leftDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(depositMapper.findByAppointmentMemberId(20L)).thenReturn(attendeeDeposit);
        when(depositMapper.findByAppointmentMemberId(22L)).thenReturn(leftDeposit);
        when(depositMapper.markRefunded(eq(200L), any())).thenReturn(1);
        when(depositMapper.markDistributed(eq(202L), any())).thenReturn(1);

        when(amountAllocator.allocate(BigDecimal.valueOf(10_000), List.of(20L), 0))
                .thenReturn(Map.of(20L, BigDecimal.valueOf(10_000)));

        when(walletTransferService.transferFromSystemWallet(
                isNull(), eq(SystemWalletCode.DEPOSIT_POOL), eq(1L),
                eq(BigDecimal.valueOf(10_000)), eq(TransferType.DEPOSIT_REFUND.name()),
                anyString()
        )).thenReturn(500L);
        when(walletTransferService.transferFromSystemWallet(
                isNull(), eq(SystemWalletCode.DEPOSIT_POOL), eq(1L),
                eq(BigDecimal.valueOf(10_000)), eq(TransferType.DEPOSIT_NO_SHOW_DISTRIBUTION.name()),
                anyString()
        )).thenReturn(501L);

        processor.processBatch(100L);

        verify(leftDeposit).distribute(any());
        verify(depositMapper).markDistributed(eq(202L), any());
        verify(depositPayoutMapper).insert(argThat(
                payout -> payout.isNoShowShare() && payout.getAmount()
                        .compareTo(BigDecimal.valueOf(10_000)) == 0
        ));
        verify(depositPayoutBatchMapper).markCompleted(batch);
    }

    @Test
    void processBatch_alreadyCompleted_doesNothing() {
        DepositPayoutBatch batch = mock(DepositPayoutBatch.class);
        when(batch.isPending()).thenReturn(false);
        when(batch.isFailed()).thenReturn(false);
        when(depositPayoutBatchMapper.findByIdForUpdate(100L)).thenReturn(batch);

        processor.processBatch(100L);

        verify(depositPayoutBatchMapper, never()).markProcessing(anyLong());
        verify(appointmentMapper, never()).findActiveMembersByAppointmentId(any());
    }

    @Test
    void processBatch_retryAlreadyPaidMember_skipsDuplicateTransfer() {
        DepositPayoutBatch batch = mock(DepositPayoutBatch.class);
        when(batch.isFailed()).thenReturn(true);
        when(batch.getAppointmentId()).thenReturn(10L);
        when(depositPayoutBatchMapper.findByIdForUpdate(100L)).thenReturn(batch);
        when(depositPayoutBatchMapper.markProcessing(100L)).thenReturn(1);
        when(depositPayoutBatchMapper.markCompleted(batch)).thenReturn(1);

        AppointmentMember attendee = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .memberId(1L)
                .attendanceStatus(AttendanceStatus.ATTENDED)
                .build();
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(attendee));

        // 이전 tick에서 이미 환급까지 끝나 REFUNDED 상태인 경우를 재현한다.
        Deposit attendeeDeposit = mock(Deposit.class);
        when(attendeeDeposit.isHeld()).thenReturn(false);
        when(attendeeDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(depositMapper.findByAppointmentMemberId(20L)).thenReturn(attendeeDeposit);

        processor.processBatch(100L);

        verify(walletTransferService, never()).transferFromSystemWallet(
                any(), anyString(), anyLong(), any(), anyString(), anyString()
        );
        verify(depositPayoutMapper, never()).insert(any());
        verify(batch).complete(
                eq(BigDecimal.valueOf(10_000)), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO),
                isNull(), any()
        );
    }

    @Test
    void processBatch_noAttendedMembers_throwsAndLeavesForFailureHandling() {
        DepositPayoutBatch batch = mock(DepositPayoutBatch.class);
        when(batch.isPending()).thenReturn(true);
        when(batch.getAppointmentId()).thenReturn(10L);
        when(depositPayoutBatchMapper.findByIdForUpdate(100L)).thenReturn(batch);
        when(depositPayoutBatchMapper.markProcessing(100L)).thenReturn(1);
        AppointmentMember noShow = AppointmentMember.builder()
                .appointmentMemberId(21L)
                .memberId(2L)
                .attendanceStatus(AttendanceStatus.NO_SHOW)
                .build();
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(noShow));

        assertThrows(BusinessException.class, () -> processor.processBatch(100L));
    }

    @Test
    void markBatchFailed_marksBatchAsFailed() {
        processor.markBatchFailed(100L);

        verify(depositPayoutBatchMapper).markFailed(100L);
    }
}
