package me.nawa.auth.oauth.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class OAuthHttpConfig {
    @Bean("oauthRestOperations")
    public RestOperations oauthRestOperations(
            @Value("${oauth.http.connect-timeout-millis}")
            int connectTimeoutMillis,
            @Value("${oauth.http.read-timeout-millis}")
            int readTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "OAuth HTTP timeouts must be positive"
            );
        }

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        return new RestTemplate(requestFactory);
    }
}
