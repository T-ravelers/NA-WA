package me.nawa.explore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import me.nawa.config.RedisConfig;
import me.nawa.explore.domain.EventStatus;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import me.nawa.explore.service.ExploreListCache.CachedPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RedisConfig.class,
        ExploreListCache.class,
        ExploreListCacheIntegrationTest.MeterConfig.class
})
@TestPropertySource(properties = {
        "redis.host=127.0.0.1",
        "redis.port=6379",
        "redis.username=",
        "redis.password=",
        "redis.ssl-enabled=false",
        "redis.timeout-millis=2000",
        "redis.key-prefix=nawa:test:"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_REDIS_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class ExploreListCacheIntegrationTest {

    @Configuration
    static class MeterConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private ExploreListCache cache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 테스트끼리, 그리고 같은 Redis를 쓰는 개발 서버 값과 섞이지 않게 조건마다 붙인다.
    private final String marker = UUID.randomUUID().toString();

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("nawa:test:explore:list:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void writeThenRead_returnsTheSameEventPageWithTtl() throws Exception {
        EventSummaryResponse event = EventSummaryResponse.builder()
                .itemId(7L)
                .eventKind("FESTIVAL")
                .status(EventStatus.ONGOING)
                .title("벚꽃 축제")
                .latitude(new BigDecimal("37.5665000"))
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .isPermanent(false)
                .build();
        Map<String, Object> condition = Map.of("marker", marker, "sort", "POPULAR");

        cache.write("event", condition, new CachedPage<>(List.of(event), 45));

        Optional<CachedPage<EventSummaryResponse>> found =
                cache.read("event", condition, EventSummaryResponse.class);
        assertTrue(found.isPresent());
        assertEquals(45, found.get().totalElements());
        EventSummaryResponse read = found.get().content().get(0);
        assertEquals(7L, read.getItemId());
        assertEquals(EventStatus.ONGOING, read.getStatus());
        assertEquals("벚꽃 축제", read.getTitle());
        assertEquals(new BigDecimal("37.5665000"), read.getLatitude());
        assertEquals(LocalDate.of(2026, 10, 31), read.getEndDate());
        assertEquals(false, read.getIsPermanent());

        Long ttlSeconds = redisTemplate.getExpire(cache.key("event", condition));
        assertTrue(ttlSeconds != null && ttlSeconds > 0
                && ttlSeconds <= ExploreListCache.TTL.toSeconds());
    }

    @Test
    void writeThenRead_keepsPlaceJsonFields() {
        PlaceSummaryResponse place = PlaceSummaryResponse.builder()
                .itemId(9L)
                .name("카페")
                .imageUrls(new ObjectMapper().createArrayNode().add("a.jpg"))
                .viewCount(12)
                .build();
        Map<String, Object> condition = Map.of("marker", marker);

        cache.write("place", condition, new CachedPage<>(List.of(place), 1));

        PlaceSummaryResponse read = cache.read("place", condition, PlaceSummaryResponse.class)
                .orElseThrow()
                .content()
                .get(0);
        assertEquals("카페", read.getName());
        assertEquals("a.jpg", read.getImageUrls().get(0).asText());
        assertEquals(12, read.getViewCount());
    }

    @Test
    void read_missesWhenAnyConditionDiffers() {
        cache.write(
                "event",
                Map.of("marker", marker, "language", "en"),
                new CachedPage<>(List.of(), 0)
        );

        assertFalse(cache.read(
                "event",
                Map.of("marker", marker, "language", "ja"),
                EventSummaryResponse.class
        ).isPresent());
        // 종류가 다르면 같은 조건이어도 다른 메모지다.
        assertFalse(cache.read(
                "place",
                Map.of("marker", marker, "language", "en"),
                PlaceSummaryResponse.class
        ).isPresent());
    }

    @Test
    void key_isTheSameRegardlessOfMapOrder() throws Exception {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("language", "en");
        first.put("marker", marker);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("marker", marker);
        second.put("language", "en");

        assertEquals(cache.key("event", first), cache.key("event", second));
        assertTrue(cache.key("event", first)
                .startsWith("nawa:test:explore:list:" + ExploreListCache.KEY_VERSION + ":event:"));
    }
}
