package me.nawa.explore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.common.i18n.SupportedLanguagePolicy;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceActivityResponse;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceListResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.mapper.ExploreItemLikeMapper;
import me.nawa.explore.mapper.PlaceMapper;
import me.nawa.explore.service.ExploreListCache.CachedPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTS = Set.of("NEWEST", "POPULAR");
    private static final Set<String> PLACE_KINDS = Set.of(
        "RESTAURANT", "CAFE", "MARKET", "BEAUTY", "ETC"
    );
    private static final String CACHE_KIND = "place";

    private final PlaceMapper placeMapper;
    private final ExploreViewCountRecorder viewCountRecorder;
    private final ExploreListCache listCache;
    private final ExploreItemLikeMapper likeMapper;

    @Transactional(readOnly = true)
    public PlaceListResponse searchPlaces(
        PlaceSearchRequest request,
        Long memberId
    ) {
        normalizeAndValidate(request);
        validateSavedOnly(request, memberId);
        int offset = calculateOffset(request);

        if (isSharedFirstPage(request)) {
            CachedPage<PlaceSummaryResponse> page = readSharedFirstPage(request);
            markSaved(page.content(), memberId);
            return createListResponse(page.content(), page.totalElements(), request);
        }

        List<PlaceSummaryResponse> content = placeMapper.searchPlaces(
            request, offset, request.getSize(), memberId
        );
        normalizeSummaries(content);
        long total = placeMapper.countPlaces(request, memberId);
        return createListResponse(content, total, request);
    }

    /** EventService.isSharedFirstPage와 같은 기준이다(#575). */
    private boolean isSharedFirstPage(PlaceSearchRequest request) {
        return request.getPage() == 0
            && request.getKeyword() == null
            && !Boolean.TRUE.equals(request.getSavedOnly());
    }

    /**
     * 키는 정규화를 마친 요청 전체다. EventService.readSharedFirstPage와 같은 이유로 필드를 골라
     * 담지 않는다. Place 목록은 기준일에 따라 달라지지 않는다.
     *
     * <p>정규화한 값을 담아 꺼낸 뒤 다시 정규화하지 않는다.
     */
    private CachedPage<PlaceSummaryResponse> readSharedFirstPage(PlaceSearchRequest request) {
        return listCache.read(CACHE_KIND, request, PlaceSummaryResponse.class)
            .orElseGet(() -> {
                List<PlaceSummaryResponse> content = placeMapper.searchPlaces(
                    request, 0, request.getSize(), null
                );
                normalizeSummaries(content);
                CachedPage<PlaceSummaryResponse> page = new CachedPage<>(
                    content,
                    placeMapper.countPlaces(request, null)
                );
                listCache.write(CACHE_KIND, request, page);
                return page;
            });
    }

    private void markSaved(List<PlaceSummaryResponse> content, Long memberId) {
        if (memberId == null || content.isEmpty()) {
            return;
        }
        Set<Long> savedIds = new HashSet<>(likeMapper.findSavedItemIds(
            memberId,
            content.stream().map(PlaceSummaryResponse::getItemId).toList()
        ));
        content.forEach(place -> place.setSaved(savedIds.contains(place.getItemId())));
    }

    /**
     * Place 상세를 읽는다.
     *
     * EventService.getEventDetail과 같다. 조회수는 여기서 세지 않고 호출부가 이 메서드를
     * 마친 뒤 {@link #recordPlaceView(Long)}를 부른다.
     */
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlaceDetail(
        Long placeId,
        String language,
        Long memberId
    ) {
        if (placeId == null || placeId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        String normalizedLanguage = SupportedLanguagePolicy.normalize(language);
        PlaceDetailResponse place = placeMapper.findPlaceDetail(
            placeId, normalizedLanguage, memberId
        );
        if (place == null) {
            throw new BusinessException(ExploreErrorCode.PLACE_NOT_FOUND);
        }
        List<PlaceActivityResponse> activities = placeMapper.findPlaceActivities(
            placeId, normalizedLanguage
        );
        place.setActivities(activities == null ? List.of() : activities);
        place.setPlaceKind(normalizePlaceKind(place.getPlaceKind()));
        place.setImageUrls(normalizeJsonKeys(place.getImageUrls()));
        place.setOpeningHours(normalizeJsonKeys(place.getOpeningHours()));
        place.setClosedDays(normalizeJsonKeys(place.getClosedDays()));

        return place;
    }

    /**
     * 조회수 집계가 실패해도 상세 화면은 열려야 한다. 삼키되 로그는 남긴다.
     *
     * EventService.recordEventView와 같은 이유로 **읽기 트랜잭션 밖에서** 부른다.
     */
    public void recordPlaceView(Long placeId) {
        try {
            viewCountRecorder.recordPlaceView(placeId);
        } catch (RuntimeException exception) {
            log.warn("Failed to record the Place view count. placeId={}", placeId, exception);
        }
    }

    private void normalizeAndValidate(PlaceSearchRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (request.getPage() < 0) request.setPage(DEFAULT_PAGE);
        if (request.getSize() <= 0) request.setSize(DEFAULT_SIZE);
        if (request.getSize() > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        request.setPlaceKinds(normalizeUppercaseValues(request.getPlaceKinds()));
        if (request.getPlaceKinds().stream().anyMatch(
            value -> !PLACE_KINDS.contains(value)
        )) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        request.setRegion1(normalizeTextValues(request.getRegion1()));
        request.setRegion2(normalizeTextValues(request.getRegion2()));
        request.setRegion3(normalizeTextValues(request.getRegion3()));
        request.setKnownRegion2Values(
            ExploreRegionPolicy.knownRegion2Values(request.getRegion1())
        );
        request.setKeyword(StringUtils.hasText(request.getKeyword())
            ? request.getKeyword().trim() : null);
        String sort = StringUtils.hasText(request.getSort())
            ? request.getSort().trim().toUpperCase(Locale.ROOT) : "POPULAR";
        // 개명 전 프론트 번들(PWA 캐시)이 보낼 수 있는 레거시 값을 수용한다.
        if ("LATEST".equals(sort)) {
            sort = "NEWEST";
        }
        if (!SORTS.contains(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        request.setSort(sort);
        request.setLanguage(SupportedLanguagePolicy.normalize(request.getLanguage()));
    }

    private void validateSavedOnly(PlaceSearchRequest request, Long memberId) {
        if (Boolean.TRUE.equals(request.getSavedOnly())
            && (memberId == null || memberId <= 0)) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private int calculateOffset(PlaceSearchRequest request) {
        long offset = (long) request.getPage() * request.getSize();
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return (int) offset;
    }

    private PlaceListResponse createListResponse(
        List<PlaceSummaryResponse> content,
        long total,
        PlaceSearchRequest request
    ) {
        int totalPages = total == 0 ? 0
            : (int) ((total + request.getSize() - 1) / request.getSize());
        return new PlaceListResponse(
            content,
            request.getPage(),
            request.getSize(),
            total,
            totalPages,
            request.getPage() + 1 < totalPages
        );
    }

    private void normalizeSummaries(List<PlaceSummaryResponse> places) {
        places.forEach(place -> {
            place.setPlaceKind(normalizePlaceKind(place.getPlaceKind()));
            place.setImageUrls(normalizeJsonKeys(place.getImageUrls()));
        });
    }

    private String normalizePlaceKind(String value) {
        if (!StringUtils.hasText(value)) return "ETC";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (PLACE_KINDS.contains(normalized)) return normalized;
        return switch (value.trim()) {
            case "관광식당", "서양식", "일식", "중식", "기타외국식",
                "김밥 분식", "분식", "퓨전음식", "이동음식", "모범음식점",
                "바/펍" -> "RESTAURANT";
            case "카페", "찻집", "제과", "기타음료점" -> "CAFE";
            case "상설시장", "비상설시장", "복합쇼핑몰", "백화점",
                "관광기념품/특산물판매점", "아웃렛", "공방/공예품점" -> "MARKET";
            case "뷰티매장" -> "BEAUTY";
            default -> "ETC";
        };
    }

    private List<String> normalizeUppercaseValues(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(StringUtils::hasText)
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .distinct().toList();
    }

    private List<String> normalizeTextValues(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(StringUtils::hasText)
            .map(String::trim).distinct().toList();
    }

    private JsonNode normalizeJsonKeys(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) return value;
        if (value.isArray()) {
            ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> normalized.add(normalizeJsonKeys(item)));
            return normalized;
        }
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        Iterator<Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Entry<String, JsonNode> field = fields.next();
            normalized.set(toCamelCase(field.getKey()), normalizeJsonKeys(field.getValue()));
        }
        return normalized;
    }

    private String toCamelCase(String name) {
        StringBuilder result = new StringBuilder();
        boolean uppercaseNext = false;
        for (char character : name.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;
            } else {
                result.append(uppercaseNext
                    ? Character.toUpperCase(character) : character);
                uppercaseNext = false;
            }
        }
        return result.toString();
    }
}
