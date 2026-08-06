package me.nawa.wallet.service;

import com.stripe.exception.StripeException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletTopup;
import me.nawa.wallet.domain.enums.TopupMethodType;
import me.nawa.wallet.dto.request.StripeIntentCreateRequest;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.StripeIntentResponse;
import me.nawa.wallet.dto.response.TopupListResponse;
import me.nawa.wallet.dto.response.TopupMethodResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.dto.response.TopupSummaryResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.external.stripe.StripeClient;
import me.nawa.wallet.external.stripe.StripePaymentIntent;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 충전 수단 조회 / 충전 미리보기 / 충전 내역 목록 / Stripe PaymentIntent 생성을 담당한다.
@Service
@RequiredArgsConstructor
@Log4j2
public class TopupServiceImpl implements TopupService {
    private static final String GUIDE_MESSAGE = "테스트 환경에서는 실제 결제가 발생하지 않습니다.";
    private static final String KRW = "KRW"; // 현재는 KRW 충전만 지원
    private static final BigDecimal FEE = BigDecimal.ZERO; // 수수료 미도입 상태, 항상 0원
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final WalletMapper walletMapper;
    private final WalletTopupMapper walletTopupMapper;
    private final StripeClient stripeClient;

    // GET /api/v1/topups/methods — 활성화된(enabled=true) 충전 수단 목록 + 안내 문구 반환
    @Override
    public TopupMethodsResponse getAvailableTopupMethods() {
        List<TopupMethodResponse> methods = Arrays.stream(TopupMethodType.values())
            .filter(TopupMethodType::isEnabled)
            .map(TopupMethodType::toResponse)
            .collect(Collectors.toList());

        return new TopupMethodsResponse(methods, GUIDE_MESSAGE);
    }

    // POST /api/v1/topups/preview — 실제 충전 없이 "충전하면 잔액이 얼마가 될지"만 미리 계산해서 보여준다.
    @Override
    @Transactional(readOnly = true)
    public TopupPreviewResponse previewTopup(Long memberId, TopupPreviewRequest request) {
        //1. 금액/충전수단/통화 검증 (하나라도 안 맞으면 예외로 바로 중단)
        validateAmount(request.amount());
        validateMethod(request.method());
        validateCurrency(request.currency());

        //2. 본인 지갑 조회
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //3. 충전 후 예상 잔액 계산 (수수료는 0원 고정이라 사실상 잔액 + 충전액)
        BigDecimal amount = request.amount();
        BigDecimal sandboxBalance = wallet.getAvailableBalance();
        BigDecimal expectedSandboxBalance = sandboxBalance.add(amount).subtract(FEE);

        //4. 지갑이 ACTIVE 상태가 아니면(정지 등) 경고 문구를 같이 내려준다 (막지는 않고 안내만)
        String warning = "ACTIVE".equals(wallet.getWalletStatus())
            ? null
            : "현재 지갑 상태(" + wallet.getWalletStatus() + ")에서는 충전이 제한될 수 있습니다.";

        return new TopupPreviewResponse(
            amount, FEE, request.currency(), sandboxBalance, expectedSandboxBalance, warning
        );
    }

    // GET /api/v1/topups — 충전 내역을 최신순으로 커서 페이지네이션 조회
    @Override
    @Transactional(readOnly = true)
    public TopupListResponse getTopups(Long memberId, Long cursor, Integer size) {
        //1. 본인 지갑 조회
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 다음 페이지 존재 여부를 판단하기 위해 (원하는 개수 + 1)건을 조회
        int pageSize = resolveSize(size);
        List<WalletTopup> topups = walletTopupMapper.findByWalletIdWithCursor(
            wallet.getWalletId(),
            cursor,
            pageSize + 1
        );

        //3. 실제 요청한 개수만큼만 자르고, 남은 게 있으면 마지막 항목의 id를 다음 커서로 사용
        boolean hasNext = topups.size() > pageSize;
        List<WalletTopup> pageTopups = hasNext ? topups.subList(0, pageSize) : topups;

        String nextCursor = hasNext
            ? String.valueOf(pageTopups.get(pageTopups.size() - 1).getTopupId())
            : null;

        //4. 응답 DTO로 변환
        List<TopupSummaryResponse> summaries = pageTopups.stream()
            .map(TopupSummaryResponse::from)
            .collect(Collectors.toList());

        return TopupListResponse.of(summaries, nextCursor);
    }

