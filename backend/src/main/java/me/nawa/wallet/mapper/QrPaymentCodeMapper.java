package me.nawa.wallet.mapper;

import java.time.LocalDateTime;
import java.util.List;
import me.nawa.wallet.domain.QrPaymentAppointmentMembership;
import me.nawa.wallet.domain.QrPaymentCode;
import me.nawa.wallet.domain.QrPaymentResolveTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface  QrPaymentCodeMapper {

    void insert(QrPaymentCode qrPaymentCode);

    // 아직 만료되지 않고 결제도 되지 않은, 다시 조회 가능한 내 QR 목록
    List<QrPaymentCode> findActiveByPayeeWalletId(
        @Param("payeeWalletId") Long payeeWalletId,
        @Param("now") LocalDateTime now
    );

    QrPaymentResolveTarget findResolveTargetByToken(
        @Param("qrToken") String qrToken
    );

    QrPaymentAppointmentMembership findActiveAppointmentMembership(
        @Param(value = "memberId") Long memberId,
        @Param("appointmentId") Long appointmentId
    );

    // 실제 결제 실행 시 QR 행을 잠금 조회한다
    // 같은 QR을 동시에 결제하려는 요청이 있으면 하나만 초리되도록 막는다
    // resolve/preview용 일반 조회와 달리 execute에서만 사용한다
    QrPaymentCode findByQrTokenForUpdate(
        @Param("qrToken") String qrToken
    );

    // idempotency key 재요청 시 기존 transferId에 연결된 QR 정보를 조회한다
    // 이미 완료된 결제의 QR ID와 토큰을 찾아 기존 결제 결과를 반환하거나
    // 다른 요청에 같능 idempotency key를 재사용할 경우를 판별하는 데 사용한다
    //
    // getIdempotentResult에서만 호출한다. 일반 SELECT로 충분하다 — executePayment가
    // READ COMMITTED로 실행되므로, 동시에 커밋된 QR 갱신도 이 statement 시점의 최신값으로
    // 그대로 보인다(executePayment의 @Transactional(isolation) 주석 참고).
    QrPaymentCode findByCompletedTransferId(
        @Param("transferId") Long transferId
    );

    // QR을 실제 결재 완료 상태로 변경한다.
    // wallet_transfers 생성 추 transferId를 연결하고
    // ACTIVE 상태이며 아직 만료되지 않능 QR만 COMPLETED로 전환한다
    // 반환 값이 1이 아니면 이미 사용되었거나 상태가 변경된 것이므로 결제를 rollback
    int markCompleted(
        @Param("qrPaymentCodeId") Long qrPaymentCodeId,
        @Param("transferId") Long transferId,
        @Param("completedAt") LocalDateTime completedAt,
        @Param("now") LocalDateTime now
    );

    // 공동 소비 결제 시, 결제자가 선택한 약속의 활성 멤버인지 잠금 조회한다
    // 약속 멤버십이 결제 처리 중 LEFT/REMOVED로 바뀌지 않도록 하고
    // trip_expense_links 생성에 필요한 tripId와 appointmentMemberId를 가져온다
    // PERSONAL 소비에서는 호출되지 않는다
    QrPaymentAppointmentMembership findActiveAppointmentMembershipForUpdate(
        @Param("memberId") Long memberId,
        @Param("appointmentId") Long appointmentId
    );
}
