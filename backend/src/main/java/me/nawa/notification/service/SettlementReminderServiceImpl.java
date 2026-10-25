package me.nawa.notification.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 미납 리마인더 실행 한 번을 묶음 단위로 끝까지 돌린다.
 *
 * <p><b>정책(초안).</b> 정산을 요청한 지 3일째에 처음 보내고, 3일 간격으로, 최대 3번 보낸다.
 * 팀이 확정하면 아래 상수의 숫자만 바꾼다.
 *
 * <p><b>날짜 단위로 센다.</b> "72시간 지났나"로 비교하면 매일 같은 시각에 도는 실행이 밀리초
 * 차이로 하루씩 밀린다. 오늘 날짜에서 (N-1)일 전 자정을 기준으로 삼으면, 요청한 날의 몇 시에
 * 만들었든 N일째 첫 실행부터 대상이 된다. 그래서 같은 날 안에서는 몇 번을 돌려도 결과가 같고,
 * 실행을 낮 시간에 매시 돌릴 수 있다. 같은 날 두 번째 실행부터는 기록 때문에 할 일이 없고, 앞
 * 실행이 중간에 죽었으면 다음 실행이 이어서 끝낸다.
 *
 * <p><b>두 번 읽는다.</b> 잠그지 않고 번호 100개를 고르고, 그 묶음을 {@link SettlementReminderWriter}가
 * 자기 트랜잭션에서 같은 조건으로 다시 읽으며 잠근다. 커서는 고른 번호의 마지막 값으로 넘긴다. 다른
 * 인스턴스가 잡은 행은 그쪽 몫이고, 기록이 이미 있는 행은 조건에서 빠지므로 실행 단위 잠금 없이도
 * 같은 사람에게 두 번 가지 않는다.
 *
 * <p><b>한 건이 묶음을 막지 않는다.</b> 묶음이 실패하면 그 번호들을 한 건씩 각자 트랜잭션으로 다시
 * 처리하고, 그래도 실패한 건만 건너뛰어 센다. 일시적인 잠금 오류도 이 재시도로 대부분 풀린다. 건너뛴
 * 건은 기록이 없으므로 다음 실행이 다시 시도한다. {@code RuntimeException}만 잡는다. {@code Error}는
 * 실행을 멈추는 신호로 두고, 멈춘 뒤 다시 부르면 남은 사람부터 이어진다.
 *
 * <p><b>푸시는 커밋된 뒤에만, 속도를 조절하며.</b> 푸시 대기열이 가득 차면 새 작업을 버리므로, 리마인더가
 * 대기열을 채우면 실시간 정산 알림 푸시가 버려진다. 다음 묶음을 읽기 전에 대기열이 일정 수준 이하로
 * 내려올 때까지 짧게 기다린다. 오래 걸려도(최대 30초) 푸시를 못 보냈다고 알림 적재를 멈추지는 않는다.
 * 푸시가 꺼져 있으면 대기열이 늘 비어서 기다리지 않는다.
 */
@Slf4j
@Service
public class SettlementReminderServiceImpl implements SettlementReminderService {

    static final int FIRST_REMINDER_AFTER_DAYS = 3;
    static final int REMINDER_INTERVAL_DAYS = 3;
    static final int MAX_REMINDERS = 3;

    /** 한 트랜잭션이 잡는 행 수. 트랜잭션이 길어지지 않으면서 왕복도 많지 않은 크기다. */
    static final int CHUNK_SIZE = 100;

    // 실시간 정산 알림 푸시가 쓸 몫이다. 리마인더는 묶음 하나가 대기열에 최대 CHUNK_SIZE건을
    // 넣으므로, 읽기 전에 대기열이 (용량 - 묶음 - 이 몫) 이하여야 실시간 몫이 남는다.
    private static final int REALTIME_PUSH_RESERVE = 50;
    static final int PUSH_QUEUE_LIMIT = PushConfig.QUEUE_CAPACITY - CHUNK_SIZE - REALTIME_PUSH_RESERVE;
    private static final Duration PUSH_WAIT_LIMIT = Duration.ofSeconds(30);
    private static final long PUSH_POLL_MILLIS = 100;

    private final SettlementReminderMapper reminderMapper;
    private final SettlementReminderWriter writer;
    private final PushSender pushSender;
    private final ThreadPoolTaskExecutor pushExecutor;
    private final Duration pushWaitLimit;

    @Autowired
    public SettlementReminderServiceImpl(
        SettlementReminderMapper reminderMapper,
        SettlementReminderWriter writer,
        PushSender pushSender,
        @Qualifier("pushExecutor") ThreadPoolTaskExecutor pushExecutor
    ) {
        this(reminderMapper, writer, pushSender, pushExecutor, PUSH_WAIT_LIMIT);
    }

