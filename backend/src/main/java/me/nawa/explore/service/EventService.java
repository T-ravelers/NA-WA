package me.nawa.explore.service;

import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.mapper.EventMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import me.nawa.explore.dto.response.EventActivityResponse;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.exception.ExploreErrorCode;

import java.util.List;
import java.util.Locale;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class EventService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final EventMapper eventMapper;

    @Transactional(readOnly = true)
    public EventListResponse searchEvents(EventSearchRequest request) {
        normalizeAndValidate(request);

        long offsetLong = (long) request.getPage() * request.getSize();
        if (offsetLong > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        int offset = (int) offsetLong;
        List<EventSummaryResponse> content = eventMapper.searchEvents(request, offset);
        long totalElements = eventMapper.countEvents(request);
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

        String sort = StringUtils.hasText(request.getSort())
            ? request.getSort().toUpperCase(Locale.ROOT)
            : "LATEST";
        if (!"LATEST".equals(sort) && !"POPULAR".equals(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        request.setSort(sort);

        if (!StringUtils.hasText(request.getLanguage())) {
            request.setLanguage("ko");
        }
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) ((totalElements + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(Long eventId, String language) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        String normalizedLanguage = StringUtils.hasText(language)
            ? language.toLowerCase(Locale.ROOT)
            : "ko";

        EventDetailResponse event = eventMapper.findEventDetail(
            eventId,
            normalizedLanguage
        );

        if (event == null) {
            throw new BusinessException(ExploreErrorCode.EVENT_NOT_FOUND);
        }

        List<EventActivityResponse> activities = eventMapper.findEventActivities(
            eventId,
            normalizedLanguage
        );

        return EventDetailResponse.builder()
            .eventId(event.getEventId())
            .eventType(event.getEventType())
            .title(event.getTitle())
            .subtitle(event.getSubtitle())
            .description(event.getDescription())
            .programText(event.getProgramText())
            .thumbnailUrl(event.getThumbnailUrl())
            .status(event.getStatus())
            .isPermanent(event.getIsPermanent())
            .startDate(event.getStartDate())
            .endDate(event.getEndDate())
            .operatingHours(event.getOperatingHours())
            .openDays(event.getOpenDays())
            .openWeekend(event.getOpenWeekend())
            .opensLate(event.getOpensLate())
            .venueName(event.getVenueName())
            .region1(event.getRegion1())
            .region2(event.getRegion2())
            .region3(event.getRegion3())
            .addressRoad(event.getAddressRoad())
            .latitude(event.getLatitude())
            .longitude(event.getLongitude())
            .hasPhotoZone(event.getHasPhotoZone())
            .isExperience(event.getIsExperience())
            .ageLimit(event.getAgeLimit())
            .isFree(event.getIsFree())
            .priceText(event.getPriceText())
            .hasBenefit(event.getHasBenefit())
            .reservable(event.getReservable())
            .activities(activities == null ? List.of() : activities)
            .build();
    }
}
