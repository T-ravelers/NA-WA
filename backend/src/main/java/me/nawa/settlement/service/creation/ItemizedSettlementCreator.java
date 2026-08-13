package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementItem;
import me.nawa.settlement.domain.SettlementItemShare;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemAllocationRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Component;

/** 클라이언트가 입력한 품목과 수량 배분으로 항목별 정산 스냅샷을 생성한다. */
@Component
@RequiredArgsConstructor
public class ItemizedSettlementCreator implements SettlementCreationHandler {
    private static final int ITEM_NAME_MAX_LENGTH = 200;
    private static final int AMOUNT_PRECISION = 19;
    private static final int AMOUNT_SCALE = 4;
    private static final int QUANTITY_PRECISION = 12;
    private static final int QUANTITY_SCALE = 3;

    private final SettlementMapper settlementMapper;

    @Override
    public String getType() {
        return "ITEMIZED";
    }

    @Override
    public SettlementCreateResponse create(
        Long memberId,
        CreateSettlementRequest request,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint
    ) {
        if (source.getCurrencyDecimalPlaces() == null || source.getCurrencyDecimalPlaces() < 0) {
            throw invalid();
        }
        Map<Long, SettlementMember> activeMembers = activeMembers(source.getAppointmentId());
        Set<Long> requestedIds = Set.copyOf(request.getParticipantAppointmentMemberIds());
        if (!activeMembers.keySet().containsAll(requestedIds)) {
            throw invalid();
        }
        ItemizedSnapshot snapshot = calculateSnapshot(
            request.getItems(), requestedIds, source.getCurrencyDecimalPlaces()
        );
        if (source.getAmount().compareTo(snapshot.totalAmount()) != 0) {
            throw invalid();
        }
        requireCurrencyUnit(source.getAmount(), source.getCurrencyDecimalPlaces());

        List<SettlementMember> members = requestedIds.stream()
            .sorted()
            .map(activeMembers::get)
            .peek(member -> member.setShareAmount(snapshot.memberAmounts()
                .getOrDefault(member.getAppointmentMemberId(), BigDecimal.ZERO)))
            .toList();
        EqualSettlementCreator.validatePayerAndPendingAmounts(members, source.getPayerMemberId());
        BigDecimal payerShare = members.stream()
            .filter(member -> source.getPayerMemberId().equals(member.getMemberId()))
            .map(SettlementMember::getShareAmount)
            .findFirst()
            .orElseThrow(this::invalid);
        Settlement settlement = newSettlement(
            memberId, source, idempotencyKey, requestFingerprint, payerShare
        );
        settlementMapper.insertSettlement(settlement);
        members.forEach(member -> {
            member.setSettlementId(settlement.getSettlementId());
            member.setRequestStatus(source.getPayerMemberId().equals(member.getMemberId())
                ? "NOT_REQUESTED" : "PENDING");
        });
        settlementMapper.insertSettlementMembers(members);
        insertItemSnapshot(settlement.getSettlementId(), snapshot.items());
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }

    private Map<Long, SettlementMember> activeMembers(Long appointmentId) {
        Map<Long, SettlementMember> members = new HashMap<>();
        for (SettlementMember member : settlementMapper.findActiveMembers(appointmentId)) {
            if (members.put(member.getAppointmentMemberId(), member) != null) {
                throw invalid();
            }
        }
        return members;
    }

