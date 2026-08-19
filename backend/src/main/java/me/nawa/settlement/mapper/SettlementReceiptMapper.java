package me.nawa.settlement.mapper;

import me.nawa.settlement.domain.SettlementReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 영수증 사진의 위치를 저장하고 정산에 연결하는 영속성 계약이다. */
@Mapper
public interface SettlementReceiptMapper {
    void insertReceipt(SettlementReceipt receipt);

    /**
     * 초안을 정산에 연결한다.
     *
     * 조건을 WHERE에 모아 둔 이유는, 두 요청이 같은 초안을 동시에 연결하려 해도 한쪽만
     * 성공시키기 위해서다. 반영된 행 수가 1이 아니면 남의 초안이거나 이미 쓰인 초안이다.
     */
    int linkToSettlement(
        @Param("settlementReceiptId") Long settlementReceiptId,
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    /** 정산 참여자에게만 영수증을 돌려준다. 참여자가 아니면 null이다. */
    SettlementReceipt findBySettlementIdForViewer(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );
}
