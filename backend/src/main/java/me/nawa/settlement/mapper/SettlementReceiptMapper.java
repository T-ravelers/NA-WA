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

    /**
     * 아직 정산에 붙지 않은 자기 초안을 돌려준다. 남의 초안이거나 이미 쓰인 초안이면 null이다.
     *
     * 글자 인식은 정산을 만들기 전 단계에서만 쓴다. 이미 정산에 붙은 사진을 다시 읽어 봐야
     * 품목은 확정된 뒤라 쓸 데가 없고, 인식 호출은 부를 때마다 요금이 나간다.
     */
    SettlementReceipt findDraftForUploader(
        @Param("settlementReceiptId") Long settlementReceiptId,
        @Param("memberId") Long memberId
    );

    /**
     * 정산 참여자에게만 영수증을 돌려준다. 참여자가 아니면 null이다.
     *
     * 누가 참여자인지 판단하는 조건은 {@code SettlementMapper.findDetail}과 같다. 참여자
     * 판정 규칙이 바뀌면 두 쿼리를 함께 고쳐야 한다.
     */
    SettlementReceipt findBySettlementIdForViewer(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    /**
     * 저장소에서 사라진 사진의 행을 만료로 표시한다.
     *
     * 사진은 보관 기한이 지나면 저장소가 알아서 지우는데, 지웠다고 알려주지는 않는다.
     * 그래서 조회하다 "그런 파일 없다"는 답을 받은 그 순간을 삭제 신호로 삼는다. 언제
     * 사라졌는지가 기록으로 남아야 나중에 "원래 없었는지, 지워졌는지"를 구분할 수 있다.
     */
    int markExpired(@Param("settlementReceiptId") Long settlementReceiptId);
}
