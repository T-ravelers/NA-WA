package me.nawa.ingest.service;

import me.nawa.ingest.dto.request.ActivityIngestItem;
import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.request.PlaceTranslationIngestItem;
import me.nawa.ingest.dto.response.IngestResultResponse;
import me.nawa.ingest.exception.IngestInvalidItemException;
import me.nawa.ingest.mapper.IngestMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Set.of;

/**
 * 파이프라인이 보낸 항목을 운영 테이블에 반영합니다.
 *
 * <p>한 요청이 한 트랜잭션입니다. 중간에 실패하면 그 배치는 통째로 되돌아가고,
 * 파이프라인의 job 은 큐에 남아 다음 회차에 다시 시도합니다. 절반만 들어간
 * 상태로 남는 경우가 없어야 리포트의 건수를 믿을 수 있습니다.
 */
@Service
public class IngestServiceImpl implements IngestService {

    /**
     * chk_event_translations_language · chk_place_translations_language 와 같은 목록.
     *
     * <p>조회 쪽 허용 목록({@code ExploreLanguagePolicy})과 <b>같은 집합을 따로 들고
     * 있습니다.</b> 로케일을 늘릴 때는 DB {@code CHECK} · 조회 · 적재 세 벌을 함께
     * 넓혀야 합니다. 여기만 빠지면 조회는 되는데 그 언어의 번역 배치가 통째로 거절돼
     * 데이터가 영영 쌓이지 않고, 화면에서는 "번역이 아직 안 붙었다"와 구별되지 않습니다.
     * 순서는 backend/docs/EXPLORE_API.md 의 「새 로케일을 추가하는 순서」에 있습니다.
     *
     * <p>비교는 대소문자를 가립니다. 파이프라인은 {@code zh-TW}를 저장된 표기 그대로
     * 보내야 합니다.
     */
    private static final Set<String> LANGUAGES = of("en", "ja", "zh-TW", "vi");

    private final IngestMapper ingestMapper;
    private final long pipelineMemberId;

    public IngestServiceImpl(
            IngestMapper ingestMapper,
            @Value("${auth.service.pipeline-member-id:1000000}") long pipelineMemberId) {
        this.ingestMapper = ingestMapper;
        this.pipelineMemberId = pipelineMemberId;
    }

    @Override
    @Transactional
    public IngestResultResponse ingestEvents(List<EventIngestItem> items) {
        if (items.isEmpty()) {
            return new IngestResultResponse(0, 0, 0, 0);
        }

        items.forEach(IngestServiceImpl::validateEvent);

        Set<String> existing = new HashSet<>(ingestMapper.findExistingEventPipelineIds(
                items.stream().map(EventIngestItem::getPipelineId).toList()));

        List<EventIngestItem> toInsert = new ArrayList<>();
        List<EventIngestItem> toUpdate = new ArrayList<>();
        for (EventIngestItem item : items) {
            if (existing.contains(item.getPipelineId())) {
                toUpdate.add(item);
            } else {
                toInsert.add(item);
            }
        }

        int inserted = 0;
        if (!toInsert.isEmpty()) {
            // explore_items 를 먼저 만든다. event 가 이 행을 참조한다.
            List<Long> itemIds = reserveItemIds(toInsert.size());
            ingestMapper.insertExploreItems(itemIds, "EVENT", pipelineMemberId);
            inserted = ingestMapper.insertEvents(toInsert, itemIds);
        }

        int updated = toUpdate.isEmpty() ? 0 : ingestMapper.updateEvents(toUpdate);

        return new IngestResultResponse(items.size(), inserted, updated, 0);
    }

    @Override
    @Transactional
    public IngestResultResponse ingestPlaces(List<PlaceIngestItem> items) {
        if (items.isEmpty()) {
            return new IngestResultResponse(0, 0, 0, 0);
        }

        items.forEach(it -> {
            requireValid(hasText(it.getPipelineId()) && hasText(it.getName()));
            requireCoordinatePair(it.getLatitude(), it.getLongitude());
        });

        Set<String> existing = new HashSet<>(ingestMapper.findExistingPlacePipelineIds(
                items.stream().map(PlaceIngestItem::getPipelineId).toList()));

        List<PlaceIngestItem> toInsert = new ArrayList<>();
        List<PlaceIngestItem> toUpdate = new ArrayList<>();
        for (PlaceIngestItem item : items) {
            if (existing.contains(item.getPipelineId())) {
                toUpdate.add(item);
            } else {
                toInsert.add(item);
            }
        }

        int inserted = 0;
        if (!toInsert.isEmpty()) {
            List<Long> itemIds = reserveItemIds(toInsert.size());
            ingestMapper.insertExploreItems(itemIds, "PLACE", pipelineMemberId);
            inserted = ingestMapper.insertPlaces(toInsert, itemIds);
        }

        int updated = toUpdate.isEmpty() ? 0 : ingestMapper.updatePlaces(toUpdate);

        return new IngestResultResponse(items.size(), inserted, updated, 0);
    }

