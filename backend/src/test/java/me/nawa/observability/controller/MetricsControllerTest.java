package me.nawa.observability.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 지표 엔드포인트의 응답 계약.
 *
 * 여기서 고정하는 것은 "Prometheus가 실제로 파싱할 수 있는 형태로 나가는가"다.
 * 레지스트리에 지표가 잘 담기는 것과 그것이 응답으로 온전히 나가는 것은 다른 문제이고,
 * 후자는 컨버터·인코딩 같은 프레임워크 기본값에 좌우된다.
 */
class MetricsControllerTest {

    private PrometheusMeterRegistry registry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MetricsController(registry))
            .build();
    }

    @Test
    void returnsPrometheusTextFormat() throws Exception {
        Gauge.builder("nawa.test.gauge", () -> 42.0).register(registry);

        String body = mockMvc.perform(get("/internal/metrics"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertTrue(body.contains("nawa_test_gauge"));
        assertTrue(body.contains("42.0"));
    }

    /**
     * 공용 `ApiResponse` 봉투로 감싸면 Prometheus가 파싱하지 못한다.
     * 지금은 저장소에 `ResponseBodyAdvice`가 없어 성립하지만, 전역 advice가 생기면
     * 조용히 깨지는 자리라 고정해 둔다.
     */
    @Test
    void doesNotWrapBodyInApiResponseEnvelope() throws Exception {
        Gauge.builder("nawa.test.gauge", () -> 1.0).register(registry);

        String body = mockMvc.perform(get("/internal/metrics"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertFalse(body.trim().startsWith("{"));
        assertFalse(body.contains("\"success\""));
    }

    /**
     * Spring 5의 `StringHttpMessageConverter` 기본 문자셋은 ISO-8859-1이다
     * (UTF-8이 된 것은 Spring 6부터). `produces`에 charset을 적지 않으면 지표 설명의
     * 한글이 전부 `?`로 바뀐다. 파싱은 통과해서 초록불로 넘어가는 종류의 결함이다.
     */
    @Test
    void servesUtf8SoKoreanDescriptionsSurvive() throws Exception {
        Gauge.builder("nawa.test.gauge", () -> 1.0)
            .description("요청을 처리 중인 스레드 수")
            .register(registry);

        var response = mockMvc.perform(get("/internal/metrics")).andReturn().getResponse();

        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        assertTrue(response.getContentAsString().contains("요청을 처리 중인 스레드 수"));
    }
}
