package me.nawa.explore.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.service.ExploreListCache.CachedPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis가 실패해도 탐색 목록은 DB로 돌아갈 수 있어야 한다. 실패를 예외로 올리지 않고
 * "없음"으로 바꾸는지, 그리고 적중률을 셀 수 있게 결과별로 세는지 본다.
 */
class ExploreListCacheTest {

    private static final Map<String, Object> CONDITION = Map.of("sort", "POPULAR");

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ExploreListCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        cache = new ExploreListCache(redisTemplate, meterRegistry, "nawa:");
    }

    @Test
    void read_countsMissWhenNothingIsStored() {
        when(values.get(anyString())).thenReturn(null);

        assertFalse(cache.read("event", CONDITION, EventSummaryResponse.class).isPresent());
        assertEquals(1.0, count("event", "miss"));
    }

    @Test
    void read_countsHitWhenStored() {
        when(values.get(anyString())).thenReturn("{\"content\":[],\"totalElements\":3}");

        assertEquals(
                3,
                cache.read("event", CONDITION, EventSummaryResponse.class)
                        .orElseThrow()
                        .totalElements()
        );
        assertEquals(1.0, count("event", "hit"));
    }

    @Test
    void read_returnsEmptyWhenRedisFails() {
        when(values.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertFalse(cache.read("event", CONDITION, EventSummaryResponse.class).isPresent());
        assertEquals(1.0, count("event", "error"));
    }

    @Test
    void read_returnsEmptyWhenStoredValueCannotBeParsed() {
        // 응답 형식이 바뀌었는데 버전을 올리지 않은 경우와 같다.
        when(values.get(anyString())).thenReturn("{\"content\":[{\"unknownField\":1}]}");

        assertFalse(cache.read("event", CONDITION, EventSummaryResponse.class).isPresent());
        assertEquals(1.0, count("event", "error"));
    }

    @Test
    void write_swallowsRedisFailure() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(values).set(anyString(), anyString(), eq(Duration.ofSeconds(30)));

        assertDoesNotThrow(() -> cache.write(
                "place", CONDITION, new CachedPage<>(List.of(), 0)
        ));
        assertEquals(1.0, count("place", "error"));
    }

    @Test
    void write_usesTheShortTtl() {
        cache.write("event", CONDITION, new CachedPage<>(List.of(), 0));

        verify(values).set(anyString(), any(String.class), eq(Duration.ofSeconds(30)));
    }

    private double count(String kind, String result) {
        return meterRegistry.counter(
                ExploreListCache.METRIC_NAME, "kind", kind, "result", result
        ).count();
    }
}
