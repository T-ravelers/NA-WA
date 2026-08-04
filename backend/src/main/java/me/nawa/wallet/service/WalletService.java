package me.nawa.wallet.service;

import me.nawa.wallet.dto.response.WalletHomeResponse;

public interface WalletService {

    WalletHomeResponse getWalletHome(Long memberId);
}
