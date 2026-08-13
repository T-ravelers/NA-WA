package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.WalletTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTransferMapper {

    WalletTransfer findByTransferId(@Param("transferId") Long transferId);

    // insert 후 MyBatis가 채워준 자동증가 PK를 transfer.transferId에 넣어준다 (WalletTransferMapper.xml의 useGeneratedKeys 참고)
    void insert(WalletTransfer transfer);

    // 멱등성 조회. idempotency_key는 NULL을 허용하는 유니크 컬럼이라, InnoDB가 "유니크 인덱스
    // 검색이 정확히 한 행을 특정하면 갭 락 없이 레코드 락만 건다"는 최적화를 이 컬럼에는
    // 적용하지 못한다 — 그래서 이 메서드는 절대 FOR UPDATE로 잠금 조회하지 않는다(과거에
    // 그렇게 시도했다가 서로 다른 QR을 같은 key로 동시 실행할 때 갭 락과 지갑 FOR UPDATE 락이
    // 얽혀 교착 상태에 빠지는 걸 QrPaymentConcurrencyIntegrationTest로 확인했다).
    //
    // 동시 요청 사이의 최종 승자 판정은 항상 insert의 유니크 제약(DuplicateKeyException)에
    // 맡긴다. 이 조회는 그 판정 전후로 호출되는데, QrPaymentServiceImpl.executePayment가
    // READ COMMITTED로 실행되므로 매 statement가 최신 커밋을 다시 읽어 별도의 잠금 없이도
    // 다른 트랜잭션이 방금 커밋한 결과를 정확히 본다(executePayment의
    // @Transactional(isolation) 주석 참고).
    WalletTransfer findByIdempotencyKey(
        @Param("idempotencyKey") String idempotencyKey
    );
}
