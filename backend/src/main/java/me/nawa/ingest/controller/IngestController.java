package me.nawa.ingest.controller;

import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.ingest.dto.request.ActivityIngestItem;
import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.request.PlaceTranslationIngestItem;
import me.nawa.ingest.dto.response.IngestResultResponse;
import me.nawa.ingest.exception.IngestBatchTooLargeException;
import me.nawa.ingest.exception.IngestForbiddenException;
import me.nawa.ingest.service.IngestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 크롤러 파이프라인이 수집·번역 결과를 밀어 넣는 곳입니다.
 *
 * <p>운영 MySQL 은 외부 포트가 닫혀 있어 파이프라인이 직접 붙을 수 없습니다.
 * 백엔드를 거치면 스키마 계약이 한곳에 모이고, 적재 결과를 응답으로 돌려줄 수
 * 있어 파이프라인 리포트에 그대로 실립니다.
 *
 * <p>SYSTEM 계정만 호출할 수 있습니다. 사람 계정의 토큰으로는 들어올 수 없습니다.
 */
@RestController
@RequestMapping("/api/v1/internal/ingest")
public class IngestController {

    /**
     * 한 요청의 상한입니다. 트랜잭션 하나가 길어지면 운영 조회가 함께 느려집니다.
     * 파이프라인은 나눠 보냅니다.
     */
    private static final int MAX_BATCH_SIZE = 500;

    private final IngestService ingestService;
    private final long pipelineMemberId;

    public IngestController(
            IngestService ingestService,
            @Value("${auth.service.pipeline-member-id:1000000}") long pipelineMemberId) {
        this.ingestService = ingestService;
        this.pipelineMemberId = pipelineMemberId;
    }

    @PostMapping("/events")
    public ApiResponse<IngestResultResponse> ingestEvents(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody List<EventIngestItem> items) {
        guard(member, items.size());
        return ApiResponse.success(ingestService.ingestEvents(items));
    }

    @PostMapping("/places")
    public ApiResponse<IngestResultResponse> ingestPlaces(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody List<PlaceIngestItem> items) {
        guard(member, items.size());
        return ApiResponse.success(ingestService.ingestPlaces(items));
    }

    @PostMapping("/event-translations")
    public ApiResponse<IngestResultResponse> ingestEventTranslations(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody List<EventTranslationIngestItem> items) {
        guard(member, items.size());
        return ApiResponse.success(ingestService.ingestEventTranslations(items));
    }

    @PostMapping("/place-translations")
    public ApiResponse<IngestResultResponse> ingestPlaceTranslations(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody List<PlaceTranslationIngestItem> items) {
        guard(member, items.size());
        return ApiResponse.success(ingestService.ingestPlaceTranslations(items));
    }

    @PostMapping("/event-activities")
    public ApiResponse<IngestResultResponse> ingestEventActivities(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody List<ActivityIngestItem> items) {
        guard(member, items.size());
        return ApiResponse.success(ingestService.ingestEventActivities(items));
    }

    @PostMapping("/place-activities")
    public ApiResponse<IngestResultResponse> ingestPlaceActivities(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody List<ActivityIngestItem> items) {
        guard(member, items.size());
        return ApiResponse.success(ingestService.ingestPlaceActivities(items));
    }

    /**
     * 호출자와 배치 크기를 확인합니다.
     *
     * <p>회원 ID 로 판별합니다. 이 경로의 토큰은 service-token 이 하나의 ID 로만
     * 발급하므로, 그 ID 가 아니면 사람 계정의 토큰입니다.
     */
    private void guard(AuthenticatedMember member, int size) {
        if (member == null || member.getMemberId() != pipelineMemberId) {
            throw new IngestForbiddenException();
        }
        if (size > MAX_BATCH_SIZE) {
            throw new IngestBatchTooLargeException();
        }
    }
}
