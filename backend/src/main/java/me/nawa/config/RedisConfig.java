package me.nawa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class RedisConfig {
    @Value("${redis.host}")
    private String host;

    @Value("${redis.port}")
    private int port;

    @Value("${redis.username:}")
    private String username;

    @Value("${redis.password:}")
    private String password;

    @Value("${redis.ssl-enabled:false}")
    private boolean sslEnabled;

    @Value("${redis.timeout-millis:2000}")
    private long timeoutMillis;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig =
                new RedisStandaloneConfiguration(host, port);

        if (StringUtils.hasText(username)) {
            serverConfig.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfig =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(timeoutMillis));

        if (sslEnabled) {
            clientConfig.useSsl();
        }

        return new LettuceConnectionFactory(serverConfig, clientConfig.build());
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            LettuceConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
