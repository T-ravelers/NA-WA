package me.nawa.config;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

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

    private final PrometheusMeterRegistry registry = new MetricsConfig().meterRegistry();

    @Test
    void registersJvmMemoryMetrics() {
        assertTrue(registry.scrape().contains("jvm_memory_used_bytes"));
    }

    @Test
    void registersJvmGcMetrics() {
        // GC 이름은 실행 JVM마다 달라 접두사만 확인한다.
        assertTrue(registry.scrape().contains("jvm_gc_"));
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
}
