package me.nawa.appointment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 약속의 시간·정원·보증금 조건에 따른 상태 전이를 담당합니다.
 * 상태는 클라이언트의 시작 버튼으로 바꾸지 않고, 주기 작업과 명시적인
 * 전이 API가 같은 조건을 사용하도록 구성합니다.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AppointmentLifecycleService {
    private final AppointmentMapper appointmentMapper;

    @Scheduled(fixedDelayString = "${appointment.lifecycle.fixed-delay-ms:60000}")
    @Transactional
    public void advanceDueAppointments() {
        LocalDateTime now = LocalDateTime.now();
        for (Appointment candidate : appointmentMapper.findLifecycleCandidates(now)) {
            try {
                advanceLocked(candidate.getAppointmentId(), now);
            } catch (RuntimeException exception) {
                log.error(
                        "약속 상태 자동 전이 실패: appointmentId={}",
                        candidate.getAppointmentId(),
                        exception
                );
            }
        }
    }

    @Transactional
    public AppointmentStatus advanceAppointment(Long appointmentId) {
        if (appointmentId == null || appointmentId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Appointment appointment = appointmentMapper.findAppointmentByIdForUpdate(
                appointmentId
        );
        if (appointment == null) {
            throw new BusinessException(AppointmentErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return advanceLocked(appointment, LocalDateTime.now());
    }

    private void advanceLocked(Long appointmentId, LocalDateTime now) {
        Appointment appointment = appointmentMapper.findAppointmentByIdForUpdate(
                appointmentId
        );
        if (appointment != null) {
            advanceLocked(appointment, now);
        }
    }

    private AppointmentStatus advanceLocked(
            Appointment appointment,
            LocalDateTime now) {
        AppointmentStatus current = appointment.getAppointmentStatus();
        if (current == AppointmentStatus.RECRUITING
                && (now.compareTo(appointment.getJoinDeadline()) >= 0
                || appointmentMapper.countParticipatingMembers(
                appointment.getAppointmentId()
        ) >= appointment.getMaxMembers())) {
            transition(appointment, AppointmentStatus.CLOSED);
            current = AppointmentStatus.CLOSED;
        }

        if (current == AppointmentStatus.CLOSED) {
            appointmentMapper.markPendingDepositsCancelled(
                    appointment.getAppointmentId(),
                    now
            );
            appointmentMapper.markPendingMembersLeft(appointment.getAppointmentId());

            int activeMembers = appointmentMapper.countActiveMembers(
                    appointment.getAppointmentId()
            );
            int heldDeposits = appointmentMapper.countHeldDepositsForActiveMembers(
                    appointment.getAppointmentId()
            );
            if (activeMembers > 0 && activeMembers == heldDeposits) {
                transition(appointment, AppointmentStatus.CONFIRMED);
                current = AppointmentStatus.CONFIRMED;
            }
        }

        if (current == AppointmentStatus.CONFIRMED
                && !now.isBefore(appointment.getActivityStartAt())) {
            transition(appointment, AppointmentStatus.IN_PROGRESS);
            current = AppointmentStatus.IN_PROGRESS;
        }
        return current;
    }

    private void transition(Appointment appointment, AppointmentStatus next) {
        AppointmentStatus current = appointment.getAppointmentStatus();
        if (!current.canTransitionTo(next)
                || appointmentMapper.updateStatus(
                appointment.getAppointmentId(),
                current.name(),
                next.name()
        ) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        appointment.setAppointmentStatus(next);
    }
}
