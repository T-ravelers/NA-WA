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

        //1. 지갑 소유자(wallet_owners) 확보 — uq_wallet_owners_member로 회원당 1행이 보장된다
        ensureWalletOwner(provision);

        //2. 확보한 소유자에 지갑(wallets) 확보 — 잔액 0 / ACTIVE는 스키마 기본값
        ensureWallet(provision);

        return provision.getWalletId();
    }

    // UNIQUE 제약에 deleted_at이 없어 "삭제 후 재생성"이 불가능하다. 남아 있는 행은 되살려서 쓴다.
    // 성공 판정은 affected rows가 아니라 확보한 ID로 한다. 복구가 이미 끝난 행에는 UPDATE가 0행을 남긴다.
    private void ensureWalletOwner(MemberWalletProvision provision) {
        Long existingWalletOwnerId = walletMapper.findWalletOwnerIdIncludingDeleted(
            provision.getMemberId()
        );
        if (existingWalletOwnerId != null) {
            walletMapper.restoreWalletOwner(existingWalletOwnerId);
            provision.setWalletOwnerId(existingWalletOwnerId);
        } else {
            walletMapper.insertMemberWalletOwner(provision);
        }

        if (provision.getWalletOwnerId() <= 0) {
            throw new IllegalStateException("Failed to resolve wallet owner");
        }
    }

    // 기존 지갑을 되살릴 때 잔액·wallet_status·원장 이력은 그대로 둔다. 통화도 기존 값을 유지한다.
    private void ensureWallet(MemberWalletProvision provision) {
        Long existingWalletId = walletMapper.findWalletIdIncludingDeleted(
            provision.getWalletOwnerId()
        );
        if (existingWalletId != null) {
            walletMapper.restoreWallet(existingWalletId);
            provision.setWalletId(existingWalletId);
        } else {
            walletMapper.insertMemberWallet(provision);
        }

        if (provision.getWalletId() <= 0) {
            throw new IllegalStateException("Failed to resolve wallet");
        }
    }
}
