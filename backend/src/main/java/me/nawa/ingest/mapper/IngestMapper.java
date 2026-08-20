package me.nawa.ingest.mapper;

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
}
