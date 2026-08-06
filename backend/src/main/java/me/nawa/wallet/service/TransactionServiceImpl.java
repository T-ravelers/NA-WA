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

// 거래 내역 목록 조회(GET /api/v1/me/transactions)와 상세 조회(GET /api/v1/me/transactions/{id})를 담당한다.
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

    // 타입/상태/기간 필터 + 커서 페이지네이션으로 거래 목록을 조회한다.
    @Override
    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(Long memberId, TransactionSearchCondition condition) {
        //1. 본인 지갑 조회
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if(wallet == null){
             throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 페이지 크기 보정, 날짜 필터는 LocalDate -> LocalDateTime 범위로 변환 (to는 다음날 0시 미만까지 포함)
        int size = resolveSize(condition.getSize());
        LocalDateTime from = condition.getFrom() != null ? condition.getFrom().atStartOfDay() : null;
        LocalDateTime to = condition.getTo() != null ? condition.getTo().plusDays(1).atStartOfDay() : null;

        //3. 다음 페이지 존재 여부를 판단하기 위해 (원하는 개수 + 1)건을 조회
        List<WalletLedgerEntry> entries = walletLedgerMapper.findByWalletIdWithCursor(
            wallet.getWalletId(),
            condition.getType() != null ? condition.getType().name() : null,
            condition.getStatus() != null ? condition.getStatus().name() : null,
            from,
            to,
            condition.getCursor(),
            size + 1
        );

        //4. 요청한 개수만큼만 자르고, 남은 게 있으면 마지막 항목의 ledgerEntryId를 다음 커서로 사용
        boolean hasNext = entries.size() > size;
        List<WalletLedgerEntry> pageEntries = hasNext ? entries.subList(0, size) : entries;

        String nextCursor = hasNext
            ? String.valueOf(pageEntries.get(pageEntries.size() - 1).getLedgerEntryId())
            : null;

        //5. 응답 DTO로 변환하고, 실제 적용된 필터값을 그대로 echo해서 반환
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

    // 거래 하나의 상세 정보(금액/상대방/영수증/환율)를 조회한다.
    // 404(거래 없음)와 403(내 거래가 아님)을 구분하기 위해 조회를 2단계로 나눈다.
    @Override
    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionDetail(Long memberId, Long transferId) {
        //1. 본인 지갑 조회
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if(wallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. transferId로 거래 자체가 존재하는지 확인 (누구 것인지는 아직 모름) -> 없으면 404
        WalletTransfer transfer = walletTransferMapper.findByTransferId(transferId);
        if(transfer == null){
            throw new BusinessException(WalletErrorCode.TRANSACTION_NOT_FOUND);
        }

        //3. 그 거래의 원장(wallet_ledger_entries)에 내 지갑 id로 연결된 행이 있는지 확인 -> 없으면 남의 거래이므로 403
        //   (wallet_transfers엔 wallet_id가 없어서 소유권 확인은 반드시 ledger를 거쳐야 함)
        WalletLedgerEntry ownership = walletLedgerMapper.findByTransferIdAndWalletId(transferId, wallet.getWalletId());
        if(ownership == null){
            throw new BusinessException(WalletErrorCode.TRANSACTION_FORBIDDEN);
        }

        //4. 거래 상대방 정보 조회 (회원/시스템/외부 결제사 중 하나로 채워짐)
        TransactionCounterpartyResponse counterparty = resolveCounterparty(transfer, transferId, wallet.getWalletId());
        //5. 환율 정보는 TOPUP(충전) 거래에만 존재하므로, 그 외 타입은 조회 자체를 생략하고 null
        TransactionFxResponse fx = "TOPUP".equals(transfer.getTransferType())
            ? resolveFx(transferId)
            : null;

        //6. 지금까지 모은 정보를 응답 하나로 조립 (occurredAt은 완료 전이면 생성 시각으로 대체)
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

    // size 미지정/0 이하면 기본값(20), 너무 크게 요청해도 최대값(50)으로 캡
    private int resolveSize(Integer requestedSize){
        if(requestedSize == null || requestedSize <= 0){
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    // 같은 거래(transferId)에서 "내 지갑이 아닌" 다른 지갑을 찾아 그 소유자 정보로 상대방을 판단한다.
    private TransactionCounterpartyResponse resolveCounterparty(WalletTransfer transfer, Long transferId, Long walletId){
        TransactionCounterparty transactionCounterparty = walletLedgerMapper.findCounterpartyByTransferId(transferId, walletId);

        if(transactionCounterparty == null){
            // TOPUP(충전)은 상대방 지갑이 원래 없는 거래라서, 이때는 외부 결제사(Stripe)로 채운다
            if("TOPUP".equals(transfer.getTransferType())){
                return new TransactionCounterpartyResponse("EXTERNAL", "Stripe");
            }
            // TOPUP이 아닌데 상대방이 없다면 데이터 이상 상황이므로 로그만 남기고 null 반환
            log.warn("[TransactionDetail] counterparty not found for transferId={}", transferId);
            return null;
        }

        // 상대방이 일반 회원이면 이름을, 시스템 지갑(정산/보증금 풀 등)이면 시스템 코드를 이름 자리에 채운다
        if("MEMBER".equals(transactionCounterparty.getOwnerType())){
            return new TransactionCounterpartyResponse("MEMBER", transactionCounterparty.getDisplayName());
        }

        return new TransactionCounterpartyResponse("SYSTEM", transactionCounterparty.getSystemCode());
    }

    // TOPUP 거래에 한해 wallet_topups에서 환율 스냅샷(원래 통화 금액/환율/기준시각)을 가져와 fx 응답을 만든다.
    private TransactionFxResponse resolveFx(Long transferId){
        WalletTopup topup = walletTopupMapper.findFxByTransferId(transferId);
        if(topup == null){
            return null;
        }

        return new TransactionFxResponse(
            topup.getSourceAmount(),
            topup.getSourceCurrencyCode(),
            "KRW", // 표시 통화는 지갑 통화(KRW) 고정
            topup.getExchangeRateKrwPerUnit(),
            topup.getQuotedAt()
        );
    }
}
