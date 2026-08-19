package me.nawa.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementReceiptUploadResponse {
    private final Long receiptId;
}
