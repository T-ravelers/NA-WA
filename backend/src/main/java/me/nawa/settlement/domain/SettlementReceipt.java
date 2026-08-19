package me.nawa.settlement.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정산에 붙일 영수증 사진이 S3 어디에 있는지 가리키는 행이다.
 *
 * settlementId가 비어 있으면 아직 정산에 붙지 않은 "초안"이다. 사용자가 사진을 먼저 올리고
 * 품목을 확인한 뒤 정산을 만들 때 이 값이 채워진다.
 *
 * deletedAt이 채워져 있으면 보관 기한이 지나 사진이 저장소에서 사라진 것이다. 행을 지우지
 * 않고 남겨 두는 이유는, 사진이 사라졌다는 사실과 그것을 알아챈 시각 자체가 기록이기
 * 때문이다. 이 값이 있어야 "처음부터 안 붙였다"와 "붙였는데 사라졌다"를 구분할 수 있다.
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
    private LocalDateTime deletedAt;

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
