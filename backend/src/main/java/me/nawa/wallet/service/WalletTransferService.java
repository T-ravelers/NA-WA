package me.nawa.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletTransferService {

    private final WalletMapper walletMapper;
    private final WalletTransferMapper walletTransferMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final TransactionNumberGenerator transactionNumberGenerator;

    @Transactional
    public long transfer(
        long initiatorMemberId,
        long payerMemberId,
        long payeeMemberId,
        BigDecimal amount,
        String memo
    ) {
        //1. 금액과 송금 주체를 먼저 검증한다. 자기 지갑으로의 정산 이체는 허용하지 않는다.
        if (amount == null || amount.signum() <= 0 || payerMemberId == payeeMemberId) {
            throw new BusinessException(WalletErrorCode.INVALID_SETTLEMENT_TRANSFER);
        }

        Wallet payerWallet = requireWallet(payerMemberId);
        Wallet payeeWallet = requireWallet(payeeMemberId);
        return executeTransfer(
            initiatorMemberId, payerWallet, payeeWallet, amount, "SETTLEMENT", memo
        );
    }

    // 회원 지갑 -> 시스템 지갑(예: DEPOSIT_POOL) 이체. 보증금 예치처럼 상대가 사람이
    // 아니라 시스템 계정일 때 쓴다. transferType은 호출자가 명시한다(DEPOSIT_HOLD 등).
    @Transactional
    public long transferToSystemWallet(
        long initiatorMemberId,
        long payerMemberId,
        String payeeSystemCode,
        BigDecimal amount,
        String transferType,
        String memo
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_SETTLEMENT_TRANSFER);
        }

        Wallet payerWallet = requireWallet(payerMemberId);
        Wallet payeeWallet = requireSystemWallet(payeeSystemCode);
        return executeTransfer(
            initiatorMemberId, payerWallet, payeeWallet, amount, transferType, memo
        );
    }

    // 지갑의 존재·활성 상태를 확인한 뒤, wallet_id 오름차순으로 잠그고 이체를 실행한다.
    // 반대 방향 이체가 동시에 들어와도 교착 상태가 나지 않게 하는 순서다.
    private long executeTransfer(
        long initiatorMemberId,
        Wallet payerWallet,
        Wallet payeeWallet,
        BigDecimal amount,
        String transferType,
        String memo
    ) {
        Wallet[] locked = lockInOrder(payerWallet, payeeWallet);
        Wallet lockedPayer = locked[0].getWalletId().equals(payerWallet.getWalletId())
            ? locked[0] : locked[1];
        Wallet lockedPayee = locked[0].getWalletId().equals(payeeWallet.getWalletId())
            ? locked[0] : locked[1];

        if (lockedPayer.getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        //하나의 거래를 만들고, 차감·입금 후 잔액을 각각 계산한다.
        BigDecimal payerBalance = lockedPayer.getAvailableBalance().subtract(amount);
        BigDecimal payeeBalance = lockedPayee.getAvailableBalance().add(amount);
        WalletTransfer transfer = new WalletTransfer(
            null, transactionNumberGenerator.generate(), transferType, "COMPLETED", amount,
            memo, null, null, LocalDateTime.now(), null, initiatorMemberId, null
        );
        walletTransferMapper.insert(transfer);

        //잔액과 양쪽 원장을 같은 트랜잭션 안에서 함께 반영한다.
        //이후 호출자가 예외를 던지면 @Transactional이 거래·원장·잔액을 모두 롤백한다.
        walletMapper.updateBalance(lockedPayer.getWalletId(), payerBalance);
        walletMapper.updateBalance(lockedPayee.getWalletId(), payeeBalance);
        walletLedgerMapper.insert(transfer.getTransferId(), lockedPayer.getWalletId(), "DEBIT", amount, payerBalance);
        walletLedgerMapper.insert(transfer.getTransferId(), lockedPayee.getWalletId(), "CREDIT", amount, payeeBalance);
        return transfer.getTransferId();
    }

    private Wallet requireWallet(long memberId) {
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        if (!"ACTIVE".equals(wallet.getWalletStatus())) {
            throw new BusinessException(WalletErrorCode.SETTLEMENT_WALLET_NOT_ACTIVE);
        }
        return wallet;
    }

    private Wallet requireSystemWallet(String systemCode) {
        Wallet wallet = walletMapper.findBySystemCode(systemCode);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        if (!"ACTIVE".equals(wallet.getWalletStatus())) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_ACTIVE);
        }
        return wallet;
    }

    private Wallet[] lockInOrder(Wallet payer, Wallet payee) {
        if (payer.getWalletId() < payee.getWalletId()) {
            return new Wallet[] {
                walletMapper.findByWalletIdForUpdate(payer.getWalletId()),
                walletMapper.findByWalletIdForUpdate(payee.getWalletId())
            };
        }
        return new Wallet[] {
            walletMapper.findByWalletIdForUpdate(payee.getWalletId()),
            walletMapper.findByWalletIdForUpdate(payer.getWalletId())
        };
    }
}
