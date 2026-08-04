package me.nawa.wallet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletLedgerEntry;
import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.TransactionAppliedFilters;
import me.nawa.wallet.dto.response.TransactionListResponse;
import me.nawa.wallet.dto.response.TransactionSummaryResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final WalletMapper walletMapper;
    private final WalletLedgerMapper walletLedgerMapper;

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

    private int resolveSize(Integer requestedSize){
        if(requestedSize == null || requestedSize <= 0){
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }
}
