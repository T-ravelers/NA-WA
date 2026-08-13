package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class SettlementAmountAllocatorTest {

    private final SettlementAmountAllocator allocator = new SettlementAmountAllocator();

    @Test
    void allocate_krwRemainder_assignsMinimumUnitsByAppointmentMemberId() {
        Map<Long, BigDecimal> allocations = allocator.allocate(
            new BigDecimal("100"),
            List.of(73L, 71L, 72L),
            0
        );

        assertEquals(
            Map.of(
                71L, new BigDecimal("34"),
                72L, new BigDecimal("33"),
                73L, new BigDecimal("33")
            ),
            allocations
        );
    }

    @Test
    void allocate_decimalCurrency_preservesExactTotal() {
        Map<Long, BigDecimal> allocations = allocator.allocate(
            new BigDecimal("10.01"),
            List.of(21L, 22L, 23L),
            2
        );

        assertEquals(new BigDecimal("3.34"), allocations.get(21L));
        assertEquals(new BigDecimal("3.34"), allocations.get(22L));
        assertEquals(new BigDecimal("3.33"), allocations.get(23L));
        assertEquals(
            new BigDecimal("10.01"),
            allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @Test
    void allocate_amountBelowCurrencyUnit_throwsInvalidCreation() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
            allocator.allocate(new BigDecimal("100.5"), List.of(71L, 72L), 0)
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }
}
