package me.nawa.settlement.service;

import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.response.ReceiptAnalysisResponse;
import org.springframework.web.multipart.MultipartFile;

/** 영수증 분석의 생성, 항목 수정, 배분 확정을 정의한다. */
public interface ReceiptAnalysisService {
    ReceiptAnalysisResponse analyzeReceipt(Long memberId, Long sourceTransferId, MultipartFile file);
    ReceiptAnalysisResponse updateReceiptItems(Long memberId, Long receiptAnalysisId, ReceiptItemUpdateRequest request);
    void updateReceiptAllocations(Long memberId, Long receiptAnalysisId, ReceiptAllocationUpdateRequest request);
}
