package me.nawa.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 지표 배선 검증.
 *
 * Spring Boot의 Actuator가 없어서 바인더를 손으로 등록하는데, 하나를 빠뜨려도
 * 애플리케이션은 정상 기동한다. 레지스트리만 뜨고 지표가 비어 있는 상태를 잡으려면
 * 실제 출력에 이름이 있는지 확인해야 한다.
 */
class MetricsConfigTest {

    private final MetricsConfig config = new MetricsConfig();
    private final PrometheusMeterRegistry registry = config.meterRegistry();

    @Test
    void registersJvmMemoryMetrics() {
        assertTrue(registry.scrape().contains("jvm_memory_used_bytes"));
    }

    /**
     * GC 바인더만 별도 빈이다 — `AutoCloseable`이라 Spring이 컨텍스트 종료 시
     * 정리하게 두려는 것이다. 빈 배선을 잊으면 GC 지표만 조용히 빠지므로 함께 확인한다.
     */
    @Test
    void registersJvmGcMetrics() {
        try (JvmGcMetrics gcMetrics = config.jvmGcMetrics(registry)) {
            assertNotNull(gcMetrics);
            // GC 이름은 실행 JVM마다 달라 접두사만 확인한다.
            assertTrue(registry.scrape().contains("jvm_gc_"));
        }
    }

    @Test
    void registersJvmThreadMetrics() {
        assertTrue(registry.scrape().contains("jvm_threads_live_threads"));
    }

    @Test
    void registersProcessAndUptimeMetrics() {
        String scraped = registry.scrape();

        assertTrue(scraped.contains("system_cpu_count"));
        assertTrue(scraped.contains("process_uptime_seconds"));
    }

    /**
     * Tomcat MBean은 이 테스트가 도는 평범한 JVM에 없다. 없을 때 예외로 죽지 않고
     * 조용히 건너뛰는 것이 이 바인더의 계약이다 — 지표가 없다고 서버가 못 뜨면 안 된다.
     */
    @Test
    void skipsTomcatMetricsWhenMBeansAbsent() {
        assertNotNull(registry.scrape());
        assertFalse(registry.scrape().contains("tomcat_threads_busy"));
    }

    /* ------------------------- Tomcat 스레드풀 ------------------------- */

    /**
     * Tomcat이 실제로 등록하는 이름 그대로 스텁을 올린다.
     *
     * 이름에 따옴표가 들어 있는 것이 핵심이다 — Tomcat은 커넥터 이름을
     * `name="http-nio-8080"`으로 등록하고, `ObjectName.getKeyProperty`는 따옴표를
     * 벗기지 않고 그대로 돌려준다. 라벨을 그대로 쓰면 Grafana에서
     * `name="http-nio-8080"`으로 거는 표준 대시보드 쿼리가 전부 빗나간다.
     */
    private static final String POOL_OBJECT_NAME =
        "Catalina:type=ThreadPool,name=\"http-nio-8080\"";

    private ObjectName registeredPool;

    @AfterEach
    void unregisterStub() throws Exception {
        if (registeredPool != null) {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(registeredPool);
            registeredPool = null;
        }
    }

    @Test
    void bindsTomcatThreadPoolMetricsFromMBeans() throws Exception {
        registerThreadPoolStub(Map.of(
            "currentThreadsBusy", 7,
            "currentThreadCount", 12,
            "maxThreads", 200));

        PrometheusMeterRegistry tomcatRegistry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new TomcatThreadPoolMetrics().bindTo(tomcatRegistry);

        assertEquals(7.0, gauge(tomcatRegistry, "tomcat.threads.busy"));
        assertEquals(12.0, gauge(tomcatRegistry, "tomcat.threads.current"));
        assertEquals(200.0, gauge(tomcatRegistry, "tomcat.threads.config.max"));
    }

    /**
     * 라벨에 따옴표가 남으면 표준 대시보드가 이 지표를 못 찾는다.
     * Micrometer의 `TomcatMetrics`도 같은 자리에서 따옴표를 벗긴다.
     */
    @Test
    void stripsQuotesFromPoolNameLabel() throws Exception {
        registerThreadPoolStub(Map.of("currentThreadsBusy", 1));

        PrometheusMeterRegistry tomcatRegistry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new TomcatThreadPoolMetrics().bindTo(tomcatRegistry);

        assertEquals(
            "http-nio-8080",
            tomcatRegistry.find("tomcat.threads.busy").gauge().getId().getTag("name"));
    }

    /** 값을 못 읽을 때 0이면 "한가하다"로 오독된다. NaN이어야 한다. */
    @Test
    void reportsNaNWhenAttributeIsUnreadable() throws Exception {
        registerThreadPoolStub(Map.of("currentThreadsBusy", 3));

        PrometheusMeterRegistry tomcatRegistry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new TomcatThreadPoolMetrics().bindTo(tomcatRegistry);

        // 스텁이 maxThreads 를 갖고 있지 않다.
        assertTrue(Double.isNaN(gauge(tomcatRegistry, "tomcat.threads.config.max")));
    }

    private double gauge(PrometheusMeterRegistry target, String name) {
        Gauge found = target.find(name).gauge();
        assertNotNull(found, name + " 지표가 등록되지 않았다");

        return found.value();
    }

    private void registerThreadPoolStub(Map<String, Object> attributes) throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        registeredPool = new ObjectName(POOL_OBJECT_NAME);
        server.registerMBean(new ThreadPoolStub(attributes), registeredPool);
    }

    /** Tomcat 커넥터 MBean 흉내. 속성명은 Tomcat과 같이 소문자로 시작한다. */
    private static final class ThreadPoolStub implements DynamicMBean {

        private final Map<String, Object> attributes;

        private ThreadPoolStub(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public AttributeList getAttributes(String[] names) {
            AttributeList list = new AttributeList();
            for (String name : names) {
                list.add(new Attribute(name, attributes.get(name)));
            }

            return list;
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            MBeanAttributeInfo[] infos = attributes.keySet().stream()
                .map(name -> new MBeanAttributeInfo(
                    name, "java.lang.Integer", name, true, false, false))
                .toArray(MBeanAttributeInfo[]::new);

            return new MBeanInfo(
                ThreadPoolStub.class.getName(), "stub", infos, null, null, null);
        }

        @Override
        public void setAttribute(Attribute attribute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AttributeList setAttributes(AttributeList list) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature) {
            throw new UnsupportedOperationException();
        }
    }
}