    /** 기다림 상한을 줄여 볼 수 있게 하려는 테스트용 생성자다. */
    SettlementReminderServiceImpl(
        SettlementReminderMapper reminderMapper,
        SettlementReminderWriter writer,
        PushSender pushSender,
        ThreadPoolTaskExecutor pushExecutor,
        Duration pushWaitLimit
    ) {
        this.reminderMapper = reminderMapper;
        this.writer = writer;
        this.pushSender = pushSender;
        this.pushExecutor = pushExecutor;
        this.pushWaitLimit = pushWaitLimit;
    }

    @Override
    public SettlementReminderResult sendReminders(LocalDateTime now) {
        long startedAt = System.nanoTime();
        SettlementReminderCriteria criteria = criteriaAt(now);
        long cursor = 0;
        int chunks = 0;
        int reminded = 0;
        int skipped = 0;

        while (!Thread.currentThread().isInterrupted()) {
            awaitPushRoom();
            List<Long> ids = reminderMapper.findCandidateIds(criteria, cursor, CHUNK_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            // 처리하지 못한 번호가 있어도 커서는 넘어간다. 다음 실행이 그 번호를 다시 집는다.
            cursor = ids.get(ids.size() - 1);
            chunks++;
            ChunkResult chunk = processChunk(ids, criteria);
            reminded += chunk.reminded();
            skipped += chunk.skipped();
        }

        SettlementReminderResult result = new SettlementReminderResult(
            chunks, reminded, skipped, (System.nanoTime() - startedAt) / 1_000_000
        );
        log.info("미납 리마인더 실행을 마쳤습니다. chunks={}, reminded={}, skipped={}, elapsedMillis={}",
            result.chunks(), result.reminded(), result.skipped(), result.elapsedMillis());
        return result;
    }

    /**
     * 오늘 날짜에서 (N-1)일 전 자정이 기준이다. 정산을 만든 시각이 그보다 앞서면 N일째가 시작된 것이다.
     * 간격도 마지막 리마인더 시각을 같은 식으로 견준다.
     */
    static SettlementReminderCriteria criteriaAt(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return new SettlementReminderCriteria(
            now,
            today.minusDays(FIRST_REMINDER_AFTER_DAYS - 1).atStartOfDay(),
            today.minusDays(REMINDER_INTERVAL_DAYS - 1).atStartOfDay(),
            MAX_REMINDERS
        );
    }

    private ChunkResult processChunk(List<Long> ids, SettlementReminderCriteria criteria) {
        try {
            return new ChunkResult(remindAndPush(ids, criteria), 0);
        } catch (RuntimeException exception) {
            log.warn("리마인더 묶음 처리에 실패해 한 건씩 다시 시도합니다. firstId={}, size={}, cause={}",
                ids.get(0), ids.size(), exception.getClass().getSimpleName());
        }
        int reminded = 0;
        int skipped = 0;
        for (Long id : ids) {
            try {
                reminded += remindAndPush(List.of(id), criteria);
            } catch (RuntimeException exception) {
                skipped++;
                log.warn("리마인더를 건너뜁니다. settlementMemberId={}, cause={}",
                    id, exception.getClass().getSimpleName());
            }
        }
        return new ChunkResult(reminded, skipped);
    }

    private int remindAndPush(List<Long> ids, SettlementReminderCriteria criteria) {
        List<Notification> notifications = writer.remind(ids, criteria);
        sendPush(notifications);
        return notifications.size();
    }

    /** 커밋된 알림만 넘어온다. 푸시가 실패해도 이미 적힌 알림은 그대로이므로 예외를 올리지 않는다. */
    private void sendPush(List<Notification> notifications) {
        try {
            pushSender.send(notifications);
        } catch (RuntimeException exception) {
            log.warn("리마인더 푸시 발송 요청에 실패했습니다. cause={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 푸시 대기열이 여유를 찾을 때까지 기다린다. 상한을 넘기면 그냥 진행한다 — 푸시는 최선 노력이라
     * 놓친 알림은 목록 폴링이 메우고, 푸시 때문에 알림 적재가 멈추면 본말이 뒤집힌다.
     */
    private void awaitPushRoom() {
        long deadline = System.nanoTime() + pushWaitLimit.toNanos();
        while (pushQueueSize() > PUSH_QUEUE_LIMIT) {
            if (System.nanoTime() >= deadline) {
                log.warn("푸시 대기열이 줄지 않아 기다리지 않고 진행합니다. queueSize={}", pushQueueSize());
                return;
            }
            try {
                Thread.sleep(PUSH_POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private int pushQueueSize() {
        return pushExecutor.getThreadPoolExecutor().getQueue().size();
    }

    private record ChunkResult(int reminded, int skipped) {
    }
}
