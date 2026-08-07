package me.nawa.journey.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.ArrayList;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.TripRegion;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.request.JourneyRegionRequest;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.mapper.JourneyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private JourneyCreateRequest validRequest() {
        JourneyCreateRequest request = new JourneyCreateRequest();
        request.setTitle(" Seoul Foodie Week ");
        request.setStartDate(LocalDate.of(2026, 3, 28));
        request.setEndDate(LocalDate.of(2026, 4, 1));
        request.setBudgetAmount(BigDecimal.valueOf(1000));
        request.setCompanionPreference("ONE_TO_ONE");
        return request;
    }
}
