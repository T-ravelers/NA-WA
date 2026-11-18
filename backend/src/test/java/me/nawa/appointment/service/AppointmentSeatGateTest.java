package me.nawa.appointment.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.mapper.AppointmentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis를 흉내 내어 키가 없을 때의 초기화 경로와, Redis·DB가 기대와 다를 때 참여를 막지 않고
 * "쓸 수 없음"으로 바꾸는지 본다. Lua의 실제 동작은 통합 테스트가 본다.
 */
class AppointmentSeatGateTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AppointmentMapper appointmentMapper = mock(AppointmentMapper.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AppointmentSeatGate gate = new AppointmentSeatGate(
            redisTemplate,
            appointmentMapper,
            meterRegistry,
            "nawa:",
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)
    );

    @Test
    void tryAcquire_fillsFromDatabaseOnce_whenKeysAreMissing() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-1L, 1L, 1L);
        when(appointmentMapper.findAppointmentById(101L)).thenReturn(appointment(6, NOW.plusHours(3)));
        when(appointmentMapper.findActiveMembersByAppointmentId(101L))
                .thenReturn(List.of(member(900L)));

        assertEquals(SeatAcquireResult.ACQUIRED, gate.tryAcquire(101L, 7L));

        // 자리 받기 → 채우기 → 자리 받기. 채우기에는 정원 6 − ACTIVE 1 = 남은 자리 5와 방장이 실린다.
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate, times(3)).execute(any(RedisScript.class), anyList(), args.capture());
        Object[] initArgs = args.getAllValues().get(1);
        assertEquals("5", initArgs[0]);
        assertEquals(String.valueOf(3 * 60 * 60 * 1000L), initArgs[1]);
        assertEquals("900", initArgs[2]);
        assertEquals(1.0, count("acquired"));
    }

    @Test
    void tryAcquire_isUnavailable_whenAppointmentIsMissing() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-1L);

        assertEquals(SeatAcquireResult.UNAVAILABLE, gate.tryAcquire(101L, 7L));
        verify(appointmentMapper, never()).findActiveMembersByAppointmentId(any());
    }

    /** 끝난 약속을 채우면 만료 시간이 0 이하가 된다. 채우지 않고 DB가 거절하게 둔다. */
    @Test
    void tryAcquire_isUnavailable_whenActivityHasEnded() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-1L);
        when(appointmentMapper.findAppointmentById(101L)).thenReturn(appointment(6, NOW.minusMinutes(1)));

        assertEquals(SeatAcquireResult.UNAVAILABLE, gate.tryAcquire(101L, 7L));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void tryAcquire_isUnavailable_whenRedisFails() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertEquals(SeatAcquireResult.UNAVAILABLE, gate.tryAcquire(101L, 7L));
        assertEquals(1.0, count("unavailable"));
    }

    @Test
    void tryAcquire_mapsScriptResults() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L, 2L);

        assertEquals(SeatAcquireResult.FULL, gate.tryAcquire(101L, 7L));
        assertEquals(SeatAcquireResult.ALREADY_HOLDING, gate.tryAcquire(101L, 7L));
    }

    @Test
    void release_swallowsRedisFailure() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertDoesNotThrow(() -> gate.release(101L, 7L));
    }

    @Test
    void keys_shareAHashTagPerAppointment() {
        assertEquals("nawa:appointment:{101}:seats", gate.seatsKey(101L));
        assertEquals("nawa:appointment:{101}:members", gate.membersKey(101L));
    }

    private double count(String result) {
        return meterRegistry.counter(AppointmentSeatGate.METRIC_NAME, "result", result).count();
    }

    private static Appointment appointment(int maxMembers, LocalDateTime activityEndAt) {
        return Appointment.builder()
                .appointmentId(101L)
                .maxMembers(maxMembers)
                .activityEndAt(activityEndAt)
                .build();
    }

    private static AppointmentMember member(long memberId) {
        return AppointmentMember.builder().memberId(memberId).build();
    }
}
