package me.nawa.appointment.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.mapper.AppointmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 약속 참여 앞에서 남은 자리를 Redis로 먼저 확인·차감한다(#612).
 *
 * <p>약속마다 남은 자리 수와 자리를 가진 회원 집합을 둔다. 확인과 차감은 Lua 하나로 묶어,
 * 마지막 한 자리를 두 요청이 함께 가져가지 못하게 한다. 자리가 없으면 DB 잠금을 기다리지 않고
 * 거절할 수 있다.
 *
 * <p><b>정본은 DB다.</b> 이 숫자는 DB 앞의 문지기일 뿐이고, 참여 트랜잭션은 지금처럼 약속 행을
 * 잠그고 자리를 다시 확인한다. 그래서 여기 숫자가 틀려도 정원은 넘지 않는다. 틀렸을 때의 최악은
 * 자리가 있는데 거절하는 것이고, 그 어긋남은 Redis와 DB를 대조하는 작업이 바로잡는다.
 *
 * <p>Redis를 쓸 수 없으면 예외 대신 {@link SeatAcquireResult#UNAVAILABLE}을 돌려준다. 문지기
 * 하나 때문에 참여 전체가 막히면 안 된다.
 *
 * <p>키의 약속 번호를 {@code {}}로 감싼다. Redis를 클러스터로 나눠도 같은 약속의 두 키가 같은
 * 슬롯에 놓여야 Lua가 둘을 함께 다룰 수 있다.
 */
@Slf4j
@Component
public class AppointmentSeatGate {

    static final String METRIC_NAME = "appointment.seat.gate";

    private static final long NEEDS_INIT = -1L;

    // 키가 없으면 -1, 이미 보유 2, 매진 0, 차감 1.
    // 처음 채울 때 회원이 없으면 집합 키가 없으므로, 차감하며 만든 집합에 자리 키의 남은 시간을 맞춘다.
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end "
                    + "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end "
                    + "if tonumber(redis.call('GET', KEYS[1])) <= 0 then return 0 end "
                    + "redis.call('DECR', KEYS[1]) "
                    + "redis.call('SADD', KEYS[2], ARGV[1]) "
                    + "local ttl = redis.call('PTTL', KEYS[1]) "
                    + "if ttl > 0 then redis.call('PEXPIRE', KEYS[2], ttl) end "
                    + "return 1",
            Long.class
    );

    // 자리 키가 이미 있으면 다른 요청이 먼저 채운 것이다. 자리 키가 만료된 뒤 남은 예전 집합은 지운다.
    private static final DefaultRedisScript<Long> INIT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                    + "redis.call('DEL', KEYS[2]) "
                    + "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) "
                    + "if #ARGV > 2 then "
                    + "redis.call('SADD', KEYS[2], unpack(ARGV, 3)) "
                    + "redis.call('PEXPIRE', KEYS[2], ARGV[2]) "
                    + "end "
                    + "return 1",
            Long.class
    );

    // 집합에서 실제로 빠졌을 때만 되돌린다. 같은 회원을 두 번 돌려줘도 한 번만 늘어난다.
    // 자리 키가 없으면(만료·미초기화) 다음 요청이 DB를 보고 다시 채우므로 아무것도 하지 않는다.
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end "
                    + "if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then "
                    + "redis.call('INCR', KEYS[1]) "
                    + "return 1 "
                    + "end "
                    + "return 0",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final AppointmentMapper appointmentMapper;
    private final MeterRegistry meterRegistry;
    private final String keyPrefix;
    private final Clock clock;

    @Autowired
    public AppointmentSeatGate(
            StringRedisTemplate redisTemplate,
            AppointmentMapper appointmentMapper,
            MeterRegistry meterRegistry,
            @Value("${redis.key-prefix}") String redisKeyPrefix) {
        this(redisTemplate, appointmentMapper, meterRegistry, redisKeyPrefix,
                Clock.systemDefaultZone());
    }

    AppointmentSeatGate(
            StringRedisTemplate redisTemplate,
            AppointmentMapper appointmentMapper,
            MeterRegistry meterRegistry,
            String redisKeyPrefix,
            Clock clock) {
        if (!StringUtils.hasText(redisKeyPrefix)) {
            throw new IllegalArgumentException("Redis key prefix must not be blank");
        }
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.appointmentMapper = Objects.requireNonNull(appointmentMapper);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        String normalizedPrefix = redisKeyPrefix.trim();
        this.keyPrefix = (normalizedPrefix.endsWith(":")
                ? normalizedPrefix
                : normalizedPrefix + ":") + "appointment:";
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 이 회원 몫으로 자리 하나를 뺀다.
     *
     * <p>약속의 키가 아직 없으면 DB의 정원과 ACTIVE 참여자로 채운 뒤 한 번 더 시도한다.
     */
    public SeatAcquireResult tryAcquire(Long appointmentId, Long memberId) {
        try {
            Long outcome = acquire(appointmentId, memberId);
            if (Objects.equals(outcome, NEEDS_INIT)) {
                if (!initialize(appointmentId)) {
                    return count(SeatAcquireResult.UNAVAILABLE);
                }
                outcome = acquire(appointmentId, memberId);
            }
            return count(toResult(outcome));
        } catch (RuntimeException exception) {
            log.warn("Failed to acquire an appointment seat in Redis. appointmentId={}",
                    appointmentId, exception);
            return count(SeatAcquireResult.UNAVAILABLE);
        }
    }

    /**
     * 이 회원 몫의 자리를 돌려준다. 실패해도 던지지 않는다 — 돌려주지 못한 자리는 "자리가 있는데
     * 거절"로만 남고, 대조 작업이 바로잡는다.
     */
    public void release(Long appointmentId, Long memberId) {
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(seatsKey(appointmentId), membersKey(appointmentId)),
                    String.valueOf(memberId)
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to release an appointment seat in Redis. appointmentId={}",
                    appointmentId, exception);
        }
    }

    private Long acquire(Long appointmentId, Long memberId) {
        return redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(seatsKey(appointmentId), membersKey(appointmentId)),
                String.valueOf(memberId)
        );
    }

    /**
     * DB를 보고 키를 채운다. 약속이 없거나 활동이 이미 끝났으면 채우지 않는다 — 그 판단은 DB
     * 처리가 오류 코드와 함께 한다.
     *
     * <p>잠그지 않고 읽는다. 읽는 사이 다른 참여가 커밋되면 남은 자리가 실제보다 많게 적힐 수
     * 있지만, 참여 트랜잭션이 자리를 다시 확인하므로 정원은 넘지 않는다.
     */
    private boolean initialize(Long appointmentId) {
        Appointment appointment = appointmentMapper.findAppointmentById(appointmentId);
        if (appointment == null || appointment.getActivityEndAt() == null) {
            return false;
        }
        // 활동이 끝나면 참여·취소가 없으므로 그때까지만 둔다.
        long ttlMillis = Duration.between(
                LocalDateTime.now(clock), appointment.getActivityEndAt()
        ).toMillis();
        if (ttlMillis <= 0) {
            return false;
        }

        List<AppointmentMember> activeMembers =
                appointmentMapper.findActiveMembersByAppointmentId(appointmentId);
        int remaining = Math.max(0, appointment.getMaxMembers() - activeMembers.size());

        List<String> args = new ArrayList<>(activeMembers.size() + 2);
        args.add(String.valueOf(remaining));
        args.add(String.valueOf(ttlMillis));
        activeMembers.forEach(member -> args.add(String.valueOf(member.getMemberId())));

        redisTemplate.execute(
                INIT_SCRIPT,
                List.of(seatsKey(appointmentId), membersKey(appointmentId)),
                args.toArray()
        );
        return true;
    }

    private static SeatAcquireResult toResult(Long outcome) {
        if (outcome == null) {
            return SeatAcquireResult.UNAVAILABLE;
        }
        return switch (outcome.intValue()) {
            case 1 -> SeatAcquireResult.ACQUIRED;
            case 0 -> SeatAcquireResult.FULL;
            case 2 -> SeatAcquireResult.ALREADY_HOLDING;
            // 채운 직후 만료처럼 키가 다시 사라진 경우다. 한 번 더 채우지 않고 DB에 맡긴다.
            default -> SeatAcquireResult.UNAVAILABLE;
        };
    }

    private SeatAcquireResult count(SeatAcquireResult result) {
        meterRegistry.counter(METRIC_NAME, "result", result.name().toLowerCase(Locale.ROOT))
                .increment();
        return result;
    }

    String seatsKey(Long appointmentId) {
        return keyPrefix + "{" + appointmentId + "}:seats";
    }

    String membersKey(Long appointmentId) {
        return keyPrefix + "{" + appointmentId + "}:members";
    }
}