    private ItemizedSnapshot calculateSnapshot(
        List<ItemizedSettlementItemRequest> requests,
        Set<Long> requestedIds,
        int decimalPlaces
    ) {
        if (requests == null || requests.isEmpty() || requests.size() > Short.MAX_VALUE) {
            throw invalid();
        }
        BigDecimal total = BigDecimal.ZERO;
        Map<Long, BigDecimal> memberAmounts = new HashMap<>();
        List<ItemSnapshot> items = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < requests.size(); itemIndex++) {
            ItemizedSettlementItemRequest request = requests.get(itemIndex);
            if (request == null || invalidItemName(request.getName())
                || invalidAmount(request.getUnitPrice()) || request.getUnitPrice().signum() < 0
                || invalidQuantity(request.getQuantity()) || request.getQuantity().signum() <= 0
                || request.getAllocations() == null || request.getAllocations().isEmpty()) {
                throw invalid();
            }
            requireCurrencyUnit(request.getUnitPrice(), decimalPlaces);
            BigDecimal lineTotal = request.getUnitPrice().multiply(request.getQuantity());
            if (invalidAmount(lineTotal)) {
                throw invalid();
            }
            requireCurrencyUnit(lineTotal, decimalPlaces);
            BigDecimal allocatedQuantity = BigDecimal.ZERO;
            Set<Long> allocatedIds = new HashSet<>();
            List<AllocationSnapshot> allocations = new ArrayList<>();
            for (ItemizedSettlementItemAllocationRequest allocation : request.getAllocations()) {
                if (allocation == null || allocation.getAppointmentMemberId() == null
                    || invalidQuantity(allocation.getQuantity())
                    || allocation.getQuantity().signum() <= 0
                    || !requestedIds.contains(allocation.getAppointmentMemberId())
                    || !allocatedIds.add(allocation.getAppointmentMemberId())) {
                    throw invalid();
                }
                BigDecimal allocatedAmount = request.getUnitPrice().multiply(allocation.getQuantity());
                if (invalidAmount(allocatedAmount)) {
                    throw invalid();
                }
                requireCurrencyUnit(allocatedAmount, decimalPlaces);
                allocatedQuantity = allocatedQuantity.add(allocation.getQuantity());
                memberAmounts.merge(
                    allocation.getAppointmentMemberId(), allocatedAmount, BigDecimal::add
                );
                allocations.add(new AllocationSnapshot(
                    allocation.getAppointmentMemberId(), allocation.getQuantity(), allocatedAmount
                ));
            }
            if (allocatedQuantity.compareTo(request.getQuantity()) != 0
                || lineTotal.compareTo(allocations.stream().map(AllocationSnapshot::allocatedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)) != 0) {
                throw invalid();
            }
            total = total.add(lineTotal);
            items.add(new ItemSnapshot(
                request.getName(), request.getUnitPrice(), request.getQuantity(), lineTotal,
                (short) itemIndex,
                allocations.stream().sorted(Comparator.comparing(AllocationSnapshot::appointmentMemberId)).toList()
            ));
        }
        requireCurrencyUnit(total, decimalPlaces);
        if (total.compareTo(memberAmounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)) != 0) {
            throw invalid();
        }
        return new ItemizedSnapshot(total, memberAmounts, items);
    }

    private void insertItemSnapshot(Long settlementId, List<ItemSnapshot> items) {
        for (ItemSnapshot snapshot : items) {
            SettlementItem item = new SettlementItem(
                settlementId,
                snapshot.name(),
                snapshot.unitPrice(),
                snapshot.quantity(),
                snapshot.lineTotal(),
                snapshot.sourceOrder()
            );
            settlementMapper.insertSettlementItem(item);
            if (item.getSettlementItemId() == null) {
                throw new IllegalStateException("settlement item id was not generated");
            }
            settlementMapper.insertSettlementItemShares(settlementId, snapshot.allocations().stream()
                .map(allocation -> new SettlementItemShare(
                    null,
                    item.getSettlementItemId(),
                    allocation.appointmentMemberId(),
                    allocation.allocatedQuantity(),
                    allocation.allocatedAmount()
                ))
                .toList());
        }
    }

    private Settlement newSettlement(
        Long memberId,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint,
        BigDecimal payerShare
    ) {
        return Settlement.builder()
            .appointmentId(source.getAppointmentId())
            .createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId())
            .sourceTransferId(source.getTransferId())
            .idempotencyKey(idempotencyKey)
            .requestFingerprint(requestFingerprint)
            .settlementStatus("REQUESTED")
            .splitMethod(getType())
            .totalAmount(source.getAmount())
            .payerShareAmount(payerShare)
            .receivableAmount(source.getAmount().subtract(payerShare))
            .build();
    }

    private void requireCurrencyUnit(BigDecimal amount, int decimalPlaces) {
        try {
            amount.movePointRight(decimalPlaces).toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private boolean invalidItemName(String name) {
        return name == null || name.isBlank() || name.length() > ITEM_NAME_MAX_LENGTH;
    }

    private boolean invalidAmount(BigDecimal amount) {
        return amount == null || amount.scale() > AMOUNT_SCALE
            || amount.precision() - amount.scale() > AMOUNT_PRECISION - AMOUNT_SCALE;
    }

    private boolean invalidQuantity(BigDecimal quantity) {
        return quantity == null || quantity.scale() > QUANTITY_SCALE
            || quantity.precision() - quantity.scale() > QUANTITY_PRECISION - QUANTITY_SCALE;
    }

    private BusinessException invalid() {
        return new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
    }

    private record ItemizedSnapshot(
        BigDecimal totalAmount,
        Map<Long, BigDecimal> memberAmounts,
        List<ItemSnapshot> items
    ) {
    }

    private record ItemSnapshot(
        String name,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal lineTotal,
        short sourceOrder,
        List<AllocationSnapshot> allocations
    ) {
    }

    private record AllocationSnapshot(
        Long appointmentMemberId,
        BigDecimal allocatedQuantity,
        BigDecimal allocatedAmount
    ) {
    }
}
