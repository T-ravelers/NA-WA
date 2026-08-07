package me.nawa.journey.mapper;

import java.util.List;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyTimelineItem;
import me.nawa.journey.domain.TripRegion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JourneyMapper {

    void insertJourney(Journey journey);

    void insertRegions(@Param("regions") List<TripRegion> regions);

    Journey findJourneyById(@Param("tripId") Long tripId);

    List<TripRegion> findRegionsByTripId(@Param("tripId") Long tripId);

    List<JourneyTimelineItem> findTimelineItemsByTripId(
        @Param("tripId") Long tripId
    );
}
