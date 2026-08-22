package me.nawa.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.log4j.Log4j2;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Set;

/**
 * Tomcat 커넥터 스레드풀 지표.
 *
 * Micrometer의 `TomcatMetrics`는 임베디드 컨테이너의 `Manager`를 요구해서 WAR 배포에는
 * 쓸 수 없다. 대신 Tomcat이 항상 띄우는 `Catalina:type=ThreadPool,*` MBean을 직접 읽는다.
 *
 * 부하 테스트에서 이 값이 중요한 이유는, 스레드풀이 포화되면 CPU와 메모리가 한가해도
 * 요청이 큐에서 대기하기 때문이다. 컨테이너 자원 지표만 보면 "서버는 놀고 있는데 느리다"로
 * 보여서 원인을 못 찾는다. `busy / max`가 1에 가까우면 여기가 병목이다.
 *
 * MBean을 못 찾아도 예외를 던지지 않는다. 지표가 없다고 애플리케이션이 뜨지 못하면 안 된다.
 */
@Log4j2
public class TomcatThreadPoolMetrics implements MeterBinder {

    private static final String THREAD_POOL_PATTERN = "Catalina:type=ThreadPool,*";

    @Override
    public void bindTo(MeterRegistry registry) {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        Set<ObjectName> pools;
        try {
            pools = server.queryNames(new ObjectName(THREAD_POOL_PATTERN), null);
        } catch (Exception exception) {
            log.warn("Tomcat 스레드풀 MBean을 조회하지 못해 관련 지표를 등록하지 않습니다.", exception);
            return;
        }

        if (pools.isEmpty()) {
            // 임베디드 Tomcat으로 도는 테스트 환경에서는 비어 있을 수 있다.
            log.info("Tomcat 스레드풀 MBean이 없어 관련 지표를 등록하지 않습니다.");
            return;
        }

        for (ObjectName pool : pools) {
            String name = pool.getKeyProperty("name");

            registerGauge(registry, server, pool, name, "currentThreadsBusy",
                "tomcat.threads.busy", "요청을 처리 중인 스레드 수");
            registerGauge(registry, server, pool, name, "currentThreadCount",
                "tomcat.threads.current", "현재 생성된 스레드 수");
            registerGauge(registry, server, pool, name, "maxThreads",
                "tomcat.threads.config.max", "스레드풀 상한");
        }
    }

    private void registerGauge(
            MeterRegistry registry,
            MBeanServer server,
            ObjectName pool,
            String poolName,
            String attribute,
            String meterName,
            String description) {
        Gauge.builder(meterName, server, mbeanServer -> readAttribute(mbeanServer, pool, attribute))
            // Tomcat은 커넥터를 `name="http-nio-8080"`으로 등록하고 getKeyProperty는
            // 따옴표를 벗기지 않는다. 그대로 두면 라벨이 `"http-nio-8080"`이 되어
            // 표준 대시보드의 `name="http-nio-8080"` 쿼리가 빗나간다.
            // Micrometer의 TomcatMetrics도 같은 자리에서 따옴표를 벗긴다.
            .tag("name", poolName == null ? "unknown" : poolName.replace("\"", ""))
            .description(description)
            .register(registry);
    }

    /** 값을 읽지 못하면 0이 아니라 NaN을 돌려준다. 0은 "한가하다"로 오독된다. */
    private double readAttribute(MBeanServer server, ObjectName pool, String attribute) {
        try {
            Object value = server.getAttribute(pool, attribute);

            return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
        } catch (Exception exception) {
            return Double.NaN;
        }
    }
}
