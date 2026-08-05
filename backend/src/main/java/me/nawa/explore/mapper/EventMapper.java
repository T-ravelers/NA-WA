package me.nawa.explore.mapper;

import me.nawa.explore.dto.EventSearchRequest;
import me.nawa.explore.dto.EventSummaryResponse;
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
}
