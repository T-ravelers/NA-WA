package me.nawa.common.ocr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * 글자 인식 서버를 부를 때 쓰는 HTTP 도구를 만든다.
 *
 * 기다리는 시간을 정해 두지 않으면 상대 서버가 응답하지 않을 때 우리 요청 처리 스레드가
 * 그대로 붙잡혀 있게 된다. 그런 요청이 쌓이면 영수증과 무관한 API까지 함께 느려진다.
 */
@Configuration
public class ClovaOcrConfig {

    @Bean("clovaOcrRestOperations")
    public RestOperations clovaOcrRestOperations(ClovaOcrProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        return new RestTemplate(requestFactory);
    }
}
