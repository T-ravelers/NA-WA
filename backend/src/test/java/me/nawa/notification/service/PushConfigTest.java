package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PushConfigTest {

    /**
     * 컴포넌트 스캔이 모으는 빈들이 실제로 서로 이어지는지 본다. 생성자 인자의 타입이나 한정자가
     * 어긋나면 단위 테스트는 모두 통과하고 서버를 띄울 때에야 실패한다.
     */
    @Test
    void pushBeans_wireTogetherAndKeepPushOffWithoutKeys() {
        try (AnnotationConfigApplicationContext context = context(Map.of(
            "push.vapid.public-key", "", "push.vapid.private-key", "", "push.vapid.subject", ""
        ))) {
            assertInstanceOf(PushSenderImpl.class, context.getBean(PushSender.class));
            assertEquals(false, context.getBean(PushProperties.class).isEnabled());
        }
    }

    @Test
    void pushBeans_failTheStartupWhenOnlyOneKeyIsSet() {
        assertThrows(BeanCreationException.class, () -> context(Map.of(
            "push.vapid.public-key", "pub", "push.vapid.private-key", "", "push.vapid.subject", ""
        )));
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("push-test", properties));
        context.registerBean(PushSubscriptionMapper.class, () -> mock(PushSubscriptionMapper.class));
        context.register(PushProperties.class, PushConfig.class, PushSenderImpl.class);
        context.refresh();
        return context;
    }

    /**
     * 풀과 큐가 가득 찬 뒤에 들어온 작업은 예외 없이 버려져야 한다. 예외가 나면 알림을 적재한
     * 쪽(정산 처리)까지 푸시 때문에 실패하고, 호출 스레드에서 실행하면 요청이 느려진다.
     */
    @Test
    void pushExecutor_dropsExtraWorkWithoutThrowingWhenPoolAndQueueAreFull() throws Exception {
        ThreadPoolTaskExecutor executor = new PushConfig().pushExecutor();
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();
        int capacity = PushConfig.POOL_SIZE + PushConfig.QUEUE_CAPACITY;

        try {
            for (int i = 0; i < capacity + 10; i++) {
                executor.execute(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ran.incrementAndGet();
                });
            }
            release.countDown();
            executor.getThreadPoolExecutor().shutdown();
            assertTrue(executor.getThreadPoolExecutor().awaitTermination(10, TimeUnit.SECONDS));

            // 받아 준 만큼만 실행되고, 넘친 10건은 조용히 사라졌다.
            assertEquals(capacity, ran.get());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