    @Override
    @Transactional
    public IngestResultResponse ingestEventTranslations(List<EventTranslationIngestItem> items) {
        if (items.isEmpty()) {
            return new IngestResultResponse(0, 0, 0, 0);
        }
        items.forEach(it -> {
            requireValid(hasText(it.getPipelineId()));
            requireLanguage(it.getLanguageCode());
        });

        // 같은 (pipeline_id, language_code) 가 배치에 두 번 오면 ON DUPLICATE KEY 로
        // 마지막 값만 남는데 건수는 두 번 세어진다. 미리 하나로 줄인다.
        items = dedupeBy(items,
                it -> it.getPipelineId() + ':' + it.getLanguageCode());

        Set<String> existing = new HashSet<>(ingestMapper.findExistingEventPipelineIds(
                items.stream().map(EventTranslationIngestItem::getPipelineId).distinct().toList()));
        int skipped = (int) items.stream()
                .filter(it -> !existing.contains(it.getPipelineId()))
                .count();

        ingestMapper.upsertEventTranslations(items);
        return new IngestResultResponse(items.size(), 0, items.size() - skipped, skipped);
    }

    @Override
    @Transactional
    public IngestResultResponse ingestPlaceTranslations(List<PlaceTranslationIngestItem> items) {
        if (items.isEmpty()) {
            return new IngestResultResponse(0, 0, 0, 0);
        }
        items.forEach(it -> {
            requireValid(hasText(it.getPipelineId()));
            requireLanguage(it.getLanguageCode());
        });

        items = dedupeBy(items,
                it -> it.getPipelineId() + ':' + it.getLanguageCode());

        Set<String> existing = new HashSet<>(ingestMapper.findExistingPlacePipelineIds(
                items.stream().map(PlaceTranslationIngestItem::getPipelineId).distinct().toList()));
        int skipped = (int) items.stream()
                .filter(it -> !existing.contains(it.getPipelineId()))
                .count();

        ingestMapper.upsertPlaceTranslations(items);
        return new IngestResultResponse(items.size(), 0, items.size() - skipped, skipped);
    }





    @Override
    @Transactional
    public IngestResultResponse ingestEventActivities(List<ActivityIngestItem> items) {
        if (items.isEmpty()) {
            return new IngestResultResponse(0, 0, 0, 0);
        }
        // 같은 항목이 두 번 오면 대표 유일성이 배치 단위로 깨진다. 각 항목은
        // 그 시점의 분류 전체라 나중 것이 최신이므로 마지막만 남긴다.
        items = dedupeBy(items, ActivityIngestItem::getPipelineId);
        items.forEach(IngestServiceImpl::validateActivities);

        Set<String> existing = new HashSet<>(ingestMapper.findExistingEventPipelineIds(
                items.stream().map(ActivityIngestItem::getPipelineId).distinct().toList()));
        List<ActivityIngestItem> known = items.stream()
                .filter(it -> existing.contains(it.getPipelineId())).toList();

        if (known.isEmpty()) {
            return new IngestResultResponse(items.size(), 0, 0, items.size());
        }

        // 분류가 없는 항목과 있는 항목을 갈라서 보낸다. 없는 쪽은 남길 짝이
        // 하나도 없어 파생 테이블이 비고, 그러면 SQL 이 성립하지 않는다.
        List<ActivityIngestItem> withLinks = known.stream()
                .filter(it -> !it.getActivities().isEmpty()).toList();
        List<String> cleared = known.stream()
                .filter(it -> it.getActivities().isEmpty())
                .map(ActivityIngestItem::getPipelineId).toList();

        if (!cleared.isEmpty()) {
            ingestMapper.deleteAllEventActivities(cleared);
        }
        if (!withLinks.isEmpty()) {
            ingestMapper.deleteMissingEventActivities(withLinks);
            ingestMapper.upsertEventActivities(withLinks);
        }

        // updated 는 분류를 손댄 항목 수다. delete 와 upsert 두 문장이 나가서
        // 영향 행수를 더하면 "관계 몇 개를 건드렸나"가 되어 다른 경로와 뜻이
        // 달라진다. 여기서는 항목 수로 통일한다.
        return new IngestResultResponse(
                items.size(), 0, known.size(), items.size() - known.size());
    }

