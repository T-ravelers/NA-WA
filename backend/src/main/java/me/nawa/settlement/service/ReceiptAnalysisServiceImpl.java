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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 영수증 파일 선택부터 항목별 참여자 배분 확정까지의 상태 전이를 담당한다. */
@Service
@RequiredArgsConstructor
public class ReceiptAnalysisServiceImpl implements ReceiptAnalysisService {
    private final SettlementMapper settlementMapper;

    @Override @Transactional
    public ReceiptAnalysisResponse analyzeReceipt(Long memberId, Long sourceTransferId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        SettlementSource source = settlementMapper.findSourceForCreate(sourceTransferId, memberId);
        if (source == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        ReceiptAnalysis analysis = new ReceiptAnalysis(null, sourceTransferId, source.getAppointmentId(), memberId,
            file.getOriginalFilename(), "DRAFT", BigDecimal.ZERO);
        settlementMapper.insertReceiptAnalysis(analysis);
        return ReceiptAnalysisResponse.builder().receiptAnalysisId(analysis.getReceiptAnalysisId())
            .recognizedTotal(BigDecimal.ZERO).items(List.of()).build();
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
        settlementMapper.deleteReceiptItems(receiptAnalysisId); settlementMapper.insertReceiptItems(items);
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
        Map<Long, Long> appointmentMembers = settlementMapper.findActiveMembers(analysis.getAppointmentId()).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementMember::getMemberId, SettlementMember::getAppointmentMemberId));
        Map<Long, BigDecimal> quantities = new HashMap<>(); List<ReceiptItemAllocation> allocations = new ArrayList<>();
        for (ReceiptAllocationRequest allocation : request.getAllocations()) {
            ReceiptAnalysisItem item = allocation == null ? null : items.get(allocation.getItemId());
            Long appointmentMemberId = allocation == null ? null : appointmentMembers.get(allocation.getParticipantId());
            if (item == null || appointmentMemberId == null || allocation.getQuantity() == null || allocation.getQuantity().signum() <= 0)
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
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
