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

// 지갑 홈 화면(잔액 + 최근 거래 5건) 조회를 담당한다. GET /api/v1/wallet
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final int RECENT_TRANSACTION_LIMIT = 5;

    private final WalletMapper walletMapper;
    private final WalletLedgerMapper walletLedgerMapper;

    @Override
    @Transactional(readOnly = true)
    public WalletHomeResponse getWalletHome(Long memberId) {
        //1. 로그인한 회원의 지갑 조회 — 없으면 지갑 자체가 아직 안 만들어진 것
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if(wallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 이 지갑의 최근 거래 원장(wallet_ledger_entries) N건 조회 (wallet_transfers 조인 포함)
        List<WalletLedgerEntry> recentEntries = walletLedgerMapper.findRecentByWalletId(
            wallet.getWalletId(),
            RECENT_TRANSACTION_LIMIT
        );

        //3. 원장 엔티티 목록 -> 응답용 요약 DTO 목록으로 변환
        List<TransactionSummaryResponse> recentTransactions = recentEntries.stream()
            .map(TransactionSummaryResponse::from)
            .collect(Collectors.toList());

        //4. 잔액/상태/최근거래를 하나의 응답으로 조립
        return WalletHomeResponse.of(
            wallet.getAvailableBalance(),
            wallet.getWalletStatus(),
            recentTransactions
        );
    }
}
