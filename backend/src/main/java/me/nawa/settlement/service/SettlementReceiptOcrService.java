package me.nawa.settlement.service;

import me.nawa.settlement.dto.response.SettlementReceiptOcrResponse;

/** 이미 올려 둔 영수증 사진에서 정산 품목 초안을 읽어낸다. */
public interface SettlementReceiptOcrService {

    /**
     * 자기가 올린 초안 영수증을 글자 인식에 보내고 품목 초안을 돌려준다.
     *
     * 결과는 어디에도 저장하지 않는다. 사용자가 확인·수정한 값만 정산 생성 요청으로 올라와
     * 저장된다.
     */
    SettlementReceiptOcrResponse recognize(Long memberId, Long receiptId);
}
