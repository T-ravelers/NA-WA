package me.nawa.ingest.service;

import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.request.PlaceTranslationIngestItem;
import me.nawa.ingest.dto.response.IngestResultResponse;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.ingest.mapper.IngestMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 파이프라인이 보낸 항목을 운영 테이블에 반영합니다.
 *
 * <p>한 요청이 한 트랜잭션입니다. 중간에 실패하면 그 배치는 통째로 되돌아가고,
 * 파이프라인의 job 은 큐에 남아 다음 회차에 다시 시도합니다. 절반만 들어간
 * 상태로 남는 경우가 없어야 리포트의 건수를 믿을 수 있습니다.
 */
@Service
public class IngestServiceImpl implements IngestService {

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

        items.forEach(it -> require(hasText(it.getPipelineId()) && hasText(it.getTitle())));

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

        items.forEach(it -> require(hasText(it.getPipelineId()) && hasText(it.getName())));

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
        items.forEach(it -> require(hasText(it.getPipelineId()) && hasText(it.getLanguageCode())));

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
        items.forEach(it -> require(hasText(it.getPipelineId()) && hasText(it.getLanguageCode())));

        Set<String> existing = new HashSet<>(ingestMapper.findExistingPlacePipelineIds(
                items.stream().map(PlaceTranslationIngestItem::getPipelineId).distinct().toList()));
        int skipped = (int) items.stream()
                .filter(it -> !existing.contains(it.getPipelineId()))
                .count();

        ingestMapper.upsertPlaceTranslations(items);
        return new IngestResultResponse(items.size(), 0, items.size() - skipped, skipped);
    }


    /**
     * 적재 키와 표시명이 없는 항목은 받지 않습니다.
     *
     * <p>한 건이라도 비어 있으면 배치 전체를 거절합니다. 일부만 넣고 나머지를
     * 조용히 버리면 파이프라인이 성공으로 알고 넘어가 격차가 누적됩니다.
     */
    private static void require(boolean condition) {
        if (!condition) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
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
