package me.nawa.explore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceActivityResponse;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceListResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.mapper.PlaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTS = Set.of("LATEST", "POPULAR");
    private static final Set<String> PLACE_KINDS = Set.of(
        "RESTAURANT", "CAFE", "MARKET", "BEAUTY", "ETC"
    );
    private final PlaceMapper placeMapper;

    @Transactional(readOnly = true)
    public PlaceListResponse searchPlaces(
        PlaceSearchRequest request,
        Long memberId
    ) {
        normalizeAndValidate(request);
        validateSavedOnly(request, memberId);
        int offset = calculateOffset(request);

        List<PlaceSummaryResponse> content = placeMapper.searchPlaces(
            request, offset, request.getSize(), memberId
        );
        normalizeSummaries(content);
        long total = placeMapper.countPlaces(request, memberId);
        return createListResponse(content, total, request);
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlaceDetail(Long placeId, String language) {
        if (placeId == null || placeId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        String normalizedLanguage = StringUtils.hasText(language)
            ? language.toLowerCase(Locale.ROOT)
            : "en";
        PlaceDetailResponse place = placeMapper.findPlaceDetail(
            placeId, normalizedLanguage
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
            ? request.getSort().trim().toUpperCase(Locale.ROOT) : "LATEST";
        if (!SORTS.contains(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        request.setSort(sort);
        request.setLanguage(StringUtils.hasText(request.getLanguage())
            ? request.getLanguage().trim().toLowerCase(Locale.ROOT) : "en");
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
