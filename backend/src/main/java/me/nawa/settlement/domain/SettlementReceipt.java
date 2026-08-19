package me.nawa.settlement.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정산에 붙일 영수증 사진이 S3 어디에 있는지 가리키는 행이다.
 *
 * settlementId가 비어 있으면 아직 정산에 붙지 않은 "초안"이다. 사용자가 사진을 먼저 올리고
 * 품목을 확인한 뒤 정산을 만들 때 이 값이 채워진다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SettlementReceipt {
    private Long settlementReceiptId;
    private Long uploadedByMemberId;
    private Long settlementId;
    private String objectKey;
    private String contentType;
    private Integer byteSize;

    public SettlementReceipt(
        Long uploadedByMemberId,
        String objectKey,
        String contentType,
        Integer byteSize
    ) {
        this.uploadedByMemberId = uploadedByMemberId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.byteSize = byteSize;
    }
}
