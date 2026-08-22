package me.nawa.observability.controller;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지표 노출 엔드포인트.
 *
 * 경로를 `/internal/`로 시작하게 둔 것은 의도적이다. nginx에서 접두사 하나로 확실히
 * 막을 수 있어야 하는데, `/metrics` 같은 단일 경로는 나중에 다른 경로가 생기면
 * 차단 규칙을 매번 늘려야 한다.
 *
 * 응답 본문은 Prometheus 텍스트 형식이라 공용 `ApiResponse` 봉투로 감싸지 않는다.
 * 감싸면 Prometheus가 파싱하지 못한다.
 */
@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final PrometheusMeterRegistry registry;

    @GetMapping(value = "/internal/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> scrape() {
        return ResponseEntity.ok(registry.scrape());
    }
}
