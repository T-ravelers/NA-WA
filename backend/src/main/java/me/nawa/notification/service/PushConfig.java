package me.nawa.notification.service;

import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 푸시 발송이 쓰는 스레드풀과 HTTP 도구를 만든다.
 *
 * 푸시 서비스는 우리가 통제하지 못하는 외부 서버라 느려지거나 멈출 수 있다. 요청 처리 스레드나
 * 공용 풀에서 보내면 그 지연이 정산·알림 API까지 번지므로 전용 풀로 떼어 둔다. 기다리는 시간도
 * 정해 두지 않으면 응답 없는 서버 하나가 풀의 스레드를 전부 붙잡는다.
 */
@Slf4j
@Configuration
public class PushConfig {

    static final int POOL_SIZE = 4;
    static final int QUEUE_CAPACITY = 200;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 큐가 가득 차면 새 작업을 버리고 로그만 남긴다.
     *
     * 호출한 스레드에서 직접 실행(CallerRunsPolicy)하면 밀린 푸시 때문에 정산 요청이 느려지고,
     * 예외로 알리면 정산·알림 처리가 푸시 때문에 실패한다. 발송은 최선 노력이고 놓친 알림은
     * 폴링이 메우므로 버리는 쪽이 안전하다.
     */
    @Bean("pushExecutor")
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-");
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler((task, pool) ->
            log.warn("푸시 발송 대기열이 가득 차 알림 하나를 보내지 않습니다."));
        return executor;
    }

    /** 리다이렉트는 따라가지 않는다(기본값). 푸시 서비스가 다른 주소로 보내는 일은 없다. */
    @Bean("pushHttpClient")
    public HttpClient pushHttpClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }
}
