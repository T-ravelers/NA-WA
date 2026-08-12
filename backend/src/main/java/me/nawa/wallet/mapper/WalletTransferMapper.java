package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.WalletTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTransferMapper {

    WalletTransfer findByTransferId(@Param("transferId") Long transferId);

    // insert 후 MyBatis가 채워준 자동증가 PK를 transfer.transferId에 넣어준다 (WalletTransferMapper.xml의 useGeneratedKeys 참고)
    void insert(WalletTransfer transfer);

    // 멱등성 조회
    WalletTransfer findByIdempotencyKey(
        @Param("idempotencyKey") String idempotencyKey
    );
}
