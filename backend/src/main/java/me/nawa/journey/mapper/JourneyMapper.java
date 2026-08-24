package me.nawa.journey.mapper;

import java.math.BigDecimal;
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

    BigDecimal findCurrentSpentAmount(
        @Param("tripId") Long tripId,
        @Param("memberId") Long memberId
    );

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
        @Param("tripId") Long tripId,
        @Param("language") String language
    );

    JourneyExploreItem findAvailableExploreItemById(
        @Param("itemId") Long itemId
    );

    boolean existsJourneyItem(
        @Param("tripId") Long tripId,
        @Param("itemId") Long itemId,
        @Param("visitDate") LocalDate visitDate
    );

    // 같은 자리에 약속이 이미 걸려 있는지만 본다. 담아만 둔 자리는 약속 항목으로
    // 승격되므로 약속 생성을 막지 않는다 — existsJourneyItem으로 대신 판단하면
    // 담아 둔 장소로는 약속을 만들 수 없게 된다.
    boolean existsAppointmentJourneyItem(
        @Param("tripId") Long tripId,
        @Param("itemId") Long itemId,
        @Param("visitDate") LocalDate visitDate
    );

    // 참여자가 "Add to journey"로 이미 담아 둔 자리에 약속이 겹칠 수 있다.
    // (trip_id, item_id, visit_date)는 살아 있는 행에 대해 UNIQUE이므로, 새로 넣기
    // 전에 그 행을 잠그고 가져와 약속 항목으로 올릴지 판단한다.
    JourneyItem findJourneyItemByItemAndDateForUpdate(
        @Param("tripId") Long tripId,
        @Param("itemId") Long itemId,
        @Param("visitDate") LocalDate visitDate
    );

    // 이미 담아 둔 항목을 약속 항목으로 올린다. 다른 약속이 이미 걸려 있으면 0을
    // 돌려주므로, 호출하는 쪽이 중복으로 처리한다.
    int promoteJourneyItemToAppointment(
        @Param("tripItemId") Long tripItemId,
        @Param("appointmentId") Long appointmentId
    );

    // 참여를 취소하면 그 약속으로 잡아 둔 여정 항목도 함께 내린다.
    int softDeleteJourneyItemByAppointment(
        @Param("tripId") Long tripId,
        @Param("appointmentId") Long appointmentId
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
