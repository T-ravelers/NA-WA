package me.nawa.wallet.service;

import lombok.RequiredArgsConstructor;
import me.nawa.wallet.domain.MemberWalletProvision;
import me.nawa.wallet.mapper.WalletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletProvisioningServiceImpl implements WalletProvisioningService {

    // 선불잔액 기준 통화 (V1 스키마 주석과 TopupServiceImpl의 지원 통화가 KRW 단일)
    private static final String DEFAULT_CURRENCY_CODE = "KRW";

    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public long provisionForMember(long memberId) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }

        MemberWalletProvision provision = new MemberWalletProvision(
            memberId,
            DEFAULT_CURRENCY_CODE
        );

        //1. 지갑 소유자(wallet_owners) 생성 — uq_wallet_owners_member로 회원당 1행이 보장된다
        if (walletMapper.insertMemberWalletOwner(provision) != 1
            || provision.getWalletOwnerId() <= 0) {
            throw new IllegalStateException("Failed to insert wallet owner");
        }

        //2. 생성된 소유자에 지갑(wallets) 생성 — 잔액 0 / ACTIVE는 스키마 기본값
        if (walletMapper.insertMemberWallet(provision) != 1
            || provision.getWalletId() <= 0) {
            throw new IllegalStateException("Failed to insert wallet");
        }

        return provision.getWalletId();
    }
}
