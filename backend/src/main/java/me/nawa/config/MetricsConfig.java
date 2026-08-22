package me.nawa.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 지표 수집 배선.
 *
 * 이 프로젝트는 Spring Boot가 아니라서 Actuator가 없다. Boot가 자동으로 해 주는
 * 세 가지 — 레지스트리 생성, 바인더 등록, 노출 엔드포인트 — 를 여기서 직접 한다.
 * 바인더를 빠뜨리면 레지스트리는 뜨지만 JVM 지표가 하나도 안 잡히므로,
 * 새 지표가 필요할 때는 이 클래스에 바인더를 추가하는 것이 정해진 자리다.
 *
 * 루트 컨텍스트에 둔다. 서블릿 컨텍스트의 컨트롤러가 부모 컨텍스트의 빈을 보므로
 * 레지스트리를 한 개만 유지할 수 있다.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public PrometheusMeterRegistry meterRegistry() {
        PrometheusMeterRegistry registry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        new TomcatThreadPoolMetrics().bindTo(registry);

        return registry;
    }

    /**
     * GC 지표.
     *
     * `JvmGcMetrics`는 GC 알림 리스너를 등록해서 닫지 않으면 남는다. 컨테이너를 통째로
     * 새로 띄우는 배포라 실질 영향은 없지만, 빈으로 등록해 두면 Spring이 컨텍스트를
     * 닫을 때 함께 정리한다. 다른 바인더와 달리 이것만 `AutoCloseable`이다.
     */
    @Bean(destroyMethod = "close")
    public JvmGcMetrics jvmGcMetrics(MeterRegistry registry) {
        JvmGcMetrics metrics = new JvmGcMetrics();
        metrics.bindTo(registry);

        return metrics;
    }

    /**
     * HikariCP 커넥션 풀 지표.
     *
     * 부하 상황에서 "DB 커넥션을 기다리느라 느린 것"과 "쿼리 자체가 느린 것"은
     * 대응이 전혀 다른데, 이 지표가 없으면 둘을 구분할 수 없다.
     * `hikaricp_connections_pending`이 쌓이면 풀 크기가 부족하다는 뜻이다.
     *
     * DataSource 빈이 만들어진 뒤에 붙여야 해서 별도 초기화 빈으로 둔다.
     */
    @Bean
    public InitializingBean hikariMetricsBinder(
            DataSource dataSource,
            MeterRegistry registry) {
        return () -> {
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).setMetricsTrackerFactory(
                    new MicrometerMetricsTrackerFactory(registry));
            }
        };
    }
}
