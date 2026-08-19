package me.nawa.settlement.service;

import me.nawa.common.storage.StoredReceipt;
import me.nawa.settlement.dto.response.SettlementReceiptUploadResponse;

/** 영수증 사진의 업로드, 정산 연결과 조회를 담당한다. */
public interface SettlementReceiptService {

    /** 사진을 저장소에 올리고 아직 정산에 붙지 않은 초안으로 기록한다. */
    SettlementReceiptUploadResponse upload(
        Long memberId,
        String declaredContentType,
        byte[] content
    );

    /**
     * 초안을 정산에 연결한다.
     *
     * 정산을 만드는 트랜잭션 안에서 호출한다. 정산과 사진이 함께 남거나 함께 없어야 하기
     * 때문이다.
     */
    void linkToSettlement(Long memberId, Long settlementId, Long receiptId);

    /** 정산에 붙은 사진을 돌려준다. 정산 참여자만 볼 수 있다. */
    StoredReceipt getReceipt(Long memberId, Long settlementId);
}
