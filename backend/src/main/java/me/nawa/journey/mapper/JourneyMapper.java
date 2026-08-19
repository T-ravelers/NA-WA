package me.nawa.journey.mapper;

import java.time.LocalDate;
import java.util.List;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyExploreItem;
import me.nawa.journey.domain.JourneyItem;
import me.nawa.journey.domain.JourneyTimelineItem;
import me.nawa.journey.domain.TripRegion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JourneyMapper {

    void insertJourney(Journey journey);

    void insertRegions(@Param("regions") List<TripRegion> regions);

    List<Journey> findJourneysByMemberId(@Param("memberId") Long memberId);

    Journey findJourneyById(@Param("tripId") Long tripId);

    Journey findJourneyByIdForUpdate(@Param("tripId") Long tripId);

    int updateJourney(Journey journey);

    boolean hasJourneyItemsOutsideRange(
        @Param("tripId") Long tripId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    int softDeleteRegionsByTripId(@Param("tripId") Long tripId);

    List<TripRegion> findRegionsByTripId(@Param("tripId") Long tripId);

    List<JourneyTimelineItem> findTimelineItemsByTripId(
        @Param("tripId") Long tripId
    );

    JourneyExploreItem findAvailableExploreItemById(
        @Param("itemId") Long itemId
    );

    boolean existsJourneyItem(
        @Param("tripId") Long tripId,
        @Param("itemId") Long itemId,
        @Param("visitDate") LocalDate visitDate
    );

    void insertJourneyItem(JourneyItem journeyItem);

    // 약속 생성과 동시에 여정 항목을 CONFIRMED로 만든다. ADDED로 넣고 나중에
    // 승격하는 경로가 아니라, 처음부터 CONFIRMED로 INSERT한다 — appointment_id
    // 없이 CONFIRMED가 될 수 없다는 chk_trip_items_status 제약과 맞물린다.
    void insertConfirmedJourneyItem(JourneyItem journeyItem);

    JourneyItem findJourneyItemById(@Param("tripItemId") Long tripItemId);

    JourneyItem findJourneyItemForUpdate(
        @Param("tripId") Long tripId,
        @Param("tripItemId") Long tripItemId,
        @Param("memberId") Long memberId
    );

    List<JourneyItem> findConfirmedJourneyItemsForUpdate(
        @Param("tripId") Long tripId,
        @Param("memberId") Long memberId
    );

    int softDeleteJourneyItem(
        @Param("tripId") Long tripId,
        @Param("tripItemId") Long tripItemId
    );

    int softDeleteJourneyItemsByTripId(@Param("tripId") Long tripId);

    int softDeleteReportsByTripId(@Param("tripId") Long tripId);

    int softDeleteExpenseLinksByTripId(@Param("tripId") Long tripId);

    int softDeleteJourney(@Param("tripId") Long tripId);
}
