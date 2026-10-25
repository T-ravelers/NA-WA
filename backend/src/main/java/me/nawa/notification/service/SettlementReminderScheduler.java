package me.nawa.notification.service;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 낮 시간에 매시 정각, 미납 리마인더 실행을 시작한다.
 *
 * <p><b>낮에만 돈다.</b> 10시부터 20시까지(서울 시각)다. 밤에는 알림과 푸시를 보내지 않는다. 날짜
 * 단위 기준이라 같은 날 첫 실행이 대상을 모두 처리하고, 이후 실행은 할 일이 없다. 매시 도는 것은
 * 앞 실행이 중간에 멈췄을 때 다음 정각에 이어서 끝내기 위해서다. Spring Batch의 재시작을
 * "처리한 행은 조건에서 빠진다"로 대신한다.
 *
 * <p><b>스케줄러 스레드는 넘기기만 한다.</b> {@code @Scheduled} 작업은 스레드 하나를 같이 쓰므로
 * 여기서 직접 돌리면 다른 스케줄러가 밀린다. 전용 실행기({@link SettlementReminderConfig})에
 * 넘기고, 앞 실행이 아직 돌고 있어 거절되면 로그만 남기고 이번 정각은 건너뛴다.
 */
@Slf4j
@Component
public class SettlementReminderScheduler {

    private final SettlementReminderService reminderService;
    private final ThreadPoolTaskExecutor reminderExecutor;

    public SettlementReminderScheduler(
        SettlementReminderService reminderService,
        @Qualifier("reminderExecutor") ThreadPoolTaskExecutor reminderExecutor
    ) {
        this.reminderService = reminderService;
        this.reminderExecutor = reminderExecutor;
    }

    @Scheduled(cron = "0 0 10-20 * * *", zone = "Asia/Seoul")
    public void remindUnpaidMembers() {
        try {
            reminderExecutor.execute(this::run);
        } catch (RejectedExecutionException exception) {
            log.info("이전 미납 리마인더 실행이 아직 진행 중이라 이번 실행은 건너뜁니다.");
        }
    }

    private void run() {
        try {
            reminderService.sendReminders(LocalDateTime.now());
        } catch (RuntimeException exception) {
            // 실패한 실행은 기록을 남기지 않은 사람을 그대로 두므로 다음 정각이 다시 시도한다.
            log.error("미납 리마인더 실행이 실패했습니다.", exception);
        }
    }
}
