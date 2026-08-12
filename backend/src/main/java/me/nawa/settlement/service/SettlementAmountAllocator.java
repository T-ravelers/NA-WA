package me.nawa.settlement.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.exception.SettlementErrorCode;
import org.springframework.stereotype.Component;

/** 금액을 통화 최소 단위로 나누고 나머지를 참가 행 순서로 배분한다. */
@Component
public class SettlementAmountAllocator {

    public Map<Long, BigDecimal> allocate(
        BigDecimal total,
        List<Long> appointmentMemberIds,
        int decimalPlaces
    ) {
        if (total == null || total.signum() < 0 || appointmentMemberIds == null
            || appointmentMemberIds.isEmpty() || decimalPlaces < 0
            || appointmentMemberIds.stream().anyMatch(java.util.Objects::isNull)
            || appointmentMemberIds.stream().distinct().count() != appointmentMemberIds.size()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }

        BigInteger minorUnits;
        try {
            minorUnits = total.movePointRight(decimalPlaces).toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID, exception);
        }

        List<Long> sortedIds = appointmentMemberIds.stream().sorted().toList();
        BigInteger[] quotientAndRemainder = minorUnits.divideAndRemainder(
            BigInteger.valueOf(sortedIds.size())
        );
        int remainder = quotientAndRemainder[1].intValueExact();
        Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
        for (int index = 0; index < sortedIds.size(); index++) {
            BigInteger memberUnits = quotientAndRemainder[0];
            if (index < remainder) {
                memberUnits = memberUnits.add(BigInteger.ONE);
            }
            allocations.put(
                sortedIds.get(index),
                new BigDecimal(memberUnits, decimalPlaces)
            );
        }
        return allocations;
    }
}
