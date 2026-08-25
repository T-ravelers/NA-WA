package me.nawa.journey.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.appointment.service.AppointmentService;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.common.i18n.SupportedLanguagePolicy;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyExploreItem;
import me.nawa.journey.domain.JourneyItem;
import me.nawa.journey.domain.JourneyTimelineItem;
import me.nawa.journey.domain.TripRegion;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.request.JourneyItemCreateRequest;
import me.nawa.journey.dto.request.JourneyRegionRequest;
import me.nawa.journey.dto.request.JourneyUpdateRequest;
import me.nawa.journey.dto.response.JourneyItemExistsResponse;
import me.nawa.journey.dto.response.JourneyItemResponse;
import me.nawa.journey.dto.response.JourneyRegionResponse;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.dto.response.JourneyTimelineAppointmentResponse;
import me.nawa.journey.dto.response.JourneyTimelineDayResponse;
import me.nawa.journey.dto.response.JourneyTimelineEventDetailResponse;
import me.nawa.journey.dto.response.JourneyTimelineExploreItemResponse;
import me.nawa.journey.dto.response.JourneyTimelineItemResponse;
import me.nawa.journey.dto.response.JourneyTimelineLocationResponse;
import me.nawa.journey.dto.response.JourneyTimelinePlaceDetailResponse;
import me.nawa.journey.dto.response.JourneyTimelineResponse;
import me.nawa.journey.dto.response.JourneySummaryResponse;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.mapper.JourneyMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JourneyService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_COMPANION_PREFERENCE_LENGTH = 30;
    private static final int MAX_REGION_CODE_LENGTH = 30;
    private static final int MAX_REGION_NAME_LENGTH = 100;
    private static final int MAX_BUDGET_INTEGER_DIGITS = 15;
    private static final int MAX_BUDGET_SCALE = 4;

    private final JourneyMapper journeyMapper;
    private final AppointmentService appointmentService;

    @Transactional
    public JourneyResponse createJourney(
        Long memberId,
        JourneyCreateRequest request
    ) {
        validateMemberId(memberId);
        validateRequest(request);

        Journey journey = Journey.builder()
            .memberId(memberId)
            .title(request.getTitle().trim())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .budgetAmount(request.getBudgetAmount())
            .companionPreference(normalizeOptional(
                request.getCompanionPreference()
            ))
            .build();

        journeyMapper.insertJourney(journey);

        List<TripRegion> regions = createRegions(
            journey.getTripId(),
            request.getRegions()
        );
        if (!regions.isEmpty()) {
            journeyMapper.insertRegions(regions);
        }

        return toResponse(journey, regions);
    }

    @Transactional
    public JourneyResponse updateJourney(
        Long memberId,
        Long tripId,
        JourneyUpdateRequest request
    ) {
        validateMemberId(memberId);
        validateUpdateRequest(request);

        Journey journey = findOwnedJourneyForUpdate(memberId, tripId);
        if (journeyMapper.hasJourneyItemsOutsideRange(
            tripId,
            request.getStartDate(),
            request.getEndDate()
        )) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_DATE_RANGE_CONFLICT
            );
        }

        journey.setTitle(request.getTitle().trim());
        journey.setStartDate(request.getStartDate());
        journey.setEndDate(request.getEndDate());
        journey.setBudgetAmount(request.getBudgetAmount());
        journey.setCompanionPreference(normalizeOptional(
            request.getCompanionPreference()
        ));

        if (journeyMapper.updateJourney(journey) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        journeyMapper.softDeleteRegionsByTripId(tripId);
        List<TripRegion> regions = createRegions(tripId, request.getRegions());
        if (!regions.isEmpty()) {
            journeyMapper.insertRegions(regions);
        }

        return toResponse(journey, regions);
    }

    @Transactional
    public JourneyItemResponse addJourneyItem(
        Long memberId,
        Long tripId,
        JourneyItemCreateRequest request
    ) {
        Journey journey = findOwnedJourneyForUpdate(memberId, tripId);
        validateJourneyItemRequest(request);

        JourneyExploreItem exploreItem = journeyMapper
            .findAvailableExploreItemById(request.getItemId());
        if (exploreItem == null) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_NOT_FOUND
            );
        }
        if (!isSupportedItemType(exploreItem.getItemType())) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_TYPE_UNSUPPORTED
            );
        }
        if (request.getVisitDate().isBefore(journey.getStartDate())
            || request.getVisitDate().isAfter(journey.getEndDate())) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_DATE_OUT_OF_RANGE
            );
        }
        validateVisitDateWithinItemPeriod(exploreItem, request.getVisitDate());
        if (journeyMapper.existsJourneyItem(
            tripId,
            request.getItemId(),
            request.getVisitDate()
        )) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_DUPLICATE
            );
        }

        JourneyItem journeyItem = JourneyItem.builder()
            .tripId(tripId)
            .itemId(request.getItemId())
            .itemType(exploreItem.getItemType())
            .visitDate(request.getVisitDate())
            .tripItemStatus("ADDED")
            .displayOrder(normalizeDisplayOrder(request.getDisplayOrder()))
            .note(normalizeOptional(request.getNote()))
            .appointmentId(null)
            .confirmedAt(null)
            .build();

        try {
            journeyMapper.insertJourneyItem(journeyItem);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_DUPLICATE,
                exception
            );
        }

        if (journeyItem.getTripItemId() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        JourneyItem createdItem = journeyMapper.findJourneyItemById(
            journeyItem.getTripItemId()
        );
        if (createdItem == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        return toJourneyItemResponse(createdItem);
    }

    @Transactional
    public void deleteJourneyItem(
        Long memberId,
        Long tripId,
        Long tripItemId
    ) {
        validateTripItemId(tripItemId);
        findOwnedJourneyForUpdate(memberId, tripId);

        JourneyItem journeyItem = journeyMapper.findJourneyItemForUpdate(
            tripId,
            tripItemId,
            memberId
        );
        if (journeyItem == null) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_SCHEDULE_NOT_FOUND
            );
        }

        leaveConfirmedAppointment(memberId, journeyItem);
        if (journeyMapper.softDeleteJourneyItem(tripId, tripItemId) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void deleteJourney(Long memberId, Long tripId) {
        findOwnedJourneyForUpdate(memberId, tripId);

        List<JourneyItem> confirmedItems = journeyMapper
            .findConfirmedJourneyItemsForUpdate(tripId, memberId);
        List<JourneyItem> items = confirmedItems == null
            ? List.of()
            : confirmedItems;

        if (items.stream().anyMatch(item -> isAppointmentHost(
            memberId,
            item
        ))) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_APPOINTMENT_HOST_DELETE_CONFLICT
            );
        }

        for (JourneyItem item : items) {
            leaveConfirmedAppointment(memberId, item);
        }

        journeyMapper.softDeleteJourneyItemsByTripId(tripId);
        journeyMapper.softDeleteRegionsByTripId(tripId);
        journeyMapper.softDeleteReportsByTripId(tripId);
        journeyMapper.softDeleteExpenseLinksByTripId(tripId);
        if (journeyMapper.softDeleteJourney(tripId) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 약속 생성 시 여정 항목 날짜를 고르는 시점에, 그 조합이 이미 있는지 미리
    // 확인하기 위한 조회다. 실제 저장은 하지 않는다 — 최종 확정은
    // AppointmentService.createAppointment가 한다.
    //
    // 두 값을 나눠서 준다. exists는 여정 담기(POST items)가 거절되는 조건이고,
    // appointmentLinked는 약속 생성이 거절되는 조건이다. 담아만 둔 자리는 약속
    // 항목으로 승격되므로 약속 생성을 막지 않는다 — 둘을 한 값으로 합치면 담아 둔
    // 장소로는 약속을 만들 수 없게 된다.
    @Transactional(readOnly = true)
    public JourneyItemExistsResponse existsJourneyItem(
        Long memberId,
        Long tripId,
        Long itemId,
        LocalDate visitDate
    ) {
        findOwnedJourney(memberId, tripId);
        if (itemId == null || itemId <= 0 || visitDate == null) {
            throw new BusinessException(JourneyErrorCode.INVALID_JOURNEY_INPUT);
        }
        return JourneyItemExistsResponse.builder()
            .exists(journeyMapper.existsJourneyItem(tripId, itemId, visitDate))
            .appointmentLinked(journeyMapper.existsAppointmentJourneyItem(
                tripId,
                itemId,
                visitDate
            ))
            .build();
    }

    @Transactional(readOnly = true)
    public JourneyResponse getJourney(Long memberId, Long tripId) {
        Journey journey = findOwnedJourney(memberId, tripId);

        List<TripRegion> regions = journeyMapper.findRegionsByTripId(tripId);
        return toResponse(
            journey,
            regions == null ? List.of() : regions
        );
    }

    @Transactional(readOnly = true)
    public JourneyTimelineResponse getTimeline(
        Long memberId,
        Long tripId,
        String language
    ) {
        findOwnedJourney(memberId, tripId);
        String normalizedLanguage = SupportedLanguagePolicy.normalize(language);

        List<JourneyTimelineItem> mappedItems =
            journeyMapper.findTimelineItemsByTripId(
                tripId,
                normalizedLanguage
            );
        if (mappedItems == null || mappedItems.isEmpty()) {
            return JourneyTimelineResponse.builder()
                .tripId(tripId)
                .timeline(List.of())
                .build();
        }

        List<JourneyTimelineItem> sortedItems = new ArrayList<>(mappedItems);
        sortedItems.sort(
            Comparator.comparing(JourneyTimelineItem::getVisitDate)
                .thenComparing(JourneyTimelineItem::getDisplayOrder)
                .thenComparing(JourneyTimelineItem::getTripItemId)
        );

        Map<LocalDate, List<JourneyTimelineItemResponse>> grouped =
            new LinkedHashMap<>();
        for (JourneyTimelineItem item : sortedItems) {
            grouped.computeIfAbsent(
                item.getVisitDate(),
                ignored -> new ArrayList<>()
            ).add(toTimelineItemResponse(item));
        }

        List<JourneyTimelineDayResponse> timeline = grouped.entrySet().stream()
            .map(entry -> JourneyTimelineDayResponse.builder()
                .visitDate(entry.getKey())
                .items(List.copyOf(entry.getValue()))
                .build())
            .toList();

        return JourneyTimelineResponse.builder()
            .tripId(tripId)
            .timeline(timeline)
            .build();
    }

    private Journey findOwnedJourney(Long memberId, Long tripId) {
        validateMemberId(memberId);
        if (tripId == null || tripId <= 0) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }

        Journey journey = journeyMapper.findJourneyById(tripId);
        if (journey == null) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_NOT_FOUND);
        }
        if (!memberId.equals(journey.getMemberId())) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_FORBIDDEN);
        }
        return journey;
    }

    private Journey findOwnedJourneyForUpdate(Long memberId, Long tripId) {
        validateMemberId(memberId);
        validateTripId(tripId);

        Journey journey = journeyMapper.findJourneyByIdForUpdate(tripId);
        validateJourneyOwner(journey, memberId);
        return journey;
    }

    private JourneyTimelineItemResponse toTimelineItemResponse(
        JourneyTimelineItem item
    ) {
        JourneyTimelineExploreItemResponse exploreItem =
            JourneyTimelineExploreItemResponse.builder()
                .itemType(item.getItemType())
                .title(item.getTitle())
                .thumbnailUrl(item.getThumbnailUrl())
                .imageUrls(toImageUrls(item.getImageUrls()))
                .location(JourneyTimelineLocationResponse.builder()
                    .region1(item.getRegion1())
                    .region2(item.getRegion2())
                    .region3(item.getRegion3())
                    .addressRoad(item.getAddressRoad())
                    .addressDetail(item.getAddressDetail())
                    .latitude(item.getLatitude())
                    .longitude(item.getLongitude())
                    .build())
                .build();

        return JourneyTimelineItemResponse.builder()
            .tripItemId(item.getTripItemId())
            .itemId(item.getItemId())
            .status(item.getStatus())
            .displayOrder(item.getDisplayOrder())
            .note(item.getNote())
            .exploreItem(exploreItem)
            .eventDetail(toEventDetail(item))
            .placeDetail(toPlaceDetail(item))
            .appointment(toAppointment(item))
            .build();
    }

    private JourneyTimelineEventDetailResponse toEventDetail(
        JourneyTimelineItem item
    ) {
        if (!"EVENT".equals(item.getItemType())) {
            return null;
        }
        return JourneyTimelineEventDetailResponse.builder()
            .eventKind(item.getEventKind())
            .startDate(item.getEventStartDate())
            .endDate(item.getEventEndDate())
            .organizer(item.getOrganizer())
            .reservationUrl(item.getEventReservationUrl())
            .venueName(item.getVenueName())
            .build();
    }

    private JourneyTimelinePlaceDetailResponse toPlaceDetail(
        JourneyTimelineItem item
    ) {
        if (!"PLACE".equals(item.getItemType())) {
            return null;
        }
        return JourneyTimelinePlaceDetailResponse.builder()
            .placeKind(item.getPlaceKind())
            .addressDetail(item.getAddressDetail())
            .menuSummary(item.getMenuSummary())
            .isActive(item.getPlaceActive())
            .build();
    }

    private JourneyTimelineAppointmentResponse toAppointment(
        JourneyTimelineItem item
    ) {
        if (item.getAppointmentId() == null) {
            return null;
        }
        return JourneyTimelineAppointmentResponse.builder()
            .appointmentId(item.getAppointmentId())
            .activityStartAt(item.getActivityStartAt())
            .activityEndAt(item.getActivityEndAt())
            .appointmentStatus(item.getAppointmentStatus())
            .build();
    }

    private List<String> toImageUrls(JsonNode imageUrls) {
        if (imageUrls == null || !imageUrls.isArray()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        imageUrls.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                urls.add(value.asText());
            }
        });
        return List.copyOf(urls);
    }

    @Transactional(readOnly = true)
    public List<JourneySummaryResponse> getJourneys(Long memberId) {
        validateMemberId(memberId);

        return journeyMapper.findJourneysByMemberId(memberId).stream()
            .map(journey -> JourneySummaryResponse.builder()
                .tripId(journey.getTripId())
                .title(journey.getTitle())
                .startDate(journey.getStartDate())
                .endDate(journey.getEndDate())
                .eventCount(journey.getEventCount())
                .placeCount(journey.getPlaceCount())
                .coverImageUrl(journey.getCoverImageUrl())
                .build())
            .toList();
    }

    private void validateRequest(JourneyCreateRequest request) {
        if (request == null) {
            throw invalidJourneyInput();
        }
        validateJourneyFields(
            request.getTitle(),
            request.getStartDate(),
            request.getEndDate(),
            request.getBudgetAmount(),
            request.getCompanionPreference(),
            request.getRegions()
        );
    }

    private void validateUpdateRequest(JourneyUpdateRequest request) {
        if (request == null || request.getRegions() == null) {
            throw invalidJourneyInput();
        }
        validateJourneyFields(
            request.getTitle(),
            request.getStartDate(),
            request.getEndDate(),
            request.getBudgetAmount(),
            request.getCompanionPreference(),
            request.getRegions()
        );
    }

    private void validateJourneyFields(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budgetAmount,
        String companionPreference,
        List<JourneyRegionRequest> regions
    ) {
        if (isBlank(title)
            || title.trim().length() > MAX_TITLE_LENGTH
            || startDate == null
            || endDate == null
            || startDate.isAfter(endDate)
            || isInvalidBudget(budgetAmount)) {
            throw invalidJourneyInput();
        }

        String preference = normalizeOptional(companionPreference);
        if (preference != null
            && preference.length() > MAX_COMPANION_PREFERENCE_LENGTH) {
            throw invalidJourneyInput();
        }

        validateRegions(regions);
    }

    private void validateRegions(List<JourneyRegionRequest> regions) {
        if (regions == null) {
            return;
        }

        Set<String> regionCodes = new HashSet<>();
        for (JourneyRegionRequest region : regions) {
            if (region == null
                || isBlank(region.getRegionCode())
                || region.getRegionCode().trim().length()
                    > MAX_REGION_CODE_LENGTH
                || isBlank(region.getRegionName())
                || region.getRegionName().trim().length()
                    > MAX_REGION_NAME_LENGTH
                || region.getDisplayOrder() == null
                || region.getDisplayOrder() < 0
                || region.getDisplayOrder() > Short.MAX_VALUE
                || !regionCodes.add(
                    region.getRegionCode().trim().toUpperCase(Locale.ROOT)
                )) {
                throw new BusinessException(
                    JourneyErrorCode.INVALID_JOURNEY_INPUT
                );
            }
        }
    }

    private List<TripRegion> createRegions(
        Long tripId,
        List<JourneyRegionRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<TripRegion> regions = new ArrayList<>(requests.size());
        for (JourneyRegionRequest request : requests) {
            regions.add(TripRegion.builder()
                .tripId(tripId)
                .regionCode(request.getRegionCode().trim())
                .regionName(request.getRegionName().trim())
                .displayOrder(request.getDisplayOrder())
                .build());
        }
        regions.sort(
            Comparator.comparing(TripRegion::getDisplayOrder)
                .thenComparing(TripRegion::getRegionCode)
        );
        return regions;
    }

    private JourneyResponse toResponse(
        Journey journey,
        List<TripRegion> regions
    ) {
        List<JourneyRegionResponse> regionResponses = regions.stream()
            .map(region -> JourneyRegionResponse.builder()
                .regionCode(region.getRegionCode())
                .regionName(region.getRegionName())
                .displayOrder(region.getDisplayOrder())
                .build())
            .toList();

        return JourneyResponse.builder()
            .tripId(journey.getTripId())
            .title(journey.getTitle())
            .startDate(journey.getStartDate())
            .endDate(journey.getEndDate())
            .budgetAmount(journey.getBudgetAmount())
            .companionPreference(journey.getCompanionPreference())
            .regions(regionResponses)
            .build();
    }

    private JourneyItemResponse toJourneyItemResponse(JourneyItem journeyItem) {
        return JourneyItemResponse.builder()
            .tripItemId(journeyItem.getTripItemId())
            .journeyId(journeyItem.getTripId())
            .itemId(journeyItem.getItemId())
            .itemType(journeyItem.getItemType())
            .visitDate(journeyItem.getVisitDate())
            .tripItemStatus(journeyItem.getTripItemStatus())
            .displayOrder(journeyItem.getDisplayOrder())
            .note(journeyItem.getNote())
            .appointmentId(journeyItem.getAppointmentId())
            .confirmedAt(journeyItem.getConfirmedAt())
            .createdAt(journeyItem.getCreatedAt())
            .build();
    }

    private void validateJourneyItemRequest(
        JourneyItemCreateRequest request
    ) {
        if (request == null
            || request.getItemId() == null
            || request.getItemId() <= 0
            || request.getVisitDate() == null) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }

        Integer displayOrder = request.getDisplayOrder();
        if (displayOrder != null
            && (displayOrder < 0 || displayOrder > Short.MAX_VALUE)) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_DISPLAY_ORDER_INVALID
            );
        }

        String note = normalizeOptional(request.getNote());
        if (note != null && note.length() > 500) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }
    }

    /*
     * 방문 날짜가 항목 자체의 운영 기간 안인지 본다.
     *
     * 여정 기간 검사(JOURNEY-007)와 별개다. 여정 기간 안이어도 이벤트가 열리지 않는
     * 날일 수 있고, 지금까지는 프론트의 달력이 가려줬을 뿐이라 API를 직접 부르면
     * 그대로 담겼다.
     *
     * PLACE는 운영 기간이라는 개념 자체가 없어(place 테이블에 컬럼이 없다) 건너뛴다.
     * 그래서 EVENT일 때만 보고, 값이 비어 있으면 막지 않는다 — 없는 근거로 거절하면
     * 사용자는 고칠 방법이 없다.
     */
    // 규칙 자체는 JourneyExploreItem이 갖고 있다. 약속 생성도 같은 것을 쓴다.
    private void validateVisitDateWithinItemPeriod(
        JourneyExploreItem exploreItem,
        LocalDate visitDate
    ) {
        if (!exploreItem.coversVisitDate(visitDate)) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_ITEM_OUTSIDE_ITEM_PERIOD
            );
        }
    }

    private Integer normalizeDisplayOrder(Integer displayOrder) {
        return displayOrder == null ? 0 : displayOrder;
    }

    private boolean isSupportedItemType(String itemType) {
        return "EVENT".equals(itemType) || "PLACE".equals(itemType);
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }
    }

    private void validateTripId(Long tripId) {
        if (tripId == null || tripId <= 0) {
            throw invalidJourneyInput();
        }
    }

    private void validateTripItemId(Long tripItemId) {
        if (tripItemId == null || tripItemId <= 0) {
            throw invalidJourneyInput();
        }
    }

    private void leaveConfirmedAppointment(
        Long memberId,
        JourneyItem journeyItem
    ) {
        if (!"CONFIRMED".equals(journeyItem.getTripItemStatus())) {
            return;
        }
        // V5 requires CONFIRMED rows to reference an Appointment. Fail closed
        // if the joined Appointment was removed or its required host is missing.
        if (journeyItem.getAppointmentId() == null
            || journeyItem.getAppointmentHostMemberId() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (isAppointmentHost(memberId, journeyItem)) {
            throw new BusinessException(
                JourneyErrorCode.JOURNEY_APPOINTMENT_HOST_DELETE_CONFLICT
            );
        }
        if ("LEFT".equals(journeyItem.getAppointmentMembershipStatus())) {
            return;
        }
        appointmentService.leaveAppointment(
            memberId,
            journeyItem.getAppointmentId()
        );
    }

    private boolean isAppointmentHost(
        Long memberId,
        JourneyItem journeyItem
    ) {
        return memberId.equals(journeyItem.getAppointmentHostMemberId());
    }

    private void validateJourneyOwner(Journey journey, Long memberId) {
        if (journey == null) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_NOT_FOUND);
        }
        if (!memberId.equals(journey.getMemberId())) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_FORBIDDEN);
        }
    }

    private BusinessException invalidJourneyInput() {
        return new BusinessException(JourneyErrorCode.INVALID_JOURNEY_INPUT);
    }

    private boolean isInvalidBudget(BigDecimal value) {
        if (value == null) {
            return false;
        }
        int integerDigits = value.precision() - value.scale();
        return value.signum() < 0
            || value.scale() > MAX_BUDGET_SCALE
            || integerDigits > MAX_BUDGET_INTEGER_DIGITS;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
