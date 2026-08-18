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
        @Param("offset") int offset,
        @Param("memberId") Long memberId
    );

    long countEvents(
        @Param("request") EventSearchRequest request,
        @Param("memberId") Long memberId
    );

    EventDetailResponse findEventDetail(
        @Param("eventId") Long eventId,
        @Param("language") String language,
        @Param("memberId") Long memberId
    );

    List<EventActivityResponse> findEventActivities(
        @Param("eventId") Long eventId,
        @Param("language") String language
    );
}
