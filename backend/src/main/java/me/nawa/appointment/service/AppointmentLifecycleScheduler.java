package me.nawa.appointment.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import me.nawa.appointment.mapper.AppointmentMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약속 lifecycle 자동 전이 스케줄러
 *
 * 시간만으로 판단하는 두 전이(활동 시작 시각 도달, 활동 종료 시각 도달)를 60초 주기로
 * 훑어서 DB에 반영한다. 지갑 QR 공동결제의 진행 중 약속 목록처럼 저장된 값으로 거르는
 * 코드가 이 결과를 본다 — 종료 전이는 오랫동안 아무도 기록하지 않아, DB가 출석 확정
 * 전까지 IN_PROGRESS에 머무는 바람에 그런 코드가 화면과 다른 말을 했다.
 *
 * 화면은 이 주기를 기다리지 않는다. 조회 응답이 같은 규칙을 조회 시점에도 계산하므로
 * ({@link AppointmentService}) 스케줄러가 늦거나 멈춰도 화면은 정확하다.
 *
 * 정원이 차서 FULL이 되는 경우는 시간과 무관한 이벤트라 여기서 다루지 않고,
 * {@link AppointmentService#joinAppointment}가 참여 성공 시점에 동기로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class AppointmentLifecycleScheduler {

    private final AppointmentMapper appointmentMapper;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void advanceLifecycle() {
        // DB의 CURRENT_TIMESTAMP 대신 애플리케이션 시각을 넘긴다.
        // activity_start_at·activity_end_at은 애플리케이션이 LocalDateTime.now()로
        // 저장한 값이라, DB 서버 컨테이너의 시간대가 다르면 CURRENT_TIMESTAMP
        // 비교가 어긋난다.
        LocalDateTime now = LocalDateTime.now();
        // 시작을 먼저 반영한다. 순서를 뒤집으면 스케줄러가 오래 멈춰 있던 사이에
        // 활동이 통째로 지나간 약속이 한 주기에 종료까지 따라오지 못한다.
        appointmentMapper.startDueAppointments(now);
        appointmentMapper.endDueAppointments(now);
    }
}
