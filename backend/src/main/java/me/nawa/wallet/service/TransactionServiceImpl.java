package me.nawa.wallet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.TransactionCounterparty;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletLedgerEntry;
import me.nawa.wallet.domain.WalletTopup;
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.TransactionAppliedFilters;
import me.nawa.wallet.dto.response.TransactionCounterpartyResponse;
import me.nawa.wallet.dto.response.TransactionDetailResponse;
import me.nawa.wallet.dto.response.TransactionFxResponse;
import me.nawa.wallet.dto.response.TransactionListResponse;
import me.nawa.wallet.dto.response.TransactionReceiptResponse;
import me.nawa.wallet.dto.response.TransactionSummaryResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
public class TransactionServiceImpl implements TransactionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final WalletMapper walletMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final WalletTransferMapper walletTransferMapper;
    private final WalletTopupMapper walletTopupMapper;

    @Override
    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(Long memberId, TransactionSearchCondition condition) {
        //1. 본인 지갑 조회
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if(wallet == null){
             throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 페이지 크기/날짜 범위 정규화
        int size = resolveSize(condition.getSize());
        LocalDateTime from = condition.getFrom() != null ? condition.getFrom().atStartOfDay() : null;
        LocalDateTime to = condition.getTo() != null ? condition.getTo().plusDays(1).atStartOfDay() : null;

        //3. size + 1건 조회해 다음 페이지 존재 여부 판단
        List<WalletLedgerEntry> entries = walletLedgerMapper.findByWalletIdWithCursor(
            wallet.getWalletId(),
            condition.getType() != null ? condition.getType().name() : null,
            condition.getStatus() != null ? condition.getStatus().name() : null,
            from,
            to,
            condition.getCursor(),
            size + 1
        );

        boolean hasNext = entries.size() > size;
        List<WalletLedgerEntry> pageEntries = hasNext ? entries.subList(0, size) : entries;

        String nextCursor = hasNext
            ? String.valueOf(pageEntries.get(pageEntries.size() - 1).getLedgerEntryId())
            : null;

        List<TransactionSummaryResponse> transactions = pageEntries.stream()
            .map(TransactionSummaryResponse::from)
            .collect(Collectors.toList());

        return TransactionListResponse.of(
            transactions,
            nextCursor,
            new TransactionAppliedFilters(
                condition.getType(),
                condition.getStatus(),
                condition.getFrom(),
                condition.getTo()
            )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionDetail(Long memberId, Long transferId) {
        //1. 자신의 wallet
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if(wallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 해당 transfer 찾지
        WalletTransfer transfer = walletTransferMapper.findByTransferId(transferId);
        if(transfer == null){
            throw new BusinessException(WalletErrorCode.TRANSACTION_NOT_FOUND);
        }

        //3. 해당 transfer에 해당하는 원장 찾기
        WalletLedgerEntry ownership = walletLedgerMapper.findByTransferIdAndWalletId(transferId, wallet.getWalletId());
        if(ownership == null){
            throw new BusinessException(WalletErrorCode.TRANSACTION_FORBIDDEN);
        }

        TransactionCounterpartyResponse counterparty = resolveCounterparty(transfer, transferId, wallet.getWalletId());
        TransactionFxResponse fx = "TOPUP".equals(transfer.getTransferType())
            ? resolveFx(transferId)
            : null;

        return new TransactionDetailResponse(
            transfer.getAmount(),
            transfer.getCompletedAt() != null ? transfer.getCompletedAt() : transfer.getCreatedAt(),
            counterparty,
            transfer.getTransferStatus(),
            new TransactionReceiptResponse(transfer.getTransferNumber(), transfer.getMemo(), transfer.getSpendingCategory()),
            transfer.getTransferNumber(),
            fx
        );
    }

    private int resolveSize(Integer requestedSize){
        if(requestedSize == null || requestedSize <= 0){
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private TransactionCounterpartyResponse resolveCounterparty(WalletTransfer transfer, Long transferId, Long walletId){
        TransactionCounterparty transactionCounterparty = walletLedgerMapper.findCounterpartyByTransferId(transferId, walletId);

        if(transactionCounterparty == null){
            if("TOPUP".equals(transfer.getTransferType())){
                return new TransactionCounterpartyResponse("EXTERNAL", "Stripe");
            }
            log.warn("[TransactionDetail] counterparty not found for transferId={}", transferId);
            return null;
        }

        if("MEMBER".equals(transactionCounterparty.getOwnerType())){
            return new TransactionCounterpartyResponse("MEMBER", transactionCounterparty.getDisplayName());
        }

        return new TransactionCounterpartyResponse("SYSTEM", transactionCounterparty.getSystemCode());
    }

    private TransactionFxResponse resolveFx(Long transferId){
        WalletTopup topup = walletTopupMapper.findFxByTransferId(transferId);
        if(topup == null){
            return null;
        }

        return new TransactionFxResponse(
            topup.getSourceAmount(),
            topup.getSourceCurrencyCode(),
            "KRW",
            topup.getExchangeRateKrwPerUnit(),
            topup.getQuotedAt()
        );
    }
}
