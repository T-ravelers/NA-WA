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
 * 순수 시간 기준으로만 판단하는 전이(활동 시작 시각 도달)를 60초 주기로 훑어서
 * 일괄 반영한다. 사용자에게 보여주는 상태는
 * {@link AppointmentService}의 조회 경로가 조회 시점에 직접 계산하므로, 이
 * 스케줄러가 최대 60초 늦게 돌아도 화면은 곧바로 정확하다 — 이 배치는 실제 DB
 * 컬럼 값을 뒤늦게 따라잡는 역할만 한다.
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
        // activity_start_at은 애플리케이션이 LocalDateTime.now()로 저장한
        // 값이라, DB 서버 컨테이너의 시간대가 다르면 CURRENT_TIMESTAMP 비교가
        // 어긋난다.
        appointmentMapper.startDueAppointments(LocalDateTime.now());
    }
}
