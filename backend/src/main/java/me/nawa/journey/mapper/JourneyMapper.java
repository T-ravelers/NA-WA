package me.nawa.journey.mapper;

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
        @Param("visitDate") java.time.LocalDate visitDate
    );

    void insertJourneyItem(JourneyItem journeyItem);

    JourneyItem findJourneyItemById(@Param("tripItemId") Long tripItemId);
}
