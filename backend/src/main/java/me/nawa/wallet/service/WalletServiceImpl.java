package me.nawa.wallet.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletLedgerEntry;
import me.nawa.wallet.dto.response.TransactionSummaryResponse;
import me.nawa.wallet.dto.response.WalletHomeResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final int RECENT_TRANSACTION_LIMIT = 5;

    private final WalletMapper walletMapper;
    private final WalletLedgerMapper walletLedgerMapper;

    @Override
    @Transactional(readOnly = true)
    public WalletHomeResponse getWalletHome(Long memberId) {
        //1. 해당 wallet 정보 가져오기
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if(wallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 해당 wallet의 거래 원장 리스트
        List<WalletLedgerEntry> recentEntries = walletLedgerMapper.findRecentByWalletId(
            wallet.getWalletId(),
            RECENT_TRANSACTION_LIMIT
        );

        //3. entity > dto
        List<TransactionSummaryResponse> recentTransactions = recentEntries.stream()
            .map(TransactionSummaryResponse::from)
            .collect(Collectors.toList());

        //4. wallet dto로 변환
        return WalletHomeResponse.of(
            wallet.getAvailableBalance(),
            wallet.getWalletStatus(),
            recentTransactions
        );
    }
}