    @Override
    @Transactional
    public IngestResultResponse ingestPlaceActivities(List<ActivityIngestItem> items) {
        if (items.isEmpty()) {
            return new IngestResultResponse(0, 0, 0, 0);
        }
        // 같은 항목이 두 번 오면 대표 유일성이 배치 단위로 깨진다. 각 항목은
        // 그 시점의 분류 전체라 나중 것이 최신이므로 마지막만 남긴다.
        items = dedupeBy(items, ActivityIngestItem::getPipelineId);
        items.forEach(IngestServiceImpl::validateActivities);

        Set<String> existing = new HashSet<>(ingestMapper.findExistingPlacePipelineIds(
                items.stream().map(ActivityIngestItem::getPipelineId).distinct().toList()));
        List<ActivityIngestItem> known = items.stream()
                .filter(it -> existing.contains(it.getPipelineId())).toList();

        if (known.isEmpty()) {
            return new IngestResultResponse(items.size(), 0, 0, items.size());
        }

        // 분류가 없는 항목과 있는 항목을 갈라서 보낸다. 없는 쪽은 남길 짝이
        // 하나도 없어 파생 테이블이 비고, 그러면 SQL 이 성립하지 않는다.
        List<ActivityIngestItem> withLinks = known.stream()
                .filter(it -> !it.getActivities().isEmpty()).toList();
        List<String> cleared = known.stream()
                .filter(it -> it.getActivities().isEmpty())
                .map(ActivityIngestItem::getPipelineId).toList();

        if (!cleared.isEmpty()) {
            ingestMapper.deleteAllPlaceActivities(cleared);
        }
        if (!withLinks.isEmpty()) {
            ingestMapper.deleteMissingPlaceActivities(withLinks);
            ingestMapper.upsertPlaceActivities(withLinks);
        }

        // updated 는 분류를 손댄 항목 수다. delete 와 upsert 두 문장이 나가서
        // 영향 행수를 더하면 "관계 몇 개를 건드렸나"가 되어 다른 경로와 뜻이
        // 달라진다. 여기서는 항목 수로 통일한다.
        return new IngestResultResponse(
                items.size(), 0, known.size(), items.size() - known.size());
    }

    /**
     * 분류 목록을 확인합니다.
     *
     * <p>대표 분류는 최대 하나입니다. 노출 기준이 대표 분류 하나라는 정책을
     * 그대로 지킵니다. 둘이 들어오면 화면이 무엇을 보여줄지 정할 수 없습니다.
     *
     * <p>빈 목록은 받습니다. 분류를 통째로 지우는 요청이기 때문입니다.
     * 다만 파이프라인은 분류 없는 항목을 보내지 않습니다 — 노출되려면 대표
     * 분류가 있어야 하므로 분류 없는 상태는 정상이 아니고, 수집이 잠깐 분류를
     * 못 붙인 항목의 기존 분류까지 지워지면 안 됩니다.
     */
    private static void validateActivities(ActivityIngestItem item) {
        requireValid(hasText(item.getPipelineId()));
        requireValid(item.getActivities() != null);

        long primaries = item.getActivities().stream()
                .filter(link -> Boolean.TRUE.equals(link.getIsPrimary()))
                .count();
        requireValid(primaries <= 1);

        item.getActivities().forEach(link -> {
            requireValid(link.getActivityId() != null);
            // is_primary 는 NOT NULL 이다. 빠진 채로 넣으면 배치 전체가 롤백되고
            // 500 이 나가, 파이프라인이 같은 배치를 계속 재시도한다.
            // 노출 기준이 대표 분류라는 정책상 빠뜨릴 값이 아니므로 거절한다.
            requireValid(link.getIsPrimary() != null);
        });
    }

    /**
     * 같은 키가 배치에 두 번 오면 마지막 값만 남는데 건수는 두 번 세어집니다.
     * 미리 하나로 줄입니다. 마지막 것을 남기는 이유는, 한 항목이 그 시점의
     * 전체 상태를 담고 있어 나중 것이 최신이기 때문입니다.
     *
     * <p>구분자는 콜론입니다. pipeline_id 는 UUID 라 콜론이 들어가지 않습니다.
     */
    private static <T> List<T> dedupeBy(List<T> items, Function<T, String> key) {
        return List.copyOf(items.stream()
                .collect(Collectors.toMap(key, it -> it, (first, last) -> last,
                        LinkedHashMap::new))
                .values());
    }

