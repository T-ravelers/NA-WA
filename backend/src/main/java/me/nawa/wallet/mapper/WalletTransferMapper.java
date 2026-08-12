package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.WalletTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTransferMapper {

    WalletTransfer findByTransferId(@Param("transferId") Long transferId);

    // insert 후 MyBatis가 채워준 자동증가 PK를 transfer.transferId에 넣어준다 (WalletTransferMapper.xml의 useGeneratedKeys 참고)
    void insert(WalletTransfer transfer);

    // 멱등성 조회(빠른 경로). QR 잠금을 잡기 전, 다른 잠금 없이 먼저 확인만 해본다.
    // 일반 SELECT라 REPEATABLE READ 스냅샷에 묶이므로 이 결과만으로는 최종 판단을 내리지 않는다
    // — QR 잠금을 잡은 뒤 findByIdempotencyKeyForUpdate로 다시 확인한다.
    WalletTransfer findByIdempotencyKey(
        @Param("idempotencyKey") String idempotencyKey
    );

    // 멱등성 조회(잠금 경로). QR 행 잠금을 이미 잡은 뒤에만 호출해야 한다.
    //
    // 존재하지 않는 키에 FOR UPDATE를 걸면 InnoDB가 갭 락을 잡는데, 갭 락은 다른 트랜잭션의
    // 갭 락과 서로 배타적이지 않다. 그래서 QR 잠금을 잡기 *전에* 두 트랜잭션이 동시에 이 메서드로
    // 같은(존재하지 않는) 키를 조회하면 둘 다 갭 락을 쥔 채로 진행하다가, 한쪽은 QR 행 잠금을 들고
    // insert를 시도하고(같은 갭에 insert 의도 락 필요) 다른 한쪽은 그 QR 행 잠금을 기다리는
    // 교착 상태에 빠진다 — 실제로 QrPaymentConcurrencyIntegrationTest에서 재현됐다.
    // QR 잠금 뒤에는 그 잠금 하나가 이미 두 트랜잭션을 직렬화해 두었으므로 안전하다.
    WalletTransfer findByIdempotencyKeyForUpdate(
        @Param("idempotencyKey") String idempotencyKey
    );
}
