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
        // 목록과 개수가 같은 날을 봐야 페이지네이션이 어긋나지 않는다. 한 번 읽어 둘에 넘긴다.
        LocalDate today = LocalDate.now();
        List<EventSummaryResponse> content = eventMapper.searchEvents(
            request,
            offset,
            memberId,
            today
        );
        long totalElements = eventMapper.countEvents(request, memberId, today);
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

        request.setLanguage(ExploreLanguagePolicy.normalize(request.getLanguage()));
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
     * 조회수는 여기서 세지 않는다. 읽기 트랜잭션 안에서 집계하면 커넥션을 하나 더 잡으므로
     * 호출부가 이 메서드를 마친 뒤 {@link #recordEventView(Long)}를 부른다.
     */
    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(
        Long eventId,
        String language,
        Long memberId
    ) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        String normalizedLanguage = ExploreLanguagePolicy.normalize(language);

        EventDetailResponse event = eventMapper.findEventDetail(
            eventId,
            normalizedLanguage,
            memberId,
            LocalDate.now()
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

        return event;
    }

    /**
     * 조회수를 쌓되 실패는 삼킨다.
     *
     * 조회수는 부가 정보다. 집계가 실패했다고 상세 화면이 안 열리면 손해가 더 크다.
     * 조용히 넘기지 않고 로그는 남겨서 집계가 멈춘 것을 알 수 있게 한다.
     *
     * **읽기 트랜잭션이 끝난 뒤에 부른다.** 그 안에서 부르면 REQUIRES_NEW가 바깥
     * 트랜잭션을 중단시키되 커넥션은 풀에 돌려주지 않아, 상세 요청 하나가 커넥션을 두 개
     * 잡는다. 풀이 10개라 상세 요청 10개가 동시에 들어오면 서로의 두 번째 커넥션을
     * 기다리다 connectionTimeout(30초)까지 아무도 진행하지 못한다. 그 예외는 여기서
     * 삼켜지므로 500이 아니라 **상세 API가 통째로 30초씩 늦어지는 형태**로만 드러난다.
     */
    public void recordEventView(Long eventId) {
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
