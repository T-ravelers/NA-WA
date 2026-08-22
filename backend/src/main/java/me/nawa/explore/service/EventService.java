package me.nawa.explore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
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
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventActivityResponse;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.mapper.EventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORTS = Set.of(
        "NEWEST",
        "POPULAR",
        "ENDING_SOON"
    );
    private static final Set<String> EVENT_KINDS = Set.of(
        "POPUP",
        "CONCERT",
        "ETC",
        "FESTIVAL",
        "EXHIBITION"
    );
    private final EventMapper eventMapper;
    private final ExploreViewCountRecorder viewCountRecorder;

    @Transactional(readOnly = true)
    public EventListResponse searchEvents(
        EventSearchRequest request,
        Long memberId
    ) {
        normalizeAndValidate(request);

        if (Boolean.TRUE.equals(request.getSavedOnly())
            && (memberId == null || memberId <= 0)) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }

        long offsetLong = (long) request.getPage() * request.getSize();
        if (offsetLong > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        int offset = (int) offsetLong;
        List<EventSummaryResponse> content = eventMapper.searchEvents(
            request,
            offset,
            memberId
        );
        long totalElements = eventMapper.countEvents(request, memberId);
        int totalPages = calculateTotalPages(totalElements, request.getSize());

        return new EventListResponse(
            content,
            request.getPage(),
            request.getSize(),
            totalElements,
            totalPages,
            request.getPage() + 1 < totalPages
        );
    }

    private void normalizeAndValidate(EventSearchRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (request.getPage() < 0) {
            request.setPage(DEFAULT_PAGE);
        }

        if (request.getSize() <= 0) {
            request.setSize(DEFAULT_SIZE);
        }

        if (request.getSize() > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        request.setEventKinds(normalizeUppercaseValues(request.getEventKinds()));
        if (request.getEventKinds().stream()
            .anyMatch(eventKind -> !EVENT_KINDS.contains(eventKind))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        request.setRegion1(normalizeTextValues(request.getRegion1()));
        request.setRegion2(normalizeTextValues(request.getRegion2()));
        request.setRegion3(normalizeTextValues(request.getRegion3()));
        request.setKnownRegion2Values(
            ExploreRegionPolicy.knownRegion2Values(request.getRegion1())
        );

        validateDateRange(request.getStartDate(), request.getEndDate());

        String sort = StringUtils.hasText(request.getSort())
            ? request.getSort().toUpperCase(Locale.ROOT)
            : "NEWEST";
        // 개명 전 프론트 번들(PWA 캐시)이 보낼 수 있는 레거시 값을 수용한다.
        if ("LATEST".equals(sort)) {
            sort = "NEWEST";
        }
        if (!SORTS.contains(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        request.setSort(sort);

        request.setLanguage(StringUtils.hasText(request.getLanguage())
            ? request.getLanguage().trim().toLowerCase(Locale.ROOT) : "en");
    }

    private List<String> normalizeUppercaseValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private List<String> normalizeTextValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) ((totalElements + size - 1) / size);
    }

    /**
     * Event 상세를 읽는다.
     *
     * {@code countView}는 사용자가 상세 화면을 연 요청에서만 참이다. 약속 생성 폼처럼
     * 같은 API로 위치만 읽어 가는 호출까지 세면 조회수가 부풀기 때문에, 셀지 말지를
     * 요청이 알려준다. 기본값은 거짓이라 새 호출부가 모르고 조회수를 올리는 일이 없다.
     */
    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(
        Long eventId,
        String language,
        Long memberId,
        boolean countView
    ) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        String normalizedLanguage = StringUtils.hasText(language)
            ? language.toLowerCase(Locale.ROOT)
            // TODO(국제화 후속 이슈): 크롤링 원본 국제화 전까지 en을 기본 fallback으로 사용한다.
            : "en";

        EventDetailResponse event = eventMapper.findEventDetail(
            eventId,
            normalizedLanguage,
            memberId
        );

        if (event == null) {
            throw new BusinessException(ExploreErrorCode.EVENT_NOT_FOUND);
        }

        List<EventActivityResponse> activities = eventMapper.findEventActivities(
            eventId,
            normalizedLanguage
        );

        event.setActivities(activities == null ? List.of() : activities);
        normalizeJsonResponse(event);
        event.setReservationUrl(resolveReservationUrl(event));

        if (countView) {
            recordView(eventId);
        }

        return event;
    }

    /**
     * 조회수를 쌓되 실패는 삼킨다.
     *
     * 조회수는 부가 정보다. 집계가 실패했다고 상세 화면이 안 열리면 손해가 더 크다.
     * 조용히 넘기지 않고 로그는 남겨서 집계가 멈춘 것을 알 수 있게 한다.
     */
    private void recordView(Long eventId) {
        try {
            viewCountRecorder.recordEventView(eventId);
        } catch (RuntimeException exception) {
            log.warn("Failed to record the Event view count. eventId={}", eventId, exception);
        }
    }

    private void normalizeJsonResponse(EventDetailResponse event) {
        event.setImageUrls(normalizeJsonKeys(event.getImageUrls()));
        event.setLinks(normalizeJsonKeys(event.getLinks()));
        event.setPreReservation(normalizeJsonKeys(event.getPreReservation()));
        event.setOperatingHours(normalizeOperatingHours(
            event.getOperatingHours()
        ));
        event.setOpenDays(normalizeJsonKeys(event.getOpenDays()));
    }

    private JsonNode normalizeOperatingHours(JsonNode operatingHours) {
        if (operatingHours == null || operatingHours.isNull()) {
            return operatingHours;
        }

        if (operatingHours.isTextual()) {
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            normalized.put("raw", operatingHours.asText());
            return normalized;
        }

        return normalizeJsonKeys(operatingHours);
    }

    private JsonNode normalizeJsonKeys(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }

        if (value.isArray()) {
            ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> normalized.add(normalizeJsonKeys(item)));
            return normalized;
        }

        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        Iterator<Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Entry<String, JsonNode> field = fields.next();
            normalized.set(
                toCamelCase(field.getKey()),
                normalizeJsonKeys(field.getValue())
            );
        }
        return normalized;
    }

    private String toCamelCase(String fieldName) {
        StringBuilder result = new StringBuilder();
        boolean uppercaseNext = false;
        for (char character : fieldName.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;
                continue;
            }
            result.append(
                uppercaseNext
                    ? Character.toUpperCase(character)
                    : character
            );
            uppercaseNext = false;
        }
        return result.toString();
    }

    private String resolveReservationUrl(EventDetailResponse event) {
        JsonNode preReservation = event.getPreReservation();
        if (preReservation != null
            && preReservation.path("has").asBoolean(false)) {
            String preReservationLink = textValue(preReservation, "link");
            if (StringUtils.hasText(preReservationLink)) {
                return preReservationLink;
            }
        }

        if (StringUtils.hasText(event.getReservationUrl())) {
            return event.getReservationUrl();
        }

        JsonNode links = event.getLinks();
        if (links != null) {
            String reservationLink = textValue(links, "reservationUrl");
            if (!StringUtils.hasText(reservationLink)) {
                reservationLink = textValue(links, "reservation_url");
            }
            if (StringUtils.hasText(reservationLink)) {
                return reservationLink;
            }
        }

        return null;
    }

    private String textValue(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }
}
