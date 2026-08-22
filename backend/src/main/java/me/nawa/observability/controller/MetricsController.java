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

    /**
     * Prometheus 텍스트 노출 형식. charset까지 여기서 못 박는다.
     *
     * <p>Spring 5의 `StringHttpMessageConverter` 기본 문자셋은 ISO-8859-1이라(UTF-8이
     * 된 것은 Spring 6부터) 그냥 두면 지표 설명의 한글이 전부 `?`로 나간다. ASCII라
     * 스크랩은 멀쩡히 통과해서 설명만 조용히 사라진다.
     */
    private static final MediaType PROMETHEUS_TEXT =
        MediaType.parseMediaType("text/plain;version=0.0.4;charset=UTF-8");

    /*
     * `produces`를 쓰지 않고 응답에 Content-Type을 직접 박는 이유가 둘이다.
     *
     * 1. `produces`에 charset을 적어도 콘텐츠 협상에서 떨어져 나간다. 스크레이퍼가
     *    `text/plain;version=0.0.4`를 명시해 보내면 Spring이 그쪽 구체성이 같다고 보고
     *    Accept 값을 고르는데, 그 값에는 charset이 없어 컨버터가 기본값을 도로 붙인다.
     * 2. `produces`가 걸려 있으면 `application/openmetrics-text`만 요청하도록 설정된
     *    수집기가 406을 받아 스크랩이 통째로 실패한다.
     *
     * ResponseEntity에 직접 박으면 협상을 건너뛰고 이 값이 그대로 나간다.
     */
    @GetMapping("/internal/metrics")
    public ResponseEntity<String> scrape() {
        return ResponseEntity.ok()
            .contentType(PROMETHEUS_TEXT)
            .body(registry.scrape());
    }
}
