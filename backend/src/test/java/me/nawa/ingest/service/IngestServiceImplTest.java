package me.nawa.ingest.service;

import me.nawa.common.exception.BusinessException;
import me.nawa.ingest.dto.request.ActivityIngestItem;
import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.response.IngestResultResponse;
import me.nawa.ingest.mapper.IngestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
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


    @Test
    void ingestEvents_rejectsAnItemThatWouldBreakTheDatabase() {
        // start_date 가 없으면 NOT NULL 위반으로 SQLException 이 나고 500 이 된다.
        // 그러면 파이프라인이 같은 배치를 매일 재시도해 영원히 막힌다.
        EventIngestItem noStart = event("a");
        noStart.setStartDate(null);

        assertThrows(BusinessException.class, () -> service.ingestEvents(List.of(noStart)));
        assertTrue(mapper.callOrder.isEmpty(), "거절해야 할 배치가 DB 에 닿았습니다");
    }

    @Test
    void ingestEvents_rejectsAPeriodThatBreaksTheInvariant() {
        // 상시가 아닌데 종료일이 없으면 chk_event_period 위반이다.
        EventIngestItem openEnded = event("a");
        openEnded.setEndDate(null);

        assertThrows(BusinessException.class, () -> service.ingestEvents(List.of(openEnded)));
    }

    @Test
    void ingestEvents_acceptsAPermanentEventWithoutAnEndDate() {
        EventIngestItem permanent = event("a");
        permanent.setIsPermanent(true);
        permanent.setEndDate(null);

        service.ingestEvents(List.of(permanent));

        assertEquals(List.of("insertExploreItems", "insertEvents"), mapper.callOrder);
    }

    @Test
    void ingestEventTranslations_rejectsALanguageTheDatabaseWillNotAccept() {
        // chk_event_translations_language 는 en·ja·zh-TW·vi 만 받는다.
        assertThrows(BusinessException.class,
                () -> service.ingestEventTranslations(List.of(translation("a", "fr"))));
    }

    @Test
    void ingestEventTranslations_countsADuplicatedRowOnce() {
        mapper.existingEventIds = List.of("a");

        // 같은 (pipeline_id, language_code) 가 두 번 오면 마지막 값만 남는다.
        // 건수까지 두 번 세면 리포트 숫자가 실제와 어긋난다.
        IngestResultResponse result = service.ingestEventTranslations(
                List.of(translation("a", "en"), translation("a", "en")));

        assertEquals(1, result.getReceived());
        assertEquals(1, result.getUpdated());
    }


    @Test
    void ingestEvents_rejectsAnEventWithoutThePermanentFlag() {
        // 리뷰에서 나온 재현 경로다. isPermanent 가 없으면 검증은 비상시로 보아
        // 통과시키는데 UPDATE 는 기존 상시를 유지해서, is_permanent=TRUE 인데
        // end_date 가 있는 행이 되어 chk_event_period 를 위반했다.
        // 셋을 한 덩어리로 받으면 이 경로가 생기지 않는다.
        EventIngestItem missingFlag = event("a");
        missingFlag.setIsPermanent(null);

        assertThrows(BusinessException.class, () -> service.ingestEvents(List.of(missingFlag)));
        assertTrue(mapper.callOrder.isEmpty(), "거절해야 할 배치가 DB 에 닿았습니다");
    }

    @Test
    void ingestEvents_rejectsAHalfCoordinate() {
        // chk_event_coordinates 는 위도·경도가 둘 다 없거나 둘 다 있어야 한다.
        EventIngestItem halfCoordinate = event("a");
        halfCoordinate.setLatitude(new BigDecimal("37.5"));

        assertThrows(BusinessException.class,
                () -> service.ingestEvents(List.of(halfCoordinate)));
    }

    @Test
    void ingestEvents_acceptsAnEventWithNoCoordinatesAtAll() {
        // 주소가 없어 지오코딩하지 못한 항목이다. 실제로 8건 있다.
        service.ingestEvents(List.of(event("a")));

        assertEquals(List.of("insertExploreItems", "insertEvents"), mapper.callOrder);
    }

    @Test
    void ingestPlaces_rejectsAHalfCoordinate() {
        PlaceIngestItem halfCoordinate = new PlaceIngestItem();
        halfCoordinate.setPipelineId("a");
        halfCoordinate.setName("이름");
        halfCoordinate.setLongitude(new BigDecimal("127.0"));

        assertThrows(BusinessException.class,
                () -> service.ingestPlaces(List.of(halfCoordinate)));
    }


    @Test
    void ingestEventActivities_removesRelationsThatAreNoLongerSent() {
        mapper.existingEventIds = List.of("a");

        service.ingestEventActivities(List.of(activities("a", 10L, 20L)));

        // 목록에 없는 분류는 지워야 한다. 분류는 더해지기만 하는 값이 아니라
        // "지금 이 항목이 속한 분류"라서, 빠진 것을 남기면 영영 붙어 있게 된다.
        assertEquals(List.of("deleteMissingEventActivities", "upsertEventActivities"),
                mapper.callOrder);
    }

    @Test
    void ingestEventActivities_deletesEverythingWhenAnEmptyListArrives() {
        mapper.existingEventIds = List.of("a");

        service.ingestEventActivities(List.of(activities("a")));

        // 분류가 하나도 없는 요청은 전부 지우라는 뜻이다. 남길 짝이 없어
        // deleteMissing 의 파생 테이블이 비므로 전용 문장으로 보낸다.
        assertEquals(List.of("deleteAllEventActivities"), mapper.callOrder);
    }

    @Test
    void ingestEventActivities_skipsItemsWithoutABody() {
        mapper.existingEventIds = List.of("has-body");

        IngestResultResponse result = service.ingestEventActivities(
                List.of(activities("has-body", 10L), activities("no-body", 11L)));

        assertEquals(2, result.getReceived());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getSkipped());
    }

    @Test
    void ingestEventActivities_rejectsTwoPrimaryActivities() {
        // 대표가 둘이면 화면이 어느 것을 보여줄지 정할 수 없다.
        ActivityIngestItem item = activities("a", 10L, 20L);
        item.getActivities().forEach(link -> link.setIsPrimary(true));

        assertThrows(BusinessException.class,
                () -> service.ingestEventActivities(List.of(item)));
        assertTrue(mapper.callOrder.isEmpty());
    }

    private static ActivityIngestItem activities(String pipelineId, Long... activityIds) {
        ActivityIngestItem item = new ActivityIngestItem();
        item.setPipelineId(pipelineId);
        List<ActivityIngestItem.ActivityLink> links = new ArrayList<>();
        for (int i = 0; i < activityIds.length; i += 1) {
            ActivityIngestItem.ActivityLink link = new ActivityIngestItem.ActivityLink();
            link.setActivityId(activityIds[i]);
            link.setIsPrimary(i == 0);
            links.add(link);
        }
        item.setActivities(links);
        return item;
    }

    private static List<String> pipelineIdsOf(List<EventIngestItem> items) {
        return items.stream().map(EventIngestItem::getPipelineId).toList();
    }

    private static EventIngestItem event(String pipelineId) {
        EventIngestItem item = new EventIngestItem();
        item.setPipelineId(pipelineId);
        item.setTitle("제목");
        // start_date 는 NOT NULL 이고, 상시가 아니면 chk_event_period 가
        // 종료일을 요구한다. 서비스가 이것을 미리 본다.
        item.setStartDate(LocalDate.of(2026, 8, 20));
        item.setEndDate(LocalDate.of(2026, 8, 21));
        item.setIsPermanent(false);
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

        @Override
        public int deleteMissingEventActivities(List<ActivityIngestItem> items) {
            callOrder.add("deleteMissingEventActivities");
            return items.size();
        }

        @Override
        public int deleteMissingPlaceActivities(List<ActivityIngestItem> items) {
            callOrder.add("deleteMissingPlaceActivities");
            return items.size();
        }

        @Override
        public int deleteAllEventActivities(List<String> pipelineIds) {
            callOrder.add("deleteAllEventActivities");
            return pipelineIds.size();
        }

        @Override
        public int deleteAllPlaceActivities(List<String> pipelineIds) {
            callOrder.add("deleteAllPlaceActivities");
            return pipelineIds.size();
        }

        @Override
        public int upsertEventActivities(List<ActivityIngestItem> items) {
            callOrder.add("upsertEventActivities");
            return items.size();
        }

        @Override
        public int upsertPlaceActivities(List<ActivityIngestItem> items) {
            callOrder.add("upsertPlaceActivities");
            return items.size();
        }
    }
}
