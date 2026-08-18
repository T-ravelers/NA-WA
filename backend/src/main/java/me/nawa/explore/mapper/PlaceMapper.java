package me.nawa.explore.mapper;

import java.util.List;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceActivityResponse;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlaceMapper {

    List<PlaceSummaryResponse> searchPlaces(
        @Param("request") PlaceSearchRequest request,
        @Param("offset") int offset,
        @Param("limit") Integer limit,
        @Param("memberId") Long memberId
    );

    long countPlaces(
        @Param("request") PlaceSearchRequest request,
        @Param("memberId") Long memberId
    );

    PlaceDetailResponse findPlaceDetail(
        @Param("placeId") Long placeId,
        @Param("memberId") Long memberId
    );

    List<PlaceActivityResponse> findPlaceActivities(
        @Param("placeId") Long placeId,
        @Param("language") String language
    );
}
