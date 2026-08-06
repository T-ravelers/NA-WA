package me.nawa.wallet.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
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
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.domain.enums.TopupMethodType;
import me.nawa.wallet.dto.request.StripeIntentCreateRequest;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.StripeIntentResponse;
import me.nawa.wallet.dto.response.StripeTopupStatusResponse;
import me.nawa.wallet.dto.response.StripeWebhookResponse;
import me.nawa.wallet.dto.response.TopupListResponse;
import me.nawa.wallet.dto.response.TopupMethodResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.dto.response.TopupSummaryResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.external.stripe.StripeClient;
import me.nawa.wallet.external.stripe.StripePaymentIntent;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 충전 수단 조회 / 충전 미리보기 / 충전 내역 목록 / Stripe PaymentIntent 생성·상태조회를 담당한다.
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
    private final WalletTransferMapper walletTransferMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final StripeClient stripeClient;
    private final TransactionNumberGenerator transactionNumberGenerator;

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

            // [오류 수정] idempotency key는 요청 헤더로 클라이언트가 직접 전달하기 때문에
            // 사용자가 같은 key와 amount로 요청을 보내면 다름 사용자의 intent와 client secret을 재사용하게 됨
            // wallet id도 같이 확인하여 idempotency key의 소유권을 확인해야함
            if (!existing.getWalletId().equals(wallet.getWalletId())) {
                throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }

            // topup의 status가 FAILED일 때는 재요청을 위해서 상태를 다시 QUOTED로 바꿔줘야 함
            if(existing.getTopupStatus().equals("FAILED")){
                walletTopupMapper.resetToQuoted(existing.getTopupId());
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

    // GET /api/v1/topups/stripe/{topupId} — 아직 진행 중인 건이면 Stripe에서 최신 상태를 가져와 반영(성공이면 여기서 실제로 잔액도 적립)한 뒤 조회한다.
    @Override
    @Transactional
    public StripeTopupStatusResponse getStripeTopupStatus(long memberId, Long topupId) {
        //1. 본인 지갑 조회
        Wallet wallet = walletMapper.findByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        //2. 충전 건 조회 (webhook과 동시에 들어와도 같은 행을 놓고 경합하도록 FOR UPDATE로 잠근다 —
        //   그렇지 않으면 둘 다 "아직 비최종 상태"로 읽고 동시에 크레딧을 시도해 중복 적립될 수 있다)
        WalletTopup topup = walletTopupMapper.findByTopupIdForUpdate(topupId);
        if (topup == null) {
            throw new BusinessException(WalletErrorCode.TOPUP_NOT_FOUND);
        }

        //3. 본인 소유가 아니면 403
        if (!topup.getWalletId().equals(wallet.getWalletId())) {
            throw new BusinessException(WalletErrorCode.TOPUP_FORBIDDEN);
        }

        //4. 아직 최종 상태가 아니면 Stripe에서 최신 상태를 가져와 반영한다.
        //   (락을 잡은 뒤 여기서 최종 상태를 다시 확인하므로, 앞서 webhook이 먼저 처리를 끝냈다면 여기서 걸러진다)
        //   succeeded로 확인되면 이 안에서 실제로 지갑 잔액 적립까지 같이 처리된다 (applyProviderUpdate -> creditWallet).
        if (!isTerminal(topup.getTopupStatus())) {
            StripePaymentIntent latest = retrieveOrThrow(topup.getProviderPaymentId());
            // 폴링은 Stripe의 "현재 상태"를 그대로 읽어오는 거라 실패 이벤트라는 개념이 없다 (isFailureEvent=false)
            topup = applyProviderUpdate(topup, latest.getStatus(), false);
        }

        //5. 표시 상태 계산 + 응답 조립 (크레딧이 방금 반영됐을 수 있어 잔액은 다시 조회)
        String displayStatus = toDisplayStatus(topup.getTopupStatus(), topup.getProviderStatus());
        Wallet latestWallet = walletMapper.findByMemberId(memberId);

        return new StripeTopupStatusResponse(
            topup.getTopupId(),
            topup.getTransferId(),
            displayStatus,
            topup.getProviderStatus(),
            "FAILED".equals(displayStatus),
            latestWallet.getAvailableBalance()
        );
    }

    // POST /api/v1/stripe/webhook — Stripe가 결제 상태 변화를 알려줄 때 호출한다.
    @Override
    @Transactional
    public StripeWebhookResponse applyStripeWebhookEvent(String payload, String signatureHeader) {
        //1. 서명 검증 — Stripe가 보낸 요청이 맞는지 확인 (이게 곧 이 엔드포인트의 인증 역할을 한다)
        Event event;
        try {
            event = stripeClient.constructWebhookEvent(payload, signatureHeader);
        } catch (SignatureVerificationException e) {
            log.warn("[Stripe Webhook] 서명 검증 실패", e);
            throw new BusinessException(WalletErrorCode.STRIPE_INVALID_SIGNATURE);
        }

        //2. payment_intent 관련 이벤트가 아니면 볼 것도 없이 수신만 확인
        PaymentIntent paymentIntent = extractPaymentIntent(event);
        if (paymentIntent == null) {
            return new StripeWebhookResponse(true, false);
        }

        //3. 우리 DB에서 이 PaymentIntent에 연결된 충전 건 조회 — 모르는 결제면 에러 없이 수신만 확인 (Stripe가 무한 재시도하지 않도록)
        //   polling과 동시에 들어와도 같은 행을 놓고 경합하도록 FOR UPDATE로 잠근다
        WalletTopup topup = walletTopupMapper.findByProviderPaymentIdForUpdate(paymentIntent.getId());
        if (topup == null) {
            log.warn("[Stripe Webhook] 알 수 없는 PaymentIntent: {}", paymentIntent.getId());
            return new StripeWebhookResponse(true, false);
        }

        //4. 이미 최종 상태로 처리된 건이면 재처리하지 않고 "이미 처리됨"만 알려준다 (이벤트 재전송/중복 방지)
        //   (락을 잡은 뒤 여기서 다시 확인하므로, 앞서 polling이 먼저 처리를 끝냈다면 여기서 걸러진다)
        if (isTerminal(topup.getTopupStatus())) {
            return new StripeWebhookResponse(true, true);
        }

        //5. payment_intent.payment_failed 이벤트는 Stripe 원문 status와 무관하게 실패로 취급한다
        //   (Stripe는 결제 실패 후에도 PaymentIntent 자체 상태를 requires_payment_method로 되돌리기 때문에,
        //    상태 문자열만으로는 "방금 실패했다"는 걸 알 수 없다 — 이벤트 타입으로 판단해야 한다)
        boolean isFailureEvent = "payment_intent.payment_failed".equals(event.getType());
        applyProviderUpdate(topup, paymentIntent.getStatus(), isFailureEvent);

        return new StripeWebhookResponse(true, false);
    }

    // 이벤트 payload에서 PaymentIntent 객체를 꺼낸다. payment_intent.* 이벤트가 아니거나 역직렬화에 실패하면 null
    // (역직렬화 실패는 보통 Stripe API 버전 불일치 때문인데, 웹훅 자체를 에러로 만들지 않고 그냥 처리를 건너뛴다 — 폴링이 기준 경로라 안전하다).
    private PaymentIntent extractPaymentIntent(Event event) {
        if (event.getType() == null || !event.getType().startsWith("payment_intent.")) {
            return null;
        }
        return event.getDataObjectDeserializer()
            .getObject()
            .map(stripeObject -> (PaymentIntent) stripeObject)
            .orElseGet(() -> {
                try {
                    return (PaymentIntent) event.getDataObjectDeserializer().deserializeUnsafe();
                } catch (Exception e) {
                    log.warn("[Stripe Webhook] PaymentIntent 역직렬화 실패, eventId={}", event.getId(), e);
                    return null;
                }
            });
    }

    private boolean isTerminal(String topupStatus) {
        return "COMPLETED".equals(topupStatus) || "FAILED".equals(topupStatus) || "CANCELLED".equals(topupStatus);
    }

    // 구현 전 확정 사항 3번 매핑표: DB 내부 상태(topupStatus) + Stripe 원문 상태(providerStatus)를 조합해서 화면 표시용 상태를 계산한다.
    private String toDisplayStatus(String topupStatus, String providerStatus) {
        if ("COMPLETED".equals(topupStatus)) {
            return "SUCCESS";
        }
        if ("FAILED".equals(topupStatus)) {
            return "FAILED";
        }
        if ("CANCELLED".equals(topupStatus)) {
            return "CANCELLED";
        }
        // 아직 QUOTED인데, Stripe가 처리 중/추가 인증 요구 상태면 PENDING, 그 외(카드 입력 대기 등)는 READY
        if ("requires_action".equals(providerStatus) || "processing".equals(providerStatus)) {
            return "PENDING";
        }
        return "READY";
    }

    // Stripe 최신 상태를 반영한다. succeeded면 실제 크레딧까지 수행하고, 실패 이벤트면 FAILED로, 그 외엔 상태만 갱신한다.
    // isFailureEvent는 웹훅의 payment_intent.payment_failed처럼 "원문 status만으론 알 수 없는 실패"를 표시하는 용도다 (폴링에서는 항상 false).
    private WalletTopup applyProviderUpdate(WalletTopup topup, String rawStatus, boolean isFailureEvent) {
        if (isFailureEvent) {
            walletTopupMapper.markFailed(topup.getTopupId(), rawStatus);
        } else if ("succeeded".equals(rawStatus)) {
            creditWallet(topup);
        } else if ("canceled".equals(rawStatus)) {
            walletTopupMapper.markCancelled(topup.getTopupId(), rawStatus);
        } else {
            walletTopupMapper.updateProviderStatus(topup.getTopupId(), rawStatus);
        }
        return walletTopupMapper.findByTopupId(topup.getTopupId());
    }

    // 지갑 잠금 -> 잔액 적립 -> 거래/원장 기록 -> 충전 건 COMPLETED 처리까지 한 트랜잭션으로 수행한다.
    private void creditWallet(WalletTopup topup) {
        Wallet wallet = walletMapper.findByWalletIdForUpdate(topup.getWalletId());
        BigDecimal newBalance = wallet.getAvailableBalance().add(topup.getKrwAmount());
        walletMapper.updateBalance(wallet.getWalletId(), newBalance);

        // 충전은 회원이 직접 시작한 거래지만, 이 크레딧 자체는 Stripe 상태 확인(폴링/웹훅)이 트리거하는
        // 시스템 자동 처리라 initiatorMemberId는 null로 둔다 (DB 코멘트: "시스템 자동 이체는 NULL").
        WalletTransfer transfer = new WalletTransfer(
            null, transactionNumberGenerator.generate(), "TOPUP", "COMPLETED",
            topup.getKrwAmount(), null, null, LocalDateTime.now(), LocalDateTime.now(),
            null, topup.getIdempotencyKey()
        );
        walletTransferMapper.insert(transfer);

        walletLedgerMapper.insert(
            transfer.getTransferId(), wallet.getWalletId(), "CREDIT", topup.getKrwAmount(), newBalance
        );

        walletTopupMapper.markCompleted(topup.getTopupId(), transfer.getTransferId(), "succeeded", LocalDateTime.now());
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
