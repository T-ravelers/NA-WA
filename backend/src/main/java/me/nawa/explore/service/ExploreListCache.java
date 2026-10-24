package me.nawa.explore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 탐색 목록 첫 페이지를 Redis에 짧게 담아 둔다(#575).
 *
 * <p>같은 첫 화면을 여러 사람이 열 때 DB가 목록과 개수를 매번 다시 계산하지 않게 한다.
 * 담는 것은 모든 회원에게 같은 값뿐이다 — 찜 여부처럼 회원마다 다른 값은 호출부가 꺼낸 뒤
 * 따로 채운다. 그 값을 담으면 다른 회원의 찜 표시가 섞여 나간다.
 *
 * <p>캐시는 정본이 아니다. Redis 읽기·쓰기나 값 해석이 실패하면 "없음"으로 보고 호출부가
 * DB에서 읽게 한다. 여기서 예외를 던지면 Redis 장애가 탐색 화면 장애로 번진다.
 *
 * <p>키에는 {@link #KEY_VERSION}이 들어간다. 담는 응답의 형식을 바꾸면 이 값을 올려야
 * 배포 직후 옛 형식의 값을 읽지 않는다.
 */
@Slf4j
@Component
public class ExploreListCache {

    static final Duration TTL = Duration.ofSeconds(30);
    static final String KEY_VERSION = "v1";
    static final String METRIC_NAME = "explore.list.cache";

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final String keyPrefix;
    private final ObjectMapper objectMapper;

    public ExploreListCache(
        StringRedisTemplate redisTemplate,
        MeterRegistry meterRegistry,
        @Value("${redis.key-prefix}") String redisKeyPrefix
    ) {
        if (!StringUtils.hasText(redisKeyPrefix)) {
            throw new IllegalArgumentException("Redis key prefix must not be blank");
        }
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        String normalizedPrefix = redisKeyPrefix.trim();
        this.keyPrefix = (normalizedPrefix.endsWith(":")
            ? normalizedPrefix
            : normalizedPrefix + ":") + "explore:list:" + KEY_VERSION + ":";
        // 같은 조건은 늘 같은 키가 되도록 필드·맵 순서를 고정한다.
        this.objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    }

    /**
     * @param kind      목록 종류(event, place). 키와 지표 태그에 들어간다.
     * @param condition 결과를 정하는 조건 전부. 하나라도 빠지면 다른 조건의 결과를 돌려준다.
     */
    public <T> Optional<CachedPage<T>> read(String kind, Object condition, Class<T> itemType) {
        try {
            String value = redisTemplate.opsForValue().get(key(kind, condition));
            if (value == null) {
                count(kind, "miss");
                return Optional.empty();
            }
            JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(CachedPage.class, itemType);
            CachedPage<T> page = objectMapper.readValue(value, type);
            count(kind, "hit");
            return Optional.of(page);
        } catch (RuntimeException | JsonProcessingException exception) {
            count(kind, "error");
            log.warn("Failed to read the Explore list cache. kind={}", kind, exception);
            return Optional.empty();
        }
    }

    public <T> void write(String kind, Object condition, CachedPage<T> page) {
        try {
            redisTemplate.opsForValue().set(
                key(kind, condition),
                objectMapper.writeValueAsString(page),
                TTL
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            count(kind, "error");
            log.warn("Failed to write the Explore list cache. kind={}", kind, exception);
        }
    }

    String key(String kind, Object condition) throws JsonProcessingException {
        return keyPrefix + kind + ":" + sha256(objectMapper.writeValueAsString(condition));
    }

    private void count(String kind, String result) {
        meterRegistry.counter(METRIC_NAME, "kind", kind, "result", result).increment();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** 첫 페이지 목록과 전체 개수. 페이지 번호·다음 페이지 여부는 호출부가 다시 계산한다. */
    public record CachedPage<T>(List<T> content, long totalElements) {
    }
}
