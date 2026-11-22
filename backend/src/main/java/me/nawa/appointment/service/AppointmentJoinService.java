package me.nawa.appointment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.nawa.appointment.dto.request.AppointmentJoinRequest;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 약속 참여의 진입점. DB 트랜잭션을 열기 전에 Redis 입장권으로 자리를 먼저 확인한다(#612).
 *
 * <p><b>트랜잭션을 두지 않는다.</b> {@link AppointmentService#joinAppointment}는 호출되는 순간
 * DB 연결을 잡고 약속 행 잠금을 기다린다. 확인을 그 안에서 하면 자리가 없는 요청도 연결을 쥔 채
 * 줄을 서므로, 이 바깥 한 겹에서 먼저 거절한다.
 *
 * <p>입장권은 문지기일 뿐이고 최종 판단은 {@code joinAppointment}가 그대로 한다. 그래서 입장권을
 * 쓸 수 없거나 이미 가진 회원이면 DB에 그대로 맡긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentJoinService {

    private final AppointmentSeatGate seatGate;
    private final AppointmentService appointmentService;
    private final AppointmentMapper appointmentMapper;

    public AppointmentMemberResponse join(
            Long memberId,
            Long appointmentId,
            AppointmentJoinRequest request) {
        // 입력 오류는 joinAppointment가 400으로 거절한다. 그 요청으로 자리를 뺐다 돌려줄 이유가 없다.
        if (!isWellFormed(memberId, appointmentId, request)) {
            return appointmentService.joinAppointment(memberId, appointmentId, request);
        }

        SeatAcquireResult seat = seatGate.tryAcquire(appointmentId, memberId);
        if (seat == SeatAcquireResult.FULL) {
            // DB가 정원으로 거절할 때와 같은 오류다. 화면은 두 경로를 구분하지 않는다.
            throw new BusinessException(AppointmentErrorCode.JOIN_NOT_AVAILABLE);
        }

        try {
            return appointmentService.joinAppointment(memberId, appointmentId, request);
        } catch (RuntimeException exception) {
            // 여기까지 왔으면 joinAppointment의 트랜잭션은 이미 롤백됐다. 이번에 뺀 자리만 돌려준다 —
            // 이미 보유했거나 입장권을 쓰지 못한 요청은 뺀 것이 없다.
            if (seat == SeatAcquireResult.ACQUIRED && !isActiveMember(appointmentId, memberId)) {
                seatGate.release(appointmentId, memberId);
            }
            throw exception;
        }
    }

    /**
     * 같은 회원의 다른 요청이 이미 참여를 확정했는지 본다.
     *
     * <p>같은 회원이 겹쳐 누르면 한 요청은 자리를 빼고 다른 요청은 "이미 보유"로 함께 DB에 간다.
     * "이미 보유" 쪽이 먼저 확정하면 자리를 뺀 요청은 실패하는데, 그 자리는 확정된 참여가 쓰고 있어
     * 돌려주면 안 된다. 실패한 요청은 약속 행 잠금이 풀린 뒤에 실패하므로, 이때 읽으면 먼저 확정된
     * 참여가 보인다.
     *
     * <p>확인하지 못하면 돌려준다. 돌려주지 않아 남는 자리는 활동 끝까지 매진으로 남지만, 잘못
     * 돌려준 자리는 DB가 정원으로 거절할 뿐이다.
     */
    private boolean isActiveMember(Long appointmentId, Long memberId) {
        try {
            return appointmentMapper.findActiveMembersByAppointmentId(appointmentId).stream()
                    .anyMatch(member -> memberId.equals(member.getMemberId()));
        } catch (RuntimeException exception) {
            log.warn("Failed to check the membership before releasing a seat. appointmentId={}",
                    appointmentId, exception);
            return false;
        }
    }

    private static boolean isWellFormed(
            Long memberId,
            Long appointmentId,
            AppointmentJoinRequest request) {
        return memberId != null && memberId > 0
                && appointmentId != null && appointmentId > 0
                && request != null && request.getTripId() != null && request.getTripId() > 0;
    }
}