    // POST /api/v1/topups/stripe/intent — Stripe에 실제 PaymentIntent 생성을 요청하고, 그 껍데기를 wallet_topups에 QUOTED 상태로 저장한다.
    @Override
    @Transactional
    public StripeIntentResponse createStripeIntent(long memberId, String idempotencyKdy,
                                                   StripeIntentCreateRequest request) {
        //1. Idempotency key는 필수 (없으면 재시도 시 중복 생성을 막을 방법이 없음)
        if (idempotencyKdy == null || idempotencyKdy.isBlank()) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        //2. 금액/통화 검증
        validateAmount(request.amount());
        validateCurrency(request.currency());

        //3. 본인 지갑 조회 + 지갑이 ACTIVE 상태여야 충전 가능
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if (wallet == null) {
            // 자깁이 존재하지 않을 때 not found
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        if (!"ACTIVE".equals(wallet.getWalletStatus())) {
            // 지갑이 존재하지만 활성 상태가 아닐 때
            throw new BusinessException(WalletErrorCode.STRIPE_WALLET_NOT_ACTIVE);
        }

        //4. 같은 Idempotency key로 이미 처리한 요청이 있으면 Stripe를 새로 부르지 않고 그 결과를 재사용
        WalletTopup existing = walletTopupMapper.findByIdempotencyKey(idempotencyKdy);
        if (existing != null) {
            // 같은 키인데 금액이 다르면 재시도가 아니라 키 재사용 실수이므로 막는다
            if (existing.getSourceAmount().compareTo(request.amount()) != 0) {
                throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            // client_secret은 DB에 저장하지 않으므로, 재요청 시엔 Stripe에서 다시 받아온다
            StripePaymentIntent refreshed = retrieveOrThrow(existing.getProviderPaymentId());
            return toStripeIntentResponse(existing, refreshed.getClientSecret());
        }

        //5. Stripe에 새 PaymentIntent 생성 요청 (실패하면 우리 쪽 문제가 아니라 결제망 문제이므로 503)
        StripePaymentIntent intent;
        try {
            intent = stripeClient.createPaymentIntent(request.amount(), idempotencyKdy);
        } catch (StripeException e) {
            log.error("[Stripe] PaymentIntent 생성 실패, memberId={}", memberId, e);
            throw new BusinessException(WalletErrorCode.STRIPE_UNAVAILABLE);
        }

        //6. wallet_topups에 QUOTED 상태로 저장 (KRW 단일 통화라 환율은 1로 고정)
        WalletTopup topup = new WalletTopup(
            request.amount(), KRW, BigDecimal.ONE, LocalDateTime.now(),
            null, "QUOTED", request.amount(), null, LocalDateTime.now(),
            wallet.getWalletId(), "stripe", intent.getProviderPaymentId(), intent.getStatus(),
            idempotencyKdy, null
        );
        walletTopupMapper.insert(topup);

        //7. 응답 조립 (생성 직후라 status는 항상 READY)
        return toStripeIntentResponse(topup, intent.getClientSecret());
    }

    private StripePaymentIntent retrieveOrThrow(String providerPaymentId) {
        try {
            return stripeClient.retrievePaymentIntent(providerPaymentId);
        } catch (StripeException e) {
            log.error("[Stripe] PaymentIntent 재조회 실패, providerPaymentId={}", providerPaymentId, e);
            throw new BusinessException(WalletErrorCode.STRIPE_UNAVAILABLE);
        }
    }

    private StripeIntentResponse toStripeIntentResponse(WalletTopup topup, String clientSecret) {
        return new StripeIntentResponse(
            topup.getTopupId(),
            clientSecret,
            topup.getProviderPaymentId(),
            topup.getSourceAmount(),
            topup.getSourceCurrencyCode(),
            "READY",
            "SANDBOX"
        );
    }

    // size 미지정/0 이하면 기본값(20), 너무 크게 요청해도 최대값(50)으로 캡
    private int resolveSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    // 충전 금액은 반드시 0보다 커야 한다
    private void validateAmount(BigDecimal amount){
        if(amount == null || amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new BusinessException(WalletErrorCode.INVALID_TOPUP_AMOUNT);
        }
    }

    // 요청한 충전 수단이 TopupMethodType에 존재하고 활성화돼 있어야 한다
    private void validateMethod(String method){
        if(method == null){
            throw new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED);
        }
        try{
            if(!TopupMethodType.valueOf(method).isEnabled()){
                throw new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED);
            }
        }catch (IllegalArgumentException e){
            // TopupMethodType에 없는 값이 들어온 경우도 "지원 안 함"으로 처리
            throw new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED);
        }
    }

    // 현재는 KRW 충전만 허용
    private void validateCurrency(String currency){
        if(!KRW.equals(currency)){
            throw new BusinessException(WalletErrorCode.UNSUPPORTED_CURRENCY);
        }
    }
}
