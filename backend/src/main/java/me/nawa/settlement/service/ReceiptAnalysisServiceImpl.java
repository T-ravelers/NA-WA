package me.nawa.settlement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.ReceiptAnalysis;
import me.nawa.settlement.domain.ReceiptAnalysisItem;
import me.nawa.settlement.domain.ReceiptItemAllocation;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.ReceiptAllocationRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptItemRequest;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.response.ReceiptAnalysisItemResponse;
import me.nawa.settlement.dto.response.ReceiptAnalysisResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DuplicateKeyException;

/** 영수증 파일 선택부터 항목별 참여자 배분 확정까지의 상태 전이를 담당한다. */
@Service
@RequiredArgsConstructor
public class ReceiptAnalysisServiceImpl implements ReceiptAnalysisService {
    private final SettlementMapper settlementMapper;

    @Value("${settlement.receipt.allowed-content-types:image/jpeg,image/png,application/pdf}")
    private String allowedContentTypes = "image/jpeg,image/png,application/pdf";

    @Value("${settlement.receipt.allowed-extensions:jpg,jpeg,png,pdf}")
    private String allowedExtensions = "jpg,jpeg,png,pdf";

    @Value("${settlement.receipt.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes = 5L * 1024 * 1024;

    @Override @Transactional
    public ReceiptAnalysisResponse analyzeReceipt(Long memberId, Long sourceTransferId, MultipartFile file) {
        if (!isAllowedReceipt(file))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        SettlementSource source = settlementMapper.findSourceForCreate(sourceTransferId, memberId);
        if (source == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        ReceiptAnalysis existing = settlementMapper.findReceiptAnalysisBySourceAndCreatorForUpdate(
            sourceTransferId,
            memberId
        );
        if (existing != null) {
            return reuseDraftReceiptAnalysis(existing, file.getOriginalFilename());
        }
        ReceiptAnalysis analysis = new ReceiptAnalysis(null, sourceTransferId, source.getAppointmentId(), memberId,
            file.getOriginalFilename(), "DRAFT", BigDecimal.ZERO);
        try {
            settlementMapper.insertReceiptAnalysis(analysis);
        } catch (DuplicateKeyException exception) {
            ReceiptAnalysis concurrent = settlementMapper.findReceiptAnalysisBySourceAndCreatorForUpdate(
                sourceTransferId,
                memberId
            );
            if (concurrent == null) {
                throw exception;
            }
            return reuseDraftReceiptAnalysis(concurrent, file.getOriginalFilename());
        }
        return ReceiptAnalysisResponse.builder().receiptAnalysisId(analysis.getReceiptAnalysisId())
            .recognizedTotal(BigDecimal.ZERO).items(List.of()).build();
    }

    private ReceiptAnalysisResponse reuseDraftReceiptAnalysis(
        ReceiptAnalysis analysis,
        String originalFileName
    ) {
        if (!"DRAFT".equals(analysis.getAnalysisStatus())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_STATE_CONFLICT);
        }
        settlementMapper.deleteReceiptAllocations(analysis.getReceiptAnalysisId());
        settlementMapper.deleteReceiptItems(analysis.getReceiptAnalysisId());
        settlementMapper.resetDraftReceiptAnalysis(analysis.getReceiptAnalysisId(), originalFileName);
        return ReceiptAnalysisResponse.builder()
            .receiptAnalysisId(analysis.getReceiptAnalysisId())
            .recognizedTotal(BigDecimal.ZERO)
            .items(List.of())
            .build();
    }

    private boolean isAllowedReceipt(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > maxFileSizeBytes
            || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            || file.getContentType() == null) {
            return false;
        }
        String fileName = file.getOriginalFilename();
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(extensionSeparator + 1).toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> extensions = java.util.Arrays.stream(allowedExtensions.split(","))
            .map(String::trim)
            .map(value -> value.toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> contentTypes = java.util.Arrays.stream(allowedContentTypes.split(","))
            .map(String::trim)
            .map(value -> value.toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        return extensions.contains(extension)
            && contentTypes.contains(file.getContentType().toLowerCase(java.util.Locale.ROOT));
    }

    @Override @Transactional
    public ReceiptAnalysisResponse updateReceiptItems(Long memberId, Long receiptAnalysisId, ReceiptItemUpdateRequest request) {
        ReceiptAnalysis analysis = requireDraftOwner(memberId, receiptAnalysisId);
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        List<ReceiptAnalysisItem> items = new ArrayList<>(); short order = 1;
        for (ReceiptItemRequest requestItem : request.getItems()) {
            if (requestItem == null || requestItem.getName() == null || requestItem.getName().isBlank()
                || requestItem.getQuantity() == null || requestItem.getQuantity().signum() <= 0
                || requestItem.getUnitPrice() == null || requestItem.getUnitPrice().signum() < 0)
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
            items.add(new ReceiptAnalysisItem(null, receiptAnalysisId, requestItem.getName().trim(), requestItem.getUnitPrice(),
                requestItem.getQuantity(), requestItem.getUnitPrice().multiply(requestItem.getQuantity()), order++));
        }
        BigDecimal total = items.stream().map(ReceiptAnalysisItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        settlementMapper.deleteReceiptAllocations(receiptAnalysisId);
        settlementMapper.deleteReceiptItems(receiptAnalysisId);
        settlementMapper.insertReceiptItems(items);
        settlementMapper.updateReceiptTotal(receiptAnalysisId, total);
        return ReceiptAnalysisResponse.builder().receiptAnalysisId(analysis.getReceiptAnalysisId()).recognizedTotal(total)
            .items(items.stream().map(item -> ReceiptAnalysisItemResponse.builder().id(item.getReceiptAnalysisItemId())
                .name(item.getItemName()).quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).build()).toList()).build();
    }

    @Override @Transactional
    public void updateReceiptAllocations(Long memberId, Long receiptAnalysisId, ReceiptAllocationUpdateRequest request) {
        ReceiptAnalysis analysis = requireDraftOwner(memberId, receiptAnalysisId);
        if (request == null || request.getAllocations() == null || request.getAllocations().isEmpty())
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        Map<Long, ReceiptAnalysisItem> items = settlementMapper.findReceiptItemsForUpdate(receiptAnalysisId).stream()
            .collect(java.util.stream.Collectors.toMap(ReceiptAnalysisItem::getReceiptAnalysisItemId, item -> item));
        java.util.Set<Long> appointmentMembers = settlementMapper.findActiveMembers(analysis.getAppointmentId()).stream()
            .map(SettlementMember::getAppointmentMemberId)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, BigDecimal> quantities = new HashMap<>(); List<ReceiptItemAllocation> allocations = new ArrayList<>();
        java.util.Set<String> allocationKeys = new java.util.HashSet<>();
        for (ReceiptAllocationRequest allocation : request.getAllocations()) {
            ReceiptAnalysisItem item = allocation == null ? null : items.get(allocation.getItemId());
            Long appointmentMemberId = allocation == null ? null : allocation.getAppointmentMemberId();
            String allocationKey = allocation == null ? null
                : allocation.getItemId() + ":" + appointmentMemberId;
            if (item == null || appointmentMemberId == null || !appointmentMembers.contains(appointmentMemberId)
                || allocation.getQuantity() == null || allocation.getQuantity().signum() <= 0
                || !allocationKeys.add(allocationKey)) {
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
            }
            quantities.merge(item.getReceiptAnalysisItemId(), allocation.getQuantity(), BigDecimal::add);
            allocations.add(new ReceiptItemAllocation(item.getReceiptAnalysisItemId(), appointmentMemberId, allocation.getQuantity(),
                item.getUnitPrice().multiply(allocation.getQuantity())));
        }
        if (items.size() != quantities.size() || items.values().stream().anyMatch(item ->
            item.getQuantity().compareTo(quantities.getOrDefault(item.getReceiptAnalysisItemId(), BigDecimal.ZERO)) != 0))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        settlementMapper.deleteReceiptAllocations(receiptAnalysisId); settlementMapper.insertReceiptAllocations(allocations);
        settlementMapper.markReceiptAllocated(receiptAnalysisId);
    }

    private ReceiptAnalysis requireDraftOwner(Long memberId, Long receiptAnalysisId) {
        ReceiptAnalysis analysis = settlementMapper.findReceiptAnalysisForUpdate(receiptAnalysisId);
        if (analysis == null || !memberId.equals(analysis.getCreatedByMemberId()) || !"DRAFT".equals(analysis.getAnalysisStatus()))
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        return analysis;
    }
}
