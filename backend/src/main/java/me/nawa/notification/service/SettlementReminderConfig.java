package me.nawa.notification.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 미납 리마인더 실행이 쓰는 전용 실행기를 만든다.
 *
 * 이 앱의 {@code @Scheduled} 작업은 스레드 하나를 같이 쓴다. {@code RootConfig}에 스케줄러 빈이
 * 없어서 스프링이 기본 단일 스레드 스케줄러를 만들기 때문이다. 리마인더를 그 스레드에서 직접
 * 돌리면 미납이 많은 날 몇 분씩 붙잡아, 60초마다 도는 보증금 정산 처리와 약속 상태 전이가 그만큼
 * 밀린다. 그래서 스케줄러 스레드는 실행기에 일을 넘기기만 하고 바로 돌아온다.
 */
@Configuration
public class SettlementReminderConfig {

    /**
     * 스레드 하나, 대기열 없음.
     *
     * 스레드가 하나라 한 인스턴스 안에서 실행이 겹치지 않는다. 대기열이 없어서 앞 실행이 아직 돌고
     * 있으면 새 실행은 쌓이지 않고 거절된다. 거절돼도 잃는 것이 없다 — 실행은 처리한 사람을 기록으로
     * 남기므로 다음 실행이 남은 사람부터 이어받는다. 쌓아 두면 앞 실행이 끝난 뒤 할 일 없는 실행이
     * 줄줄이 돈다. 서버가 여러 대일 때 인스턴스 사이의 중복은 이 실행기가 아니라 행 잠금이 막는다.
     */
    @Bean("reminderExecutor")
    public ThreadPoolTaskExecutor reminderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("reminder-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        // 하루에 열한 번 도는 작업이라 쉬는 동안 스레드를 붙들고 있을 이유가 없다.
        executor.setAllowCoreThreadTimeOut(true);
        return executor;
    }
}
