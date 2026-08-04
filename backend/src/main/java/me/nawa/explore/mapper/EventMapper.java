package me.nawa.explore.mapper;

import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventActivityResponse;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EventMapper {

    List<EventSummaryResponse> searchEvents(
        @Param("request") EventSearchRequest request,
        @Param("offset") int offset
    );

    long countEvents(
        @Param("request") EventSearchRequest request
    );

    EventDetailResponse findEventDetail(
        @Param("eventId") Long eventId,
        @Param("language") String language
    );

    List<EventActivityResponse> findEventActivities(
        @Param("eventId") Long eventId,
        @Param("language") String language
    );
}
