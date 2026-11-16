package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 실행 한 번의 흐름(묶음 순회·실패한 건만 건너뛰기·푸시 속도 조절)을 가짜 매퍼와 라이터로 고정한다.
 * 실제 DB에서 중복이 없는지는 {@code SettlementReminderServiceIntegrationTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementReminderServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);

    @Mock
    private SettlementReminderMapper reminderMapper;
    @Mock
    private SettlementReminderWriter writer;
    @Mock
    private PushSender pushSender;

    private ThreadPoolTaskExecutor pushExecutor;

    @BeforeEach
    void setUp() {
        // 푸시 대기열 크기를 읽는 데 쓰는 진짜 실행기다. 운영과 같은 용량의 큐를 둔다.
        pushExecutor = new ThreadPoolTaskExecutor();
        pushExecutor.setCorePoolSize(1);
        pushExecutor.setMaxPoolSize(1);
        pushExecutor.setQueueCapacity(PushConfig.QUEUE_CAPACITY);
        pushExecutor.initialize();
    }

    @AfterEach
    void tearDown() {
        pushExecutor.getThreadPoolExecutor().shutdownNow();
    }

    /**
     * 정책(요청한 날로부터 3일 뒤 첫 발송, 3일 간격, 3회)을 날짜 단위 기준으로 바꾸는 규칙이다.
     * 같은 날이면 몇 시에 불러도 기준이 같고, 요청한 날의 늦은 밤에 만든 정산도 3일 뒤 첫 실행부터
     * 대상이 된다.
     */
    @Test
    void criteriaAt_countsInCalendarDaysNotHours() {
        SettlementReminderCriteria morning = SettlementReminderServiceImpl.criteriaAt(
            LocalDateTime.of(2026, 10, 25, 0, 0, 1));
        SettlementReminderCriteria night = SettlementReminderServiceImpl.criteriaAt(
            LocalDateTime.of(2026, 10, 25, 23, 59, 59));

        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), morning.getFirstBefore());
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), morning.getRemindedBefore());
        assertEquals(morning.getFirstBefore(), night.getFirstBefore());
        assertEquals(morning.getRemindedBefore(), night.getRemindedBefore());
        assertEquals(3, morning.getMaxCount());

        // 22일 23시 59분에 만든 정산은 25일이 3일 뒤라 대상이고, 23일 0시 정각에 만든 정산은 아니다.
        assertTrue(LocalDateTime.of(2026, 10, 22, 23, 59).isBefore(morning.getFirstBefore()));
        assertFalse(LocalDateTime.of(2026, 10, 23, 0, 0).isBefore(morning.getFirstBefore()));
    }

    @Test
    void sendReminders_walksChunksUsingTheLastIdAsTheCursor() {
        List<Notification> firstChunk = notifications(100);
        List<Notification> secondChunk = notifications(30);
        when(reminderMapper.findCandidateIds(any(), eq(0L), eq(100))).thenReturn(ids(1, 100));
        when(reminderMapper.findCandidateIds(any(), eq(100L), eq(100))).thenReturn(ids(101, 130));
        when(reminderMapper.findCandidateIds(any(), eq(130L), eq(100))).thenReturn(List.of());
        when(writer.remind(eq(ids(1, 100)), any())).thenReturn(firstChunk);
        when(writer.remind(eq(ids(101, 130)), any())).thenReturn(secondChunk);

        SettlementReminderResult result = service().sendReminders(NOW);

        assertEquals(2, result.chunks());
        assertEquals(130, result.reminded());
        assertEquals(0, result.skipped());
        verify(pushSender).send(firstChunk);
        verify(pushSender).send(secondChunk);
    }

    @Test
    void sendReminders_chunkFails_retriesOneByOneAndSkipsOnlyTheFailingOne() {
        List<Notification> forFirst = notifications(1);
        List<Notification> forThird = notifications(1);
        when(reminderMapper.findCandidateIds(any(), eq(0L), eq(100))).thenReturn(List.of(1L, 2L, 3L));
        when(reminderMapper.findCandidateIds(any(), eq(3L), eq(100))).thenReturn(List.of());
        when(writer.remind(eq(List.of(1L, 2L, 3L)), any())).thenThrow(new IllegalStateException("bad"));
        when(writer.remind(eq(List.of(1L)), any())).thenReturn(forFirst);
        when(writer.remind(eq(List.of(2L)), any())).thenThrow(new IllegalStateException("bad"));
        when(writer.remind(eq(List.of(3L)), any())).thenReturn(forThird);

        SettlementReminderResult result = service().sendReminders(NOW);

        // 불량 한 건(2번)이 나머지 둘을 막지 않는다. 푸시는 커밋된 두 건에 대해서만 나간다.
        assertEquals(1, result.chunks());
        assertEquals(2, result.reminded());
        assertEquals(1, result.skipped());
        verify(pushSender).send(forFirst);
        verify(pushSender).send(forThird);
        verify(pushSender, times(2)).send(anyList());
    }

    @Test
    void sendReminders_pushRequestFails_neitherSkipsNorStops() {
        when(reminderMapper.findCandidateIds(any(), eq(0L), eq(100))).thenReturn(List.of(1L, 2L));
        when(reminderMapper.findCandidateIds(any(), eq(2L), eq(100))).thenReturn(List.of());
        when(writer.remind(eq(List.of(1L, 2L)), any())).thenReturn(notifications(2));
        doThrow(new IllegalStateException("push")).when(pushSender).send(anyList());

        SettlementReminderResult result = service().sendReminders(NOW);

        // 알림은 이미 적혔다. 푸시 실패 때문에 같은 묶음을 한 건씩 다시 돌리거나 건너뜀으로 세지 않는다.
        assertEquals(2, result.reminded());
        assertEquals(0, result.skipped());
        verify(writer, never()).remind(eq(List.of(1L)), any());
    }

    /**
     * RuntimeException이 아닌 Error는 건너뛰기 경로가 잡지 않고 실행을 멈춘다. 서버가 도중에
     * 죽는 상황을 흉내 내는 데 쓰며, 이미 끝난 묶음은 그대로 남는다.
     */
    @Test
    void sendReminders_error_abortsTheRunButKeepsFinishedChunks() {
        List<Notification> firstChunk = notifications(100);
        when(reminderMapper.findCandidateIds(any(), eq(0L), eq(100))).thenReturn(ids(1, 100));
        when(reminderMapper.findCandidateIds(any(), eq(100L), eq(100))).thenReturn(ids(101, 200));
        when(writer.remind(eq(ids(1, 100)), any())).thenReturn(firstChunk);
        when(writer.remind(eq(ids(101, 200)), any())).thenThrow(new StackOverflowError());

        assertThrows(StackOverflowError.class, () -> service().sendReminders(NOW));

        verify(pushSender).send(firstChunk);
        verify(pushSender, times(1)).send(anyList());
    }

    @Test
    void sendReminders_interrupted_doesNotReadAnotherChunk() {
        Thread.currentThread().interrupt();
        try {
            SettlementReminderResult result = service().sendReminders(NOW);

            assertEquals(0, result.chunks());
            verify(reminderMapper, never()).findCandidateIds(any(), eq(0L), eq(100));
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 푸시 대기열이 가득 차면 새 푸시가 버려진다. 리마인더가 그 대기열을 채워 실시간 정산 알림
     * 푸시를 밀어내지 않도록, 대기열이 내려올 때까지 다음 묶음을 읽지 않는다.
     */
    @Test
    void sendReminders_waitsWhilePushQueueIsAboveTheLimit() throws Exception {
        CountDownLatch release = fillPushQueue(SettlementReminderServiceImpl.PUSH_QUEUE_LIMIT + 10);
        when(reminderMapper.findCandidateIds(any(), eq(0L), eq(100))).thenReturn(List.of());
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            Future<SettlementReminderResult> run = runner.submit(() -> service().sendReminders(NOW));

            Thread.sleep(300);
            assertFalse(run.isDone());
            verify(reminderMapper, never()).findCandidateIds(any(), eq(0L), eq(100));

            release.countDown();
            assertEquals(0, run.get(5, TimeUnit.SECONDS).chunks());
            verify(reminderMapper).findCandidateIds(any(), eq(0L), eq(100));
        } finally {
            release.countDown();
            runner.shutdownNow();
        }
    }

    /** 대기열이 끝내 줄지 않아도 알림 적재를 멈추지 않고, 상한이 지나면 그냥 진행한다. */
    @Test
    void sendReminders_pushQueueNeverDrains_proceedsAfterTheWaitLimit() throws Exception {
        CountDownLatch release = fillPushQueue(SettlementReminderServiceImpl.PUSH_QUEUE_LIMIT + 10);
        when(reminderMapper.findCandidateIds(any(), eq(0L), eq(100))).thenReturn(List.of());
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            SettlementReminderServiceImpl service = new SettlementReminderServiceImpl(
                reminderMapper, writer, pushSender, pushExecutor, Duration.ofMillis(300), Duration.ofMinutes(50));

            Future<SettlementReminderResult> run = runner.submit(() -> service.sendReminders(NOW));

            assertEquals(0, run.get(5, TimeUnit.SECONDS).chunks());
            verify(reminderMapper).findCandidateIds(any(), eq(0L), eq(100));
        } finally {
            release.countDown();
            runner.shutdownNow();
        }
    }

    /**
     * 실행 시간 상한이 지나면 다음 묶음을 읽지 않는다. 20시 실행이 밤까지 이어지지 않게 하는 장치다.
     * 상한을 0으로 두면 첫 묶음을 읽기 전에 이미 지난 것이라 시각에 기대지 않고 확인된다.
     */
    @Test
    void sendReminders_stopsWithoutReadingOnceTheRunTimeLimitHasPassed() {
        SettlementReminderServiceImpl service = new SettlementReminderServiceImpl(
            reminderMapper, writer, pushSender, pushExecutor, Duration.ofSeconds(30), Duration.ZERO);

        SettlementReminderResult result = service.sendReminders(NOW);

        assertEquals(0, result.chunks());
        verify(reminderMapper, never()).findCandidateIds(any(), anyLong(), anyInt());
        verify(writer, never()).remind(any(), any());
    }

    /** 첫 작업이 스레드를 붙잡고 나머지가 큐에 쌓이게 한다. 래치를 풀면 모두 끝난다. */
    private CountDownLatch fillPushQueue(int queued) throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        pushExecutor.execute(() -> {
            started.countDown();
            await(release);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < queued; i++) {
            pushExecutor.execute(() -> await(release));
        }
        assertEquals(queued, pushExecutor.getThreadPoolExecutor().getQueue().size());
        return release;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    // 테스트는 인터페이스 타입으로 부른다. 구현체는 여기서 한 번만 만든다.
    private SettlementReminderService service() {
        return new SettlementReminderServiceImpl(reminderMapper, writer, pushSender, pushExecutor);
    }

    private static List<Long> ids(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }

    /** 내용은 중요하지 않다. 개수만 맞는 알림 목록이다. 비교는 목록 객체 동일성으로 한다. */
    private static List<Notification> notifications(int count) {
        return IntStream.range(0, count).mapToObj(index -> new Notification()).toList();
    }
}
