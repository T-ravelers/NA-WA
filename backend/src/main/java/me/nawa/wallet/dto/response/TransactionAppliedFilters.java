package me.nawa.wallet.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.enums.TransferStatus;
import me.nawa.wallet.domain.enums.TransferType;

public record TransactionAppliedFilters(
    TransferType type,
    TransferStatus status,
    LocalDate from,
    LocalDate to
) {
}
