package me.nawa.wallet.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.enums.TransferStatus;
import me.nawa.wallet.domain.enums.TransferType;

public record TransactionAppliedFilters(
    TransferType type,     // 실제 적용된 거래 종류 필터
    TransferStatus status, // 실제 적용된 거래 상태 필터
    LocalDate from,        // 실제 적용된 조회 시작일
    LocalDate to           // 실제 적용된 조회 종료일
) {
}