    /**
     * DB 제약과 같은 것을 미리 봅니다. 여기서 걸러야 4xx 로 나가고, 파이프라인이
     * 재시도 대상이 아니라고 판단할 수 있습니다.
     *
     * <p>확인하는 것은 SQLException 으로 터질 세 가지입니다.
     * <ul>
     *   <li>{@code start_date NOT NULL}
     *   <li>{@code chk_event_period} — 상시면 종료일이 없어야 하고,
     *       상시가 아니면 종료일이 있고 시작일 이하여야 한다
     *   <li>표시명이 비어 있지 않을 것
     * </ul>
     */
    private static void validateEvent(EventIngestItem item) {
        requireValid(hasText(item.getPipelineId()) && hasText(item.getTitle()));
        requireValid(item.getStartDate() != null);

        // isPermanent 는 필수입니다. start_date · end_date · is_permanent 는 한 덩어리
        // 사실이라, 일부만 "null 이면 기존값 유지"로 두면 셋이 찢어집니다.
        //
        // 찢어지면 이렇게 됩니다: 상시 이벤트에 isPermanent=null, endDate=2026-12-31 이
        // 오면 검증은 비상시로 보아 통과시키는데 UPDATE 는 상시를 유지해서,
        // is_permanent=TRUE 인데 end_date 가 있는 행이 되어 chk_event_period 를
        // 위반합니다. 크롤러가 이미 이 값을 정규화하므로 받아서 그대로 씁니다.
        requireValid(item.getIsPermanent() != null);

        if (item.getIsPermanent()) {
            requireValid(item.getEndDate() == null);
        } else {
            requireValid(item.getEndDate() != null
                    && !item.getStartDate().isAfter(item.getEndDate()));
        }

        requireCoordinatePair(item.getLatitude(), item.getLongitude());
    }

    /**
     * chk_event_coordinates · chk_place_coordinates 는 위도·경도가 둘 다 없거나
     * 둘 다 있어야 합니다. 한쪽만 오면 SQLException 이 500 으로 터지고 배치가
     * 재시도 불능이 됩니다.
     *
     * <p>범위 검사는 DB 에 맡깁니다. 쌍이 깨지는 것과 달리 파이프라인이 만들 수
     * 있는 값이 아닙니다.
     */
    private static void requireCoordinatePair(BigDecimal latitude, BigDecimal longitude) {
        requireValid((latitude == null) == (longitude == null));
    }

    /**
     * {@code chk_event_translations_language} 와 같은 목록입니다.
     * 어긋나면 배치 전체가 SQLException 으로 터집니다.
     */
    private static void requireLanguage(String languageCode) {
        requireValid(languageCode != null && LANGUAGES.contains(languageCode));
    }

    /**
     * 배치에 하나라도 어긋난 항목이 있으면 통째로 거절합니다.
     *
     * <p>일부만 넣고 성공으로 알리면 파이프라인이 넘어가 격차가 쌓입니다.
     * 필수값 누락과 제약 위반을 같은 코드로 냅니다. 파이프라인 입장에서는
     * 둘 다 "고쳐서 보내야 하는 것"이라 구분할 이유가 없습니다.
     */
    private static void requireValid(boolean condition) {
        if (!condition) {
            throw new IngestInvalidItemException();
        }
    }


    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * explore_items 의 새 id 를 명시적으로 발급합니다.
     *
     * <p>AUTO_INCREMENT 순서에 기대지 않습니다. explore_items 와 event/place 가
     * 같은 id 를 공유하므로 순서에 기대면 환경에 따라 매핑이 어긋납니다.
     *
     * <p>같은 트랜잭션 안에서 MAX 를 읽고 바로 쓰므로, 파이프라인이 하나만
     * 돌 때는 충돌하지 않습니다. 동시에 두 배치가 들어오면 PK 충돌로 한쪽이
     * 실패하고 그 배치만 재시도됩니다. 조용히 덮어쓰는 것보다 낫습니다.
     */
    private List<Long> reserveItemIds(int count) {
        long base = ingestMapper.findMaxExploreItemId();
        List<Long> ids = new ArrayList<>(count);
        for (int i = 1; i <= count; i += 1) {
            ids.add(base + i);
        }
        return ids;
    }

}
