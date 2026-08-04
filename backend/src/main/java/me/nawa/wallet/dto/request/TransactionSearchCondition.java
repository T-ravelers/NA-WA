package me.nawa.wallet.dto.request;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.wallet.domain.enums.TransferStatus;
import me.nawa.wallet.domain.enums.TransferType;

@Getter
@Setter
@NoArgsConstructor
public class TransactionSearchCondition {

    private TransferType type;
    private TransferStatus status;
    private LocalDate from;
    private LocalDate to;
    private Long cursor;
    private Integer size;
}
