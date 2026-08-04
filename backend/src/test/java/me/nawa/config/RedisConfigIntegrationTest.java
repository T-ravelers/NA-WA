package me.nawa.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RedisConfig.class)
@TestPropertySource(properties = {
        "redis.host=127.0.0.1",
        "redis.port=6379",
        "redis.username=",
        "redis.password=",
        "redis.ssl-enabled=false",
        "redis.timeout-millis=2000"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_REDIS_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class RedisConfigIntegrationTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void respondsToPing() {
        String response = redisTemplate.execute(
                (RedisCallback<String>) connection -> connection.ping());

        assertEquals("PONG", response);
    }
}
