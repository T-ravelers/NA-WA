package me.nawa.ingest.service;

import me.nawa.common.exception.BusinessException;
import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.response.IngestResultResponse;
import me.nawa.ingest.mapper.IngestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 적재 서비스가 신규와 갱신을 제대로 가르는지, 본체 없는 번역을 세는지 봅니다.
 */
class IngestServiceImplTest {

    private static final long PIPELINE_MEMBER_ID = 1000000L;

    private RecordingMapper mapper;
    private IngestServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = new RecordingMapper();
        service = new IngestServiceImpl(mapper, PIPELINE_MEMBER_ID);
    }

    @Test
    void ingestEvents_splitsNewAndExistingByPipelineId() {
        mapper.existingEventIds = List.of("old");

        IngestResultResponse result = service.ingestEvents(List.of(event("old"), event("new")));

        assertEquals(2, result.getReceived());
        assertEquals(1, result.getInserted());
        assertEquals(1, result.getUpdated());
        assertEquals(List.of("new"), pipelineIdsOf(mapper.insertedEvents));
        assertEquals(List.of("old"), pipelineIdsOf(mapper.updatedEvents));
    }

    @Test
    void ingestEvents_createsExploreItemsBeforeEvents() {
        mapper.maxExploreItemId = 40;

        service.ingestEvents(List.of(event("a"), event("b")));

        // event 가 explore_items 를 참조한다. 순서가 뒤집히면 FK 로 막힌다.
        assertEquals(List.of("insertExploreItems", "insertEvents"), mapper.callOrder);
        assertEquals(List.of(41L, 42L), mapper.reservedItemIds);
        assertEquals("EVENT", mapper.exploreItemType);
        assertEquals(PIPELINE_MEMBER_ID, mapper.reviewedBy);
    }

    @Test
    void ingestEvents_rejectsBatchWhenAnyItemLacksKeyOrTitle() {
        EventIngestItem broken = new EventIngestItem();
        broken.setPipelineId("a");

        // 한 건이라도 비면 배치 전체를 거절한다. 일부만 넣고 성공으로 알리면
        // 파이프라인이 넘어가고 격차가 쌓인다.
        assertThrows(BusinessException.class,
                () -> service.ingestEvents(List.of(event("ok"), broken)));
        assertTrue(mapper.callOrder.isEmpty(), "거절해야 할 배치가 DB 에 닿았습니다");
    }

    @Test
    void ingestEventTranslations_countsRowsWithoutABodyAsSkipped() {
        mapper.existingEventIds = List.of("has-body");

        IngestResultResponse result = service.ingestEventTranslations(
                List.of(translation("has-body", "en"),
                        translation("has-body", "ja"),
                        translation("no-body", "en")));

        assertEquals(3, result.getReceived());
        assertEquals(2, result.getUpdated());
        assertEquals(1, result.getSkipped());
    }

    @Test
    void ingestPlaces_returnsZeroesForAnEmptyBatch() {
        IngestResultResponse result = service.ingestPlaces(List.of());

        assertEquals(0, result.getReceived());
        assertTrue(mapper.callOrder.isEmpty(), "빈 배치로 DB 를 건드렸습니다");
    }

    private static List<String> pipelineIdsOf(List<EventIngestItem> items) {
        return items.stream().map(EventIngestItem::getPipelineId).toList();
    }

    private static EventIngestItem event(String pipelineId) {
        EventIngestItem item = new EventIngestItem();
        item.setPipelineId(pipelineId);
        item.setTitle("제목");
        return item;
    }

    private static EventTranslationIngestItem translation(String pipelineId, String language) {
        EventTranslationIngestItem item = new EventTranslationIngestItem();
        item.setPipelineId(pipelineId);
        item.setLanguageCode(language);
        return item;
    }

    /**
     * 호출 순서와 인자를 기록하는 가짜 매퍼입니다. 목 프레임워크를 쓰지 않는
     * 이 저장소의 방식을 따릅니다.
     */
    private static final class RecordingMapper implements IngestMapper {
        private List<String> existingEventIds = List.of();
        private long maxExploreItemId;

        private final List<String> callOrder = new ArrayList<>();
        private final List<EventIngestItem> insertedEvents = new ArrayList<>();
        private final List<EventIngestItem> updatedEvents = new ArrayList<>();
        private List<Long> reservedItemIds = List.of();
        private String exploreItemType;
        private long reviewedBy;

        @Override
        public List<String> findExistingEventPipelineIds(List<String> pipelineIds) {
            return pipelineIds.stream().filter(existingEventIds::contains).toList();
        }

        @Override
        public List<String> findExistingPlacePipelineIds(List<String> pipelineIds) {
            return List.of();
        }

        @Override
        public long findMaxExploreItemId() {
            return maxExploreItemId;
        }

        @Override
        public int insertExploreItems(List<Long> itemIds, String itemType, long reviewedBy) {
            callOrder.add("insertExploreItems");
            this.reservedItemIds = itemIds;
            this.exploreItemType = itemType;
            this.reviewedBy = reviewedBy;
            return itemIds.size();
        }

        @Override
        public int insertEvents(List<EventIngestItem> items, List<Long> itemIds) {
            callOrder.add("insertEvents");
            insertedEvents.addAll(items);
            return items.size();
        }

        @Override
        public int updateEvents(List<EventIngestItem> items) {
            callOrder.add("updateEvents");
            updatedEvents.addAll(items);
            return items.size();
        }

        @Override
        public int insertPlaces(List<PlaceIngestItem> items, List<Long> itemIds) {
            callOrder.add("insertPlaces");
            return items.size();
        }

        @Override
        public int updatePlaces(List<PlaceIngestItem> items) {
            callOrder.add("updatePlaces");
            return items.size();
        }

        @Override
        public int upsertEventTranslations(List<EventTranslationIngestItem> items) {
            callOrder.add("upsertEventTranslations");
            return items.size();
        }

        @Override
        public int upsertPlaceTranslations(
                List<me.nawa.ingest.dto.request.PlaceTranslationIngestItem> items) {
            callOrder.add("upsertPlaceTranslations");
            return items.size();
        }
    }
}
