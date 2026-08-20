package me.nawa.ingest.mapper;

import me.nawa.ingest.dto.request.ActivityIngestItem;
import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.request.PlaceTranslationIngestItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 크롤러 파이프라인이 보낸 항목을 운영 테이블에 반영합니다.
 *
 * <p>모든 조회와 반영의 키는 pipeline_id 입니다.
 */
@Mapper
public interface IngestMapper {

    /**
     * 보낸 pipeline_id 중 이미 event 에 있는 것만 돌려줍니다.
     * 신규와 갱신을 가르는 기준입니다.
     */
    List<String> findExistingEventPipelineIds(@Param("pipelineIds") List<String> pipelineIds);

    List<String> findExistingPlacePipelineIds(@Param("pipelineIds") List<String> pipelineIds);

    /**
     * 신규 item_id 를 발급하기 위한 현재 최댓값입니다.
     *
     * <p>AUTO_INCREMENT 에 기대지 않고 명시 발급합니다. explore_items 와
     * event/place 가 같은 id 를 공유하므로 순서에 기대면 환경에 따라
     * 매핑이 어긋납니다.
     */
    long findMaxExploreItemId();

    /**
     * explore_items 를 먼저 만듭니다. event/place 가 이 행을 참조합니다.
     */
    int insertExploreItems(
        @Param("itemIds") List<Long> itemIds,
        @Param("itemType") String itemType,
        @Param("reviewedBy") long reviewedBy
    );

    int insertEvents(@Param("items") List<EventIngestItem> items,
                     @Param("itemIds") List<Long> itemIds);

    int updateEvents(@Param("items") List<EventIngestItem> items);

    int insertPlaces(@Param("items") List<PlaceIngestItem> items,
                     @Param("itemIds") List<Long> itemIds);

    int updatePlaces(@Param("items") List<PlaceIngestItem> items);

    /**
     * 번역은 본체가 있는 건만 반영합니다. 없는 건은 조용히 빠집니다.
     * 반환값이 보낸 건수보다 적으면 그 차이가 건너뛴 수입니다.
     */
    int upsertEventTranslations(@Param("items") List<EventTranslationIngestItem> items);

    int upsertPlaceTranslations(@Param("items") List<PlaceTranslationIngestItem> items);

    /**
     * 보낸 목록에 없는 분류를 지웁니다.
     *
     * <p>대상은 이번 배치에 들어온 pipeline_id 로 한정합니다. 그러지 않으면
     * 배치에 없는 항목의 분류까지 지워집니다.
     */
    int deleteMissingEventActivities(@Param("items") List<ActivityIngestItem> items);

    int deleteMissingPlaceActivities(@Param("items") List<ActivityIngestItem> items);

    /**
     * 분류를 통째로 지웁니다.
     *
     * <p>분류가 하나도 없는 항목은 위 문장으로 보내지 않습니다. 남길 짝이 없어
     * 파생 테이블이 비면 SQL 이 성립하지 않기 때문입니다.
     */
    int deleteAllEventActivities(@Param("pipelineIds") List<String> pipelineIds);

    int deleteAllPlaceActivities(@Param("pipelineIds") List<String> pipelineIds);

    /**
     * 본체가 있는 항목의 분류만 넣습니다. 없는 항목은 JOIN 이 걸러 냅니다.
     */
    int upsertEventActivities(@Param("items") List<ActivityIngestItem> items);

    int upsertPlaceActivities(@Param("items") List<ActivityIngestItem> items);
}
