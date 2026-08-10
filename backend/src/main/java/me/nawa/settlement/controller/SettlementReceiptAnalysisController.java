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

@RestController
@RequestMapping("/api/v1/settlements/receipt-analyses")
@RequiredArgsConstructor
public class SettlementReceiptAnalysisController {

    private final SettlementService settlementService;

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
