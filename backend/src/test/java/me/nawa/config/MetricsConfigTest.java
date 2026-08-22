package me.nawa.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
     * Tomcat이 없는 환경에서도 레지스트리 배선이 끝까지 돈다.
     *
     * 바인더가 예외를 던지면 `meterRegistry()` 빈 생성이 실패해 컨텍스트가 통째로 안
     * 뜬다 — 지표가 없다고 서버가 못 뜨면 안 된다. "없을 때 아무것도 등록하지 않는다"는
     * 계약 자체는 격리된 MBeanServer를 쓰는 `registersNothingWhenNoThreadPoolExists`가
     * 결정적으로 검증하고, 여기서는 config 경로가 무사한지만 본다.
     */
    @Test
    void meterRegistryIsUsableWithoutTomcat() {
        assertNotNull(registry.scrape());
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

    /**
     * 테스트마다 새 MBeanServer를 쓴다.
     *
     * 플랫폼 MBeanServer는 JVM 싱글턴이라 여기에 스텁을 올리면 "스레드풀이 없을 때"를
     * 단정하는 형제 테스트가 그 스텁을 보게 된다. 지금은 순차 실행이라 정리로 넘어가지만
     * 병렬화를 켜는 순간 깨진다. 아예 공유하지 않는 편이 낫다.
     */
    private final MBeanServer isolatedServer = MBeanServerFactory.newMBeanServer();

    @Test
    void bindsTomcatThreadPoolMetricsFromMBeans() throws Exception {
        registerThreadPoolStub(Map.of(
            "currentThreadsBusy", 7,
            "currentThreadCount", 12,
            "maxThreads", 200));

        PrometheusMeterRegistry tomcatRegistry = bindTomcatMetrics();

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

        PrometheusMeterRegistry tomcatRegistry = bindTomcatMetrics();

        assertEquals(
            "http-nio-8080",
            tomcatRegistry.find("tomcat.threads.busy").gauge().getId().getTag("name"));
    }

    /**
     * 값을 못 읽을 때 0이면 "한가하다"로 오독된다. NaN이어야 한다.
     *
     * 실제 MBean은 모르는 속성에 `AttributeNotFoundException`을 던지므로 스텁도 그렇게
     * 둔다. `null` 반환 경로만 타면 예외 처리 분기가 검증되지 않는다.
     */
    @Test
    void reportsNaNWhenAttributeIsUnreadable() throws Exception {
        registerThreadPoolStub(Map.of("currentThreadsBusy", 3));

        PrometheusMeterRegistry tomcatRegistry = bindTomcatMetrics();

        // 스텁이 maxThreads 를 모르므로 예외를 던진다.
        assertTrue(Double.isNaN(gauge(tomcatRegistry, "tomcat.threads.config.max")));
    }

    /** 스레드풀 MBean이 하나도 없으면 지표를 등록하지 않는다. */
    @Test
    void registersNothingWhenNoThreadPoolExists() {
        PrometheusMeterRegistry tomcatRegistry = bindTomcatMetrics();

        assertNull(tomcatRegistry.find("tomcat.threads.busy").gauge());
    }

    private PrometheusMeterRegistry bindTomcatMetrics() {
        PrometheusMeterRegistry tomcatRegistry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new TomcatThreadPoolMetrics(isolatedServer).bindTo(tomcatRegistry);

        return tomcatRegistry;
    }

    private double gauge(PrometheusMeterRegistry target, String name) {
        Gauge found = target.find(name).gauge();
        assertNotNull(found, name + " 지표가 등록되지 않았다");

        return found.value();
    }

    private void registerThreadPoolStub(Map<String, Object> attributes) throws Exception {
        isolatedServer.registerMBean(
            new ThreadPoolStub(attributes), new ObjectName(POOL_OBJECT_NAME));
    }

    /** Tomcat 커넥터 MBean 흉내. 속성명은 Tomcat과 같이 소문자로 시작한다. */
    private static final class ThreadPoolStub implements DynamicMBean {

        private final Map<String, Object> attributes;

        private ThreadPoolStub(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        @Override
        public Object getAttribute(String name) throws AttributeNotFoundException {
            Object value = attributes.get(name);
            if (value == null) {
                // 실제 Tomcat MBean과 같은 동작. null 반환이 아니라 예외다.
                throw new AttributeNotFoundException(name);
            }

            return value;
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
