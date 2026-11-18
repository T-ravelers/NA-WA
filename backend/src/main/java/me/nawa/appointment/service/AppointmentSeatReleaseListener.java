package me.nawa.appointment.service;

import lombok.RequiredArgsConstructor;
import me.nawa.appointment.event.AppointmentMemberLeftEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 참여 취소가 커밋된 뒤 Redis 입장권 자리를 돌려준다(#612).
 *
 * <p><b>커밋된 뒤에만 듣는다.</b> 취소가 아직 끝나지 않았는데 자리를 먼저 돌려주면, 그 자리를 다른
 * 참여가 가져간 뒤 취소가 롤백되어 회원이 그대로 남을 때 자리가 하나 모자란다. 롤백되면 이 메서드는
 * 불리지 않는다.
 *
 * <p>돌려주기가 실패해도 취소는 이미 끝났으므로 {@link AppointmentSeatGate#release}가 로그만 남긴다.
 * 이벤트가 트랜잭션 밖에서 발행되면 이 리스너는 불리지 않는다 — 발행 지점인
 * {@code leaveAppointment}는 {@code @Transactional}이다.
 */
@Component
@RequiredArgsConstructor
public class AppointmentSeatReleaseListener {

    private final AppointmentSeatGate seatGate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberLeft(AppointmentMemberLeftEvent event) {
        seatGate.release(event.appointmentId(), event.memberId());
    }
}
