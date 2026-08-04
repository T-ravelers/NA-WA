package me.nawa.wallet.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.enums.TopupMethodType;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.TopupMethodResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TopupServiceImpl implements TopupService {
    private static final String GUIDE_MESSAGE = "테스트 환경에서는 실제 결제가 발생하지 않습니다.";
    private static final String KRW = "KRW";
    private static final BigDecimal FEE = BigDecimal.ZERO;

    private final WalletMapper walletMapper;
    private final WalletTopupMapper walletTopupMapper;

    @Override
    public TopupMethodsResponse getAvailableTopupMethods() {
        List<TopupMethodResponse> methods = Arrays.stream(TopupMethodType.values())
            .filter(TopupMethodType::isEnabled)
            .map(TopupMethodType::toResponse)
            .collect(Collectors.toList());

        return new TopupMethodsResponse(methods, GUIDE_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    //amount, method, currency
    public TopupPreviewResponse previewTopup(Long memberId, TopupPreviewRequest request) {
        validateAmount(request.amount());
        validateMethod(request.method());
        validateCurrency(request.currency());

        Wallet wallet = walletMapper.findByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        BigDecimal amount = request.amount();
        BigDecimal sandboxBalance = wallet.getAvailableBalance();
        BigDecimal expectedSandboxBalance = sandboxBalance.add(amount).subtract(FEE);

        String warning = "ACTIVE".equals(wallet.getWalletStatus())
            ? null
            : "현재 지갑 상태(" + wallet.getWalletStatus() + ")에서는 충전이 제한될 수 있습니다.";

        return new TopupPreviewResponse(
            amount, FEE, request.currency(), sandboxBalance, expectedSandboxBalance, warning
        );
    }

    private void validateAmount(BigDecimal amount){
        if(amount == null || amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new BusinessException(WalletErrorCode.INVALID_TOPUP_AMOUNT);
        }
    }

    private void validateMethod(String method){
        if(method == null){
            throw new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED);
        }
        try{
            if(!TopupMethodType.valueOf(method).isEnabled()){
                throw new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED);
            }
        }catch (IllegalArgumentException e){
            throw new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED);
        }
    }

    private void validateCurrency(String currency){
        if(!KRW.equals(currency)){
            throw new BusinessException(WalletErrorCode.UNSUPPORTED_CURRENCY);
        }
    }
}
