package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementReminderSchedulerTest {

    private static final long TIMEOUT_SECONDS = 5L;

    private ThreadPoolTaskExecutor reminderExecutor;
    private FakeReminderService reminderService;

    @BeforeEach
    void setUp() {
        reminderExecutor = new SettlementReminderConfig().reminderExecutor();
        reminderExecutor.initialize();
        reminderService = new FakeReminderService();
    }

    @AfterEach
    void tearDown() {
        reminderService.release.countDown();
        reminderExecutor.shutdown();
    }

    /**
     * 실제로 언제 도는지를 크론 식에서 뽑아 확인한다. 하루 열한 번, 10시부터 20시까지 정각이고
     * 밤에는 돌지 않는다. 시간대는 서버 설정과 무관하게 서울로 못 박혀 있어야 한다.
     */
    @Test
    void schedule_runsOnTheHourFromTenToTwentyInSeoulOnly() throws Exception {
        Scheduled scheduled = SettlementReminderScheduler.class
            .getMethod("remindUnpaidMembers").getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals("Asia/Seoul", scheduled.zone());

        CronExpression cron = CronExpression.parse(scheduled.cron());
        LocalDate day = LocalDate.of(2026, 10, 25);
        List<LocalDateTime> triggers = new ArrayList<>();
        LocalDateTime next = cron.next(day.atStartOfDay().minusSeconds(1));
        while (next != null && next.toLocalDate().equals(day)) {
            triggers.add(next);
            next = cron.next(next);
        }

        assertEquals(11, triggers.size());
        assertEquals(day.atTime(10, 0), triggers.get(0));
        assertEquals(day.atTime(20, 0), triggers.get(10));
        assertTrue(triggers.stream().allMatch(time -> time.getMinute() == 0 && time.getSecond() == 0));
        // 20시 다음은 다음 날 10시다. 그 사이에는 돌지 않는다.
        assertEquals(day.plusDays(1).atTime(10, 0), next);
    }

    /** 스케줄러 스레드를 붙잡지 않는다. 일은 전용 실행기가 하고, 실행 시각은 호출 시점이다. */
    @Test
    void remindUnpaidMembers_handsTheRunOverAndReturnsAtOnce() throws Exception {
        reminderService.blockUntilReleased = true;
        LocalDateTime before = LocalDateTime.now();

        scheduler().remindUnpaidMembers();

        // 서비스는 아직 끝나지 않았는데 호출은 이미 돌아왔다.
        assertTrue(reminderService.started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, reminderService.finished.get());
        assertTrue(reminderService.threadName().startsWith("reminder-"));
        LocalDateTime passed = reminderService.nows.get(0);
        assertFalse(passed.isBefore(before) || passed.isAfter(LocalDateTime.now()));
    }

    /**
     * 앞 실행이 아직 돌고 있으면 이번 정각은 쌓지 않고 건너뛴다. 쌓아 두면 앞 실행이 끝난 뒤 할 일
     * 없는 실행이 줄줄이 돈다. 앞 실행이 끝나면 다음 정각은 다시 돈다.
     */
    @Test
    void remindUnpaidMembers_previousRunStillGoing_skipsInsteadOfQueueing() throws Exception {
        reminderService.blockUntilReleased = true;
        SettlementReminderScheduler scheduler = scheduler();
        scheduler.remindUnpaidMembers();
        assertTrue(reminderService.started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        scheduler.remindUnpaidMembers();
        scheduler.remindUnpaidMembers();

        reminderService.release.countDown();
        awaitFinished(1);
        assertEquals(1, reminderService.calls.get());

        awaitIdle();
        scheduler.remindUnpaidMembers();
        awaitFinished(2);
        assertEquals(2, reminderService.calls.get());
    }

    /** 한 번 실패해도 실행기가 멈추지 않는다. 다음 정각이 다시 시도한다. */
    @Test
    void remindUnpaidMembers_failedRun_doesNotStopLaterRuns() throws Exception {
        reminderService.failFirstCall = true;
        SettlementReminderScheduler scheduler = scheduler();

        scheduler.remindUnpaidMembers();
        awaitFinished(1);
        awaitIdle();
        scheduler.remindUnpaidMembers();
        awaitFinished(2);

        assertEquals(2, reminderService.calls.get());
    }

    /**
     * 실행기가 둘(푸시, 리마인더) 있어도 각자 자기 자리에 들어간다. 타입이 같은 빈이 둘이라
     * 한정자가 어긋나면 서버를 띄울 때에야 실패하고 단위 테스트는 모두 통과한다.
     */
    @Test
    void beans_wireTheRightExecutorsWhenBothExecutorsExist() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SettlementReminderMapper.class, () -> mock(SettlementReminderMapper.class));
            context.registerBean(SettlementReminderWriter.class, () -> mock(SettlementReminderWriter.class));
            context.registerBean(PushSender.class, () -> mock(PushSender.class));
            context.register(
                PushConfig.class, SettlementReminderConfig.class,
                SettlementReminderServiceImpl.class, SettlementReminderScheduler.class
            );
            context.refresh();

            assertSame(
                context.getBean("reminderExecutor"),
                ReflectionTestUtils.getField(context.getBean(SettlementReminderScheduler.class), "reminderExecutor")
            );
            assertSame(
                context.getBean("pushExecutor"),
                ReflectionTestUtils.getField(context.getBean(SettlementReminderServiceImpl.class), "pushExecutor")
            );
        }
    }

    private SettlementReminderScheduler scheduler() {
        return new SettlementReminderScheduler(reminderService, reminderExecutor);
    }

    /**
     * 일이 끝난 직후 스레드가 다음 일을 받을 자리로 돌아갈 때까지 기다린다. 그 찰나에 들어온 호출은
     * 거절되는데, 운영에서는 한 시간에 한 번이라 건너뛴 정각 하나일 뿐이지만 테스트는 흔들린다.
     */
    private void awaitIdle() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (reminderExecutor.getActiveCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        Thread.sleep(50);
    }

    private void awaitFinished(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (reminderService.finished.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, reminderService.finished.get());
    }

    /** 호출을 세고, 원하면 풀어 줄 때까지 붙잡거나 첫 호출에서 실패하는 대역이다. */
    private static final class FakeReminderService implements SettlementReminderService {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger finished = new AtomicInteger();
        final List<LocalDateTime> nows = new CopyOnWriteArrayList<>();
        final List<String> threadNames = new CopyOnWriteArrayList<>();
        volatile boolean blockUntilReleased;
        volatile boolean failFirstCall;

        @Override
        public SettlementReminderResult sendReminders(LocalDateTime now) {
            int call = calls.incrementAndGet();
            nows.add(now);
            threadNames.add(Thread.currentThread().getName());
            started.countDown();
            try {
                if (blockUntilReleased) {
                    release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                if (failFirstCall && call == 1) {
                    throw new IllegalStateException("run failed");
                }
                return new SettlementReminderResult(0, 0, 0, 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new SettlementReminderResult(0, 0, 0, 0);
            } finally {
                finished.incrementAndGet();
            }
        }

        String threadName() {
            return threadNames.get(0);
        }
    }
}
