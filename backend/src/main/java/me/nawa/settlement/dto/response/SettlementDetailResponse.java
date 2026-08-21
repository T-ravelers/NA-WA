package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 상세 응답
 *
 * 정산의 금액, 진행 상태, 원거래와 항목 및 결제자 정보를 반환합니다.
 */
@Getter
@Builder
public class SettlementDetailResponse {

    private final Long id;
    private final String type;
    private final BigDecimal totalAmount;
    private final String status;
    private final String requestedBy;
    private final String gatheringName;
    private final String merchantName;
    private final List<SettlementViewerItemResponse> viewerItems;
    private final String transactionId;
    private final String paidBy;
    private final SettlementViewerResponse viewer;

    /**
     * 누가 냈는지의 목록. 돈을 받을 원결제자에게만 채워 주고 그 밖에는 null이다.
     *
     * 빈 목록 대신 null인 것은 "볼 수 없다"와 "받을 사람이 없다"를 구분하기 위해서다.
     */
    private final SettlementCollectionResponse collection;
}
