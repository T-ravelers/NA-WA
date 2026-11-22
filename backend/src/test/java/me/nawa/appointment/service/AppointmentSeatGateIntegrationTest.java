package me.nawa.appointment.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.dto.request.AppointmentJoinRequest;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.config.RedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Redis에서 Lua가 확인·차감·반환을 한 동작으로 하는지 본다. DB는 흉내 낸다.
 *
 * <p>테스트마다 다른 약속 번호를 써서 같은 Redis를 쓰는 다른 테스트·개발 서버 키와 섞이지 않게 한다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RedisConfig.class)
@TestPropertySource(properties = {
        "redis.host=127.0.0.1",
        "redis.port=6379",
        "redis.username=",
        "redis.password=",
        "redis.ssl-enabled=false",
        "redis.timeout-millis=2000",
        "redis.key-prefix=nawa:test:"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_REDIS_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class AppointmentSeatGateIntegrationTest {

    private static final AtomicLong NEXT_APPOINTMENT_ID = new AtomicLong(
            System.currentTimeMillis() % 1_000_000_000L * 10
    );

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final AppointmentMapper appointmentMapper = mock(AppointmentMapper.class);
    private AppointmentSeatGate gate;
    private long appointmentId;

    @BeforeEach
    void setUp() {
        gate = new AppointmentSeatGate(
                redisTemplate, appointmentMapper, new SimpleMeterRegistry(), "nawa:test:",
                Clock.systemDefaultZone()
        );
        appointmentId = NEXT_APPOINTMENT_ID.incrementAndGet();
        // 정원 6, 방장(900)만 ACTIVE → 남은 자리 5
        givenDatabase(6, List.of(900L));
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(List.of(
                gate.seatsKey(appointmentId),
                gate.membersKey(appointmentId),
                gate.releaseSeqKey(appointmentId)));
    }

    @Test
    void tryAcquire_fillsFromDatabaseThenAcquires() {
        assertEquals(SeatAcquireResult.ACQUIRED, gate.tryAcquire(appointmentId, 1L));

        assertEquals("4", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
        assertEquals(Set.of("900", "1"), members());
    }

    @Test
    void tryAcquire_returnsFull_whenNoSeatIsLeft() {
        for (long memberId = 1; memberId <= 5; memberId++) {
            assertEquals(SeatAcquireResult.ACQUIRED, gate.tryAcquire(appointmentId, memberId));
        }

        assertEquals(SeatAcquireResult.FULL, gate.tryAcquire(appointmentId, 6L));
        assertEquals("0", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
    }

    /** 같은 회원이 두 번 눌러도 자리는 한 번만 빠진다. 방장처럼 처음부터 명단에 있는 회원도 같다. */
    @Test
    void tryAcquire_doesNotDeductTwiceForTheSameMember() {
        gate.tryAcquire(appointmentId, 1L);

        assertEquals(SeatAcquireResult.ALREADY_HOLDING, gate.tryAcquire(appointmentId, 1L));
        assertEquals(SeatAcquireResult.ALREADY_HOLDING, gate.tryAcquire(appointmentId, 900L));
        assertEquals("4", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
    }

    /** 남은 자리 5개에 50명이 동시에 몰려도 정확히 5명만 통과한다. 키가 없는 상태에서 시작한다. */
    @Test
    void tryAcquire_neverAdmitsMoreThanTheRemainingSeats_underConcurrency() throws Exception {
        Map<SeatAcquireResult, Long> results = runConcurrently(50, memberId ->
                gate.tryAcquire(appointmentId, memberId));

        assertEquals(5L, results.getOrDefault(SeatAcquireResult.ACQUIRED, 0L));
        assertEquals(45L, results.getOrDefault(SeatAcquireResult.FULL, 0L));
        assertEquals("0", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
        assertEquals(6, members().size());
    }

    @Test
    void release_returnsTheSeatOnlyOnce() {
        gate.tryAcquire(appointmentId, 1L);

        gate.release(appointmentId, 1L);
        gate.release(appointmentId, 1L);

        assertEquals("5", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
        assertEquals(Set.of("900"), members());
    }

    /** 자리를 가진 적 없는 회원을 돌려줘도 숫자가 늘지 않는다. */
    @Test
    void release_ignoresMembersWithoutASeat() {
        gate.tryAcquire(appointmentId, 1L);

        gate.release(appointmentId, 2L);

        assertEquals("4", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
    }

    /** 키가 없을 때 돌려주면 아무것도 만들지 않는다. 다음 요청이 DB를 보고 채운다. */
    @Test
    void release_doesNothing_whenKeysAreMissing() {
        gate.release(appointmentId, 1L);

        assertEquals(Boolean.FALSE, redisTemplate.hasKey(gate.seatsKey(appointmentId)));
    }

    /**
     * 처음 채우려고 DB를 읽은 직후 취소가 커밋되고 자리 반환이 먼저 실행된 경우다. 반환할 키가 아직
     * 없으므로 반환 순번만 오르고, 초기화는 그것을 보고 다시 읽는다. 취소 전 인원(정원 2·참여 2)으로
     * 채우면 빈자리가 활동 끝까지 매진으로 남는다.
     */
    @Test
    void tryAcquire_rereads_whenACancellationCommitsWhileInitializing() {
        when(appointmentMapper.findAppointmentById(appointmentId)).thenReturn(Appointment.builder()
                .appointmentId(appointmentId)
                .maxMembers(2)
                .activityEndAt(LocalDateTime.now().plusHours(3))
                .build());
        List<AppointmentMember> beforeCancel = members(900L, 1L);
        List<AppointmentMember> afterCancel = members(900L);
        when(appointmentMapper.findActiveMembersByAppointmentId(appointmentId))
                .thenAnswer(invocation -> {
                    // 첫 읽기 직후 회원 1의 취소가 커밋되고 반환이 실행된다.
                    gate.release(appointmentId, 1L);
                    return beforeCancel;
                })
                .thenReturn(afterCancel);

        assertEquals(SeatAcquireResult.ACQUIRED, gate.tryAcquire(appointmentId, 2L));
        assertEquals("0", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
        assertEquals(Set.of("900", "2"), members());
    }

    /**
     * 마지막 한 자리에서 같은 회원의 요청 A가 자리를 빼고, 요청 B는 "이미 보유"로 DB에 간다. B가 먼저
     * 참여를 확정하고 A가 실패해도 그 자리는 B의 것이라 돌려주지 않는다. 돌려주면 정원이 찼는데도
     * 다음 회원이 입장권을 통과해 DB 잠금을 잡고 실패한다.
     */
    @Test
    void join_doesNotReturnTheSeatTakenByAnotherRequestOfTheSameMember() {
        AtomicReference<List<AppointmentMember>> active = new AtomicReference<>(members(900L));
        when(appointmentMapper.findAppointmentById(appointmentId)).thenReturn(Appointment.builder()
                .appointmentId(appointmentId)
                .maxMembers(2)
                .activityEndAt(LocalDateTime.now().plusHours(3))
                .build());
        when(appointmentMapper.findActiveMembersByAppointmentId(appointmentId))
                .thenAnswer(invocation -> active.get());

        AppointmentService appointmentService = mock(AppointmentService.class);
        AppointmentJoinService joinService =
                new AppointmentJoinService(gate, appointmentService, appointmentMapper);
        AppointmentJoinRequest request = new AppointmentJoinRequest();
        request.setTripId(42L);
        when(appointmentService.joinAppointment(7L, appointmentId, request)).thenAnswer(invocation -> {
            // A가 DB 잠금을 기다리는 사이 B가 입장권(이미 보유)을 거쳐 먼저 확정한다.
            assertEquals(SeatAcquireResult.ALREADY_HOLDING, gate.tryAcquire(appointmentId, 7L));
            active.set(members(900L, 7L));
            throw new BusinessException(AppointmentErrorCode.ALREADY_JOINED);
        });

        assertThrows(BusinessException.class, () -> joinService.join(7L, appointmentId, request));

        assertEquals(SeatAcquireResult.FULL, gate.tryAcquire(appointmentId, 8L));
        assertEquals("0", redisTemplate.opsForValue().get(gate.seatsKey(appointmentId)));
    }

    /** 두 키 모두 활동 종료까지만 남는다. 회원이 없어 차감하며 처음 만든 명단도 같다. */
    @Test
    void keys_expireAtActivityEnd() {
        givenDatabase(6, List.of());

        gate.tryAcquire(appointmentId, 1L);

        long seatsTtl = redisTemplate.getExpire(gate.seatsKey(appointmentId), TimeUnit.SECONDS);
        long membersTtl = redisTemplate.getExpire(gate.membersKey(appointmentId), TimeUnit.SECONDS);
        assertTrue(seatsTtl > 0 && seatsTtl <= 3 * 60 * 60, "seats ttl=" + seatsTtl);
        assertTrue(membersTtl > 0 && membersTtl <= 3 * 60 * 60, "members ttl=" + membersTtl);
    }

    private void givenDatabase(int maxMembers, List<Long> activeMemberIds) {
        when(appointmentMapper.findAppointmentById(appointmentId)).thenReturn(Appointment.builder()
                .appointmentId(appointmentId)
                .maxMembers(maxMembers)
                .activityEndAt(LocalDateTime.now().plusHours(3))
                .build());
        when(appointmentMapper.findActiveMembersByAppointmentId(appointmentId)).thenReturn(
                activeMemberIds.stream()
                        .map(id -> AppointmentMember.builder().memberId(id).build())
                        .toList());
    }

    private static List<AppointmentMember> members(Long... memberIds) {
        return Arrays.stream(memberIds)
                .map(id -> AppointmentMember.builder().memberId(id).build())
                .toList();
    }

    private Set<String> members() {
        return redisTemplate.opsForSet().members(gate.membersKey(appointmentId));
    }

    private static Map<SeatAcquireResult, Long> runConcurrently(
            int requests,
            Function<Long, SeatAcquireResult> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SeatAcquireResult>> futures = new ArrayList<>();
            for (long memberId = 1; memberId <= requests; memberId++) {
                long id = memberId;
                Callable<SeatAcquireResult> task = () -> {
                    start.await();
                    return action.apply(id);
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            List<SeatAcquireResult> results = new ArrayList<>();
            for (Future<SeatAcquireResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results.stream().collect(Collectors.groupingBy(
                    Function.identity(), Collectors.counting()));
        } finally {
            executor.shutdownNow();
        }
    }
}
