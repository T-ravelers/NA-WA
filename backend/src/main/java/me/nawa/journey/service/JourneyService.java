package me.nawa.journey.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.TripRegion;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.request.JourneyRegionRequest;
import me.nawa.journey.dto.response.JourneyRegionResponse;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.mapper.JourneyMapper;
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

    @Transactional(readOnly = true)
    public JourneyResponse getJourney(Long memberId, Long tripId) {
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

        List<TripRegion> regions = journeyMapper.findRegionsByTripId(tripId);
        return toResponse(
            journey,
            regions == null ? List.of() : regions
        );
    }

    private void validateRequest(JourneyCreateRequest request) {
        if (request == null
            || isBlank(request.getTitle())
            || request.getTitle().trim().length() > MAX_TITLE_LENGTH
            || request.getStartDate() == null
            || request.getEndDate() == null
            || request.getStartDate().isAfter(request.getEndDate())
            || isInvalidBudget(request.getBudgetAmount())) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }

        String preference = normalizeOptional(request.getCompanionPreference());
        if (preference != null
            && preference.length() > MAX_COMPANION_PREFERENCE_LENGTH) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }

        validateRegions(request.getRegions());
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

    private void validateMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new BusinessException(
                JourneyErrorCode.INVALID_JOURNEY_INPUT
            );
        }
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
