package me.nawa.journey.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyExploreItem;
import me.nawa.journey.domain.JourneyItem;
import me.nawa.journey.domain.JourneyTimelineItem;
import me.nawa.journey.domain.TripRegion;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.request.JourneyItemCreateRequest;
import me.nawa.journey.dto.request.JourneyRegionRequest;
import me.nawa.journey.dto.request.JourneyUpdateRequest;
import me.nawa.journey.dto.response.JourneyItemResponse;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.dto.response.JourneyTimelineResponse;
import me.nawa.journey.dto.response.JourneySummaryResponse;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.mapper.JourneyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JourneyServiceTest {

    @Mock
    private JourneyMapper journeyMapper;

    @InjectMocks
    private JourneyService journeyService;

    @Test
    void createJourney_allowsEmptyRegions() {
        JourneyCreateRequest request = validRequest();
        request.setRegions(null);

        doAnswer(invocation -> {
            Journey journey = invocation.getArgument(0);
            journey.setTripId(10L);
            return null;
        }).when(journeyMapper).insertJourney(any(Journey.class));

        JourneyResponse result = journeyService.createJourney(1L, request);

        assertEquals(10L, result.getTripId());
        assertEquals("Seoul Foodie Week", result.getTitle());
        assertEquals(BigDecimal.valueOf(1000), result.getBudgetAmount());
        assertEquals(List.of(), result.getRegions());
        verify(journeyMapper, never()).insertRegions(anyList());
    }

    @Test
    void updateJourney_replacesSettingsAndRegions() {
        JourneyUpdateRequest request = validUpdateRequest();
        request.setTitle(" Updated Seoul Journey ");
        request.setCompanionPreference("  FRIENDS  ");
        request.setRegions(List.of(
            new JourneyRegionRequest("BUSAN", " Busan ", 1),
            new JourneyRegionRequest(" SEOUL ", " Seoul ", 0)
        ));
        Journey journey = ownedJourney(20L);
        when(journeyMapper.findJourneyByIdForUpdate(20L)).thenReturn(journey);
        when(journeyMapper.hasJourneyItemsOutsideRange(
            20L,
            request.getStartDate(),
            request.getEndDate()
        )).thenReturn(false);
        when(journeyMapper.updateJourney(any(Journey.class))).thenReturn(1);

        JourneyResponse result = journeyService.updateJourney(1L, 20L, request);

        org.mockito.ArgumentCaptor<Journey> journeyCaptor =
            org.mockito.ArgumentCaptor.forClass(Journey.class);
        verify(journeyMapper).updateJourney(journeyCaptor.capture());
        assertEquals(
            "Updated Seoul Journey",
            journeyCaptor.getValue().getTitle()
        );
        assertEquals(
            "FRIENDS",
            journeyCaptor.getValue().getCompanionPreference()
        );
        verify(journeyMapper).softDeleteRegionsByTripId(20L);

        org.mockito.ArgumentCaptor<List<TripRegion>> regionsCaptor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(journeyMapper).insertRegions(regionsCaptor.capture());
        assertEquals("SEOUL", regionsCaptor.getValue().get(0).getRegionCode());
        assertEquals("Seoul", regionsCaptor.getValue().get(0).getRegionName());
        assertEquals("Updated Seoul Journey", result.getTitle());
        assertEquals(2, result.getRegions().size());
    }

    @Test
    void updateJourney_allowsEmptyRegionsAndClearsExistingRegions() {
        JourneyUpdateRequest request = validUpdateRequest();
        Journey journey = ownedJourney(21L);
        when(journeyMapper.findJourneyByIdForUpdate(21L)).thenReturn(journey);
        when(journeyMapper.hasJourneyItemsOutsideRange(
            21L,
            request.getStartDate(),
            request.getEndDate()
        )).thenReturn(false);
        when(journeyMapper.updateJourney(any(Journey.class))).thenReturn(1);

        JourneyResponse result = journeyService.updateJourney(1L, 21L, request);

        verify(journeyMapper).softDeleteRegionsByTripId(21L);
        verify(journeyMapper, never()).insertRegions(anyList());
        assertEquals(List.of(), result.getRegions());
    }

    @Test
    void updateJourney_throwsConflictWhenItemsFallOutsideNewRange() {
        JourneyUpdateRequest request = validUpdateRequest();
        when(journeyMapper.findJourneyByIdForUpdate(22L)).thenReturn(
            ownedJourney(22L)
        );
        when(journeyMapper.hasJourneyItemsOutsideRange(
            22L,
            request.getStartDate(),
            request.getEndDate()
        )).thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.updateJourney(1L, 22L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_DATE_RANGE_CONFLICT,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).updateJourney(any(Journey.class));
        verify(journeyMapper, never()).softDeleteRegionsByTripId(22L);
    }

    @Test
    void updateJourney_throwsInvalidInputWhenRegionsAreOmitted() {
        JourneyUpdateRequest request = validUpdateRequest();
        request.setRegions(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.updateJourney(1L, 23L, request)
        );

        assertEquals(
            JourneyErrorCode.INVALID_JOURNEY_INPUT,
            exception.getErrorCode()
        );
        verifyNoInteractions(journeyMapper);
    }

    @Test
    void updateJourney_throwsForbiddenWhenJourneyHasDifferentOwner() {
        JourneyUpdateRequest request = validUpdateRequest();
        Journey journey = ownedJourney(24L);
        journey.setMemberId(2L);
        when(journeyMapper.findJourneyByIdForUpdate(24L)).thenReturn(journey);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.updateJourney(1L, 24L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_FORBIDDEN,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).hasJourneyItemsOutsideRange(
            any(), any(), any()
        );
    }

    @Test
    void updateJourney_throwsNotFoundWhenJourneyDoesNotExist() {
        JourneyUpdateRequest request = validUpdateRequest();
        when(journeyMapper.findJourneyByIdForUpdate(25L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.updateJourney(1L, 25L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).hasJourneyItemsOutsideRange(
            any(), any(), any()
        );
    }

    @Test
    void updateJourney_throwsInternalErrorWhenUpdateAffectsNoRow() {
        JourneyUpdateRequest request = validUpdateRequest();
        when(journeyMapper.findJourneyByIdForUpdate(26L)).thenReturn(
            ownedJourney(26L)
        );
        when(journeyMapper.hasJourneyItemsOutsideRange(
            26L,
            request.getStartDate(),
            request.getEndDate()
        )).thenReturn(false);
        when(journeyMapper.updateJourney(any(Journey.class))).thenReturn(0);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.updateJourney(1L, 26L, request)
        );

        assertEquals(
            CommonErrorCode.INTERNAL_SERVER_ERROR,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).softDeleteRegionsByTripId(26L);
    }

    @Test
    void addJourneyItem_createsAddedEventWithNullConfirmationFields() {
        JourneyItemCreateRequest request = itemRequest();
        request.setDisplayOrder(2);
        request.setNote("오전 방문");
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder()
                .itemId(300L)
                .itemType("EVENT")
                .build()
        );
        when(journeyMapper.existsJourneyItem(
            90L,
            300L,
            request.getVisitDate()
        )).thenReturn(false);
        doAnswer(invocation -> {
            JourneyItem item = invocation.getArgument(0);
            item.setTripItemId(901L);
            return null;
        }).when(journeyMapper).insertJourneyItem(any(JourneyItem.class));
        when(journeyMapper.findJourneyItemById(901L)).thenReturn(
            JourneyItem.builder()
                .tripItemId(901L)
                .tripId(90L)
                .itemId(300L)
                .itemType("EVENT")
                .visitDate(request.getVisitDate())
                .tripItemStatus("ADDED")
                .displayOrder(2)
                .note("오전 방문")
                .createdAt(LocalDateTime.of(2026, 4, 1, 10, 0))
                .build()
        );

        JourneyItemResponse result = journeyService.addJourneyItem(
            1L,
            90L,
            request
        );

        assertEquals(901L, result.getTripItemId());
        assertEquals(90L, result.getJourneyId());
        assertEquals("EVENT", result.getItemType());
        assertEquals("ADDED", result.getTripItemStatus());
        assertEquals(2, result.getDisplayOrder());
        assertEquals("오전 방문", result.getNote());
        assertNull(result.getAppointmentId());
        assertNull(result.getConfirmedAt());
    }

    @Test
    void addJourneyItem_defaultsDisplayOrderAndNormalizesBlankNote() {
        JourneyItemCreateRequest request = itemRequest();
        request.setDisplayOrder(null);
        request.setNote("  ");
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("PLACE").build()
        );
        when(journeyMapper.existsJourneyItem(
            90L,
            300L,
            request.getVisitDate()
        )).thenReturn(false);
        doAnswer(invocation -> {
            JourneyItem item = invocation.getArgument(0);
            item.setTripItemId(902L);
            return null;
        }).when(journeyMapper).insertJourneyItem(any(JourneyItem.class));
        when(journeyMapper.findJourneyItemById(902L)).thenReturn(
            JourneyItem.builder()
                .tripItemId(902L)
                .tripId(90L)
                .itemId(300L)
                .itemType("PLACE")
                .visitDate(request.getVisitDate())
                .tripItemStatus("ADDED")
                .displayOrder(0)
                .build()
        );

        journeyService.addJourneyItem(1L, 90L, request);

        org.mockito.ArgumentCaptor<JourneyItem> captor =
            org.mockito.ArgumentCaptor.forClass(JourneyItem.class);
        verify(journeyMapper).insertJourneyItem(captor.capture());
        assertEquals(0, captor.getValue().getDisplayOrder());
        assertNull(captor.getValue().getNote());
        assertEquals("ADDED", captor.getValue().getTripItemStatus());
        assertNull(captor.getValue().getAppointmentId());
        assertNull(captor.getValue().getConfirmedAt());
    }

    @Test
    void addJourneyItem_throwsInternalError_whenGeneratedKeyIsMissing() {
        JourneyItemCreateRequest request = itemRequest();
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("EVENT").build()
        );
        when(journeyMapper.existsJourneyItem(
            90L,
            300L,
            request.getVisitDate()
        )).thenReturn(false);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            CommonErrorCode.INTERNAL_SERVER_ERROR,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).findJourneyItemById(any());
    }

    @Test
    void addJourneyItem_throwsInternalError_whenCreatedItemCannotBeReloaded() {
        JourneyItemCreateRequest request = itemRequest();
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("EVENT").build()
        );
        when(journeyMapper.existsJourneyItem(
            90L,
            300L,
            request.getVisitDate()
        )).thenReturn(false);
        doAnswer(invocation -> {
            JourneyItem item = invocation.getArgument(0);
            item.setTripItemId(903L);
            return null;
        }).when(journeyMapper).insertJourneyItem(any(JourneyItem.class));
        when(journeyMapper.findJourneyItemById(903L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            CommonErrorCode.INTERNAL_SERVER_ERROR,
            exception.getErrorCode()
        );
    }

    @Test
    void addJourneyItem_throwsDuplicate_whenExistingItemMatchesTripDate() {
        JourneyItemCreateRequest request = itemRequest();
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("EVENT").build()
        );
        when(journeyMapper.existsJourneyItem(
            90L,
            300L,
            request.getVisitDate()
        )).thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_ITEM_DUPLICATE,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).insertJourneyItem(any(JourneyItem.class));
    }

    @Test
    void addJourneyItem_mapsConcurrentUniqueViolationToDuplicate() {
        JourneyItemCreateRequest request = itemRequest();
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("EVENT").build()
        );
        when(journeyMapper.existsJourneyItem(
            90L,
            300L,
            request.getVisitDate()
        )).thenReturn(false);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
            .when(journeyMapper).insertJourneyItem(any(JourneyItem.class));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_ITEM_DUPLICATE,
            exception.getErrorCode()
        );
    }

    @Test
    void addJourneyItem_throwsDateError_whenVisitDateIsOutsideJourney() {
        JourneyItemCreateRequest request = itemRequest();
        request.setVisitDate(LocalDate.of(2026, 4, 4));
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("EVENT").build()
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_ITEM_DATE_OUT_OF_RANGE,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).insertJourneyItem(any(JourneyItem.class));
    }

    @Test
    void addJourneyItem_throwsDisplayOrderError_whenNegative() {
        JourneyItemCreateRequest request = itemRequest();
        request.setDisplayOrder(-1);
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_ITEM_DISPLAY_ORDER_INVALID,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).findAvailableExploreItemById(300L);
    }

    @Test
    void addJourneyItem_throwsNotFound_whenExploreItemIsUnavailable() {
        JourneyItemCreateRequest request = itemRequest();
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_ITEM_NOT_FOUND,
            exception.getErrorCode()
        );
    }

    @Test
    void addJourneyItem_throwsTypeError_whenExploreItemTypeIsUnsupported() {
        JourneyItemCreateRequest request = itemRequest();
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(
            ownedJourney(90L)
        );
        when(journeyMapper.findAvailableExploreItemById(300L)).thenReturn(
            JourneyExploreItem.builder().itemId(300L).itemType("HOTEL").build()
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_ITEM_TYPE_UNSUPPORTED,
            exception.getErrorCode()
        );
    }

    @Test
    void addJourneyItem_throwsForbidden_whenJourneyHasDifferentOwner() {
        JourneyItemCreateRequest request = itemRequest();
        Journey journey = ownedJourney(90L);
        journey.setMemberId(2L);
        when(journeyMapper.findJourneyByIdForUpdate(90L)).thenReturn(journey);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.addJourneyItem(1L, 90L, request)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_FORBIDDEN,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).findAvailableExploreItemById(300L);
    }

    @Test
    void createJourney_insertsNormalizedRegionsByDisplayOrder() {
        JourneyCreateRequest request = validRequest();
        request.setRegions(List.of(
            new JourneyRegionRequest("BUSAN", "Busan", 1),
            new JourneyRegionRequest(" SEOUL ", " Seoul ", 0)
        ));
        List<List<TripRegion>> insertedRegions = new ArrayList<>();

        doAnswer(invocation -> {
            Journey journey = invocation.getArgument(0);
            journey.setTripId(11L);
            return null;
        }).when(journeyMapper).insertJourney(any(Journey.class));
        doAnswer(invocation -> {
            insertedRegions.add(invocation.getArgument(0));
            return null;
        }).when(journeyMapper).insertRegions(anyList());

        JourneyResponse result = journeyService.createJourney(1L, request);

        assertEquals("SEOUL", insertedRegions.get(0).get(0).getRegionCode());
        assertEquals("Seoul", insertedRegions.get(0).get(0).getRegionName());
        assertEquals(1, insertedRegions.get(0).get(1).getDisplayOrder());
        assertEquals(2, result.getRegions().size());
        assertEquals("SEOUL", result.getRegions().get(0).getRegionCode());
    }

    @Test
    void createJourney_throwsInvalidInput_whenDatesAreReversed() {
        JourneyCreateRequest request = validRequest();
        request.setStartDate(LocalDate.of(2026, 4, 2));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.createJourney(1L, request)
        );

        assertEquals(
            JourneyErrorCode.INVALID_JOURNEY_INPUT,
            exception.getErrorCode()
        );
        verifyNoInteractions(journeyMapper);
    }

    @Test
    void createJourney_throwsInvalidInput_whenRegionCodeIsDuplicated() {
        JourneyCreateRequest request = validRequest();
        request.setRegions(List.of(
            new JourneyRegionRequest("SEOUL", "Seoul", 0),
            new JourneyRegionRequest("seoul", "Seoul again", 1)
        ));

        assertThrows(
            BusinessException.class,
            () -> journeyService.createJourney(1L, request)
        );
        verifyNoInteractions(journeyMapper);
    }

    @Test
    void createJourney_throwsInvalidInput_whenBudgetIsNegative() {
        JourneyCreateRequest request = validRequest();
        request.setBudgetAmount(BigDecimal.valueOf(-1));

        assertThrows(
            BusinessException.class,
            () -> journeyService.createJourney(1L, request)
        );
        verifyNoInteractions(journeyMapper);
    }

    @Test
    void createJourney_throwsInvalidInput_whenBudgetExceedsDatabaseScale() {
        JourneyCreateRequest request = validRequest();
        request.setBudgetAmount(new BigDecimal("1000.00001"));

        assertThrows(
            BusinessException.class,
            () -> journeyService.createJourney(1L, request)
        );
        verifyNoInteractions(journeyMapper);
    }

    @Test
    void createJourney_throwsInvalidInput_whenRegionFieldsAreInvalid() {
        JourneyCreateRequest request = validRequest();
        request.setRegions(List.of(
            new JourneyRegionRequest("SEOUL", " ", -1)
        ));

        assertThrows(
            BusinessException.class,
            () -> journeyService.createJourney(1L, request)
        );
        verifyNoInteractions(journeyMapper);
    }

    @Test
    void getJourney_returnsOwnedJourneyWithEmptyRegions() {
        Journey journey = Journey.builder()
            .tripId(20L)
            .memberId(1L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 3, 28))
            .endDate(LocalDate.of(2026, 4, 1))
            .build();
        when(journeyMapper.findJourneyById(20L)).thenReturn(journey);
        when(journeyMapper.findRegionsByTripId(20L)).thenReturn(null);

        JourneyResponse result = journeyService.getJourney(1L, 20L);

        assertEquals(20L, result.getTripId());
        assertEquals(List.of(), result.getRegions());
    }

    @Test
    void getJourneys_returnsOnlySummaryFields() {
        when(journeyMapper.findJourneysByMemberId(1L)).thenReturn(List.of(
            Journey.builder()
                .tripId(20L)
                .memberId(1L)
                .title("Seoul Foodie Week")
                .startDate(LocalDate.of(2026, 3, 28))
                .endDate(LocalDate.of(2026, 4, 1))
                .eventCount(3L)
                .placeCount(5L)
                .build()
        ));

        List<JourneySummaryResponse> result = journeyService.getJourneys(1L);

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getTripId());
        assertEquals("Seoul Foodie Week", result.get(0).getTitle());
        assertEquals(LocalDate.of(2026, 3, 28), result.get(0).getStartDate());
        assertEquals(3L, result.get(0).getEventCount());
        assertEquals(5L, result.get(0).getPlaceCount());
    }

    @Test
    void getJourney_throwsNotFound_whenJourneyDoesNotExist() {
        when(journeyMapper.findJourneyById(30L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.getJourney(1L, 30L)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_NOT_FOUND,
            exception.getErrorCode()
        );
    }

    @Test
    void getJourney_throwsForbidden_whenJourneyHasDifferentOwner() {
        Journey journey = Journey.builder()
            .tripId(40L)
            .memberId(2L)
            .build();
        when(journeyMapper.findJourneyById(40L)).thenReturn(journey);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.getJourney(1L, 40L)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_FORBIDDEN,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).findRegionsByTripId(40L);
    }

    @Test
    void getTimeline_returnsEmptyTimeline_whenJourneyHasNoItems() {
        when(journeyMapper.findJourneyById(50L)).thenReturn(ownedJourney(50L));
        when(journeyMapper.findTimelineItemsByTripId(50L)).thenReturn(null);

        JourneyTimelineResponse result = journeyService.getTimeline(1L, 50L);

        assertEquals(50L, result.getTripId());
        assertEquals(List.of(), result.getTimeline());
    }

    @Test
    void getTimeline_groupsAndSortsItemsWithDisplayModels() {
        JourneyTimelineItem laterDate = timelineItem(
            103L,
            LocalDate.of(2026, 4, 2),
            0,
            "PLACE"
        );
        laterDate.setTitle("Gwangjang Market");
        laterDate.setPlaceKind("MARKET");
        laterDate.setAddressDetail("Gate 2");
        laterDate.setMenuSummary("Bindaetteok");
        laterDate.setPlaceActive(true);
        laterDate.setAppointmentId(900L);
        laterDate.setActivityStartAt(
            LocalDateTime.of(2026, 4, 2, 10, 0)
        );
        laterDate.setActivityEndAt(
            LocalDateTime.of(2026, 4, 2, 12, 0)
        );
        laterDate.setAppointmentStatus("CONFIRMED");
        laterDate.setStatus("CONFIRMED");

        JourneyTimelineItem second = timelineItem(
            102L,
            LocalDate.of(2026, 4, 1),
            1,
            "EVENT"
        );
        second.setTitle("Night Concert");

        JourneyTimelineItem first = timelineItem(
            101L,
            LocalDate.of(2026, 4, 1),
            0,
            "EVENT"
        );
        first.setTitle("Spring Festival");
        first.setEventKind("FESTIVAL");
        first.setEventStartDate(LocalDate.of(2026, 4, 1));
        first.setEventEndDate(LocalDate.of(2026, 4, 3));
        first.setOrganizer("Seoul City");
        first.setEventReservationUrl("https://example.com/reserve");
        first.setVenueName("City Hall");

        when(journeyMapper.findJourneyById(60L)).thenReturn(ownedJourney(60L));
        when(journeyMapper.findTimelineItemsByTripId(60L)).thenReturn(
            List.of(laterDate, second, first)
        );

        JourneyTimelineResponse result = journeyService.getTimeline(1L, 60L);

        assertEquals(2, result.getTimeline().size());
        assertEquals(
            LocalDate.of(2026, 4, 1),
            result.getTimeline().get(0).getVisitDate()
        );
        assertEquals(101L, result.getTimeline().get(0).getItems().get(0)
            .getTripItemId());
        assertEquals("FESTIVAL", result.getTimeline().get(0).getItems().get(0)
            .getEventDetail().getEventKind());
        assertEquals(List.of(), result.getTimeline().get(0).getItems().get(0)
            .getExploreItem().getImageUrls());
        assertNull(result.getTimeline().get(0).getItems().get(0)
            .getPlaceDetail());
        assertNotNull(result.getTimeline().get(1).getItems().get(0)
            .getPlaceDetail());
        assertNotNull(result.getTimeline().get(1).getItems().get(0)
            .getAppointment());
        assertEquals(900L, result.getTimeline().get(1).getItems().get(0)
            .getAppointment().getAppointmentId());
    }

    @Test
    void getTimeline_throwsNotFound_whenJourneyDoesNotExist() {
        when(journeyMapper.findJourneyById(70L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.getTimeline(1L, 70L)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).findTimelineItemsByTripId(70L);
    }

    @Test
    void getTimeline_throwsForbidden_whenJourneyHasDifferentOwner() {
        Journey journey = ownedJourney(80L);
        journey.setMemberId(2L);
        when(journeyMapper.findJourneyById(80L)).thenReturn(journey);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> journeyService.getTimeline(1L, 80L)
        );

        assertEquals(
            JourneyErrorCode.JOURNEY_FORBIDDEN,
            exception.getErrorCode()
        );
        verify(journeyMapper, never()).findTimelineItemsByTripId(80L);
    }

    private Journey ownedJourney(Long tripId) {
        return Journey.builder()
            .tripId(tripId)
            .memberId(1L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 3, 28))
            .endDate(LocalDate.of(2026, 4, 3))
            .build();
    }

    private JourneyTimelineItem timelineItem(
        Long tripItemId,
        LocalDate visitDate,
        int displayOrder,
        String itemType
    ) {
        return JourneyTimelineItem.builder()
            .tripItemId(tripItemId)
            .itemId(tripItemId + 1000)
            .visitDate(visitDate)
            .status("ADDED")
            .displayOrder(displayOrder)
            .itemType(itemType)
            .region1("Seoul")
            .addressRoad("1 Jong-ro")
            .build();
    }

    private JourneyItemCreateRequest itemRequest() {
        JourneyItemCreateRequest request = new JourneyItemCreateRequest();
        request.setItemId(300L);
        request.setVisitDate(LocalDate.of(2026, 4, 1));
        return request;
    }

    private JourneyCreateRequest validRequest() {
        JourneyCreateRequest request = new JourneyCreateRequest();
        request.setTitle(" Seoul Foodie Week ");
        request.setStartDate(LocalDate.of(2026, 3, 28));
        request.setEndDate(LocalDate.of(2026, 4, 1));
        request.setBudgetAmount(BigDecimal.valueOf(1000));
        request.setCompanionPreference("ONE_TO_ONE");
        return request;
    }

    private JourneyUpdateRequest validUpdateRequest() {
        JourneyUpdateRequest request = new JourneyUpdateRequest();
        request.setTitle("Updated Seoul Journey");
        request.setStartDate(LocalDate.of(2026, 3, 28));
        request.setEndDate(LocalDate.of(2026, 4, 3));
        request.setBudgetAmount(BigDecimal.valueOf(2000));
        request.setCompanionPreference("FRIENDS");
        request.setRegions(List.of());
        return request;
    }
}
