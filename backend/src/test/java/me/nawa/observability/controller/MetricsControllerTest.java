package me.nawa.observability.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
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
     * (UTF-8이 된 것은 Spring 6부터). charset을 못 박지 않으면 지표 설명의 한글이
     * 전부 `?`로 바뀐다. 파싱은 통과해서 초록불로 넘어가는 종류의 결함이다.
     *
     * <p><b>Accept 헤더를 반드시 실제 값으로 보내야 한다.</b> 헤더 없이 부르면
     * 콘텐츠 협상을 지나가지 않아 어떻게 고치든 통과한다. 실제 스크레이퍼는
     * `text/plain;version=0.0.4`를 명시해서 보내고, 그때 Spring이 Accept 쪽을 고르면서
     * charset이 떨어져 나간다.
     */
    @ParameterizedTest(name = "Accept: {0}")
    @ValueSource(strings = {
        // Prometheus가 실제로 보내는 헤더
        "application/openmetrics-text;version=1.0.0,text/plain;version=0.0.4;q=0.5,*/*;q=0.1",
        "text/plain;version=0.0.4",
        "text/plain",
        "*/*",
        // OpenMetrics만 요청하도록 설정된 수집기
        "application/openmetrics-text;version=1.0.0"
    })
    void servesUtf8ForEveryScraperAcceptHeader(String accept) throws Exception {
        Gauge.builder("nawa.test.gauge", () -> 1.0)
            .description("요청을 처리 중인 스레드 수")
            .register(registry);

        MockHttpServletResponse response = mockMvc
            .perform(get("/internal/metrics").header(HttpHeaders.ACCEPT, accept))
            .andReturn()
            .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        assertTrue(
            response.getContentAsString().contains("요청을 처리 중인 스레드 수"),
            "한글 설명이 깨졌다: " + response.getContentType());
    }
}
