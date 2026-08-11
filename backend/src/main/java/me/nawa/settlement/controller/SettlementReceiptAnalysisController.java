package me.nawa.settlement.controller;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.response.ReceiptAnalysisResponse;
import me.nawa.settlement.service.SettlementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 영수증 정산 분석 API
 *
 * 영수증 분석 결과의 생성, 항목 수정 및 참여자별 배분 확정 요청을 처리합니다.
 */
@RestController
@RequestMapping("/api/v1/settlements/receipt-analyses")
@RequiredArgsConstructor
public class SettlementReceiptAnalysisController {

    private final SettlementService settlementService;

    /**
     * 영수증 분석
     *
     * 원거래와 업로드한 영수증 파일을 바탕으로 인식한 항목과 합계를 반환합니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReceiptAnalysisResponse> analyzeReceipt(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestParam Long sourceTransferId,
        @RequestPart MultipartFile file
    ) {
        return ApiResponse.success(settlementService.analyzeReceipt(
            member.getMemberId(), sourceTransferId, file
        ));
    }

    /**
     * 영수증 항목 수정
     *
     * 인식된 영수증 항목의 이름, 수량 또는 단가를 수정하고 갱신된 결과를 반환합니다.
     */
    @PutMapping("/{receiptAnalysisId}/items")
    public ApiResponse<ReceiptAnalysisResponse> updateReceiptItems(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long receiptAnalysisId,
        @RequestBody ReceiptItemUpdateRequest request
    ) {
        return ApiResponse.success(settlementService.updateReceiptItems(
            member.getMemberId(), receiptAnalysisId, request
        ));
    }

    /**
     * 영수증 항목 배분 확정
     *
     * 항목별 참여자 수량 배분을 저장해 영수증 기반 정산 생성에 사용합니다.
     */
    @PutMapping("/{receiptAnalysisId}/allocations")
    public ApiResponse<Void> updateReceiptAllocations(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long receiptAnalysisId,
        @RequestBody ReceiptAllocationUpdateRequest request
    ) {
        settlementService.updateReceiptAllocations(
            member.getMemberId(), receiptAnalysisId, request
        );
        return ApiResponse.success();
    }
}
