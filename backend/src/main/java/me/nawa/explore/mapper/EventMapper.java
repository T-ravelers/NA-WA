package me.nawa.explore.mapper;

import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventActivityResponse;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 기준일(today)은 애플리케이션이 넘깁니다.
 *
 * <p>운영 기간 판정과 status 파생이 모두 이 값 하나를 봅니다. DB의 CURRENT_DATE()를
 * 쓰면 세션 시간대에 따라 둘이 서로 다른 날을 볼 수 있어, 자정 근처에서 "목록에는
 * 있는데 배지는 종료"처럼 어긋납니다. CI는 MySQL을 UTC로 둬서 이 의존을 잡습니다.
 */
@Mapper
public interface EventMapper {

    List<EventSummaryResponse> searchEvents(
        @Param("request") EventSearchRequest request,
        @Param("offset") int offset,
        @Param("memberId") Long memberId,
        @Param("today") LocalDate today
    );

    long countEvents(
        @Param("request") EventSearchRequest request,
        @Param("memberId") Long memberId,
        @Param("today") LocalDate today
    );

    EventDetailResponse findEventDetail(
        @Param("eventId") Long eventId,
        @Param("language") String language,
        @Param("memberId") Long memberId,
        @Param("today") LocalDate today
    );

    List<EventActivityResponse> findEventActivities(
        @Param("eventId") Long eventId,
        @Param("language") String language
    );

    int increaseEventViewCount(@Param("eventId") Long eventId);

    /**
     * 저장 status를 기준일이 정한 값으로 맞춥니다. 이미 맞는 행은 건드리지 않습니다.
     *
     * @return 실제로 옮겨진 행 수
     */
    int realignEventStatuses(@Param("today") LocalDate today);
}
