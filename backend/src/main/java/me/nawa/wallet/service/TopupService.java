package me.nawa.wallet.service;

import me.nawa.wallet.dto.request.StripeIntentCreateRequest;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.StripeIntentResponse;
import me.nawa.wallet.dto.response.TopupListResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;

public interface TopupService {

    TopupMethodsResponse getAvailableTopupMethods();

    TopupPreviewResponse previewTopup(Long memberId, TopupPreviewRequest request);

    TopupListResponse getTopups(Long memberId, Long cursor, Integer size);

    StripeIntentResponse createStripeIntent(long memberId, String idempotencyKdy, StripeIntentCreateRequest request);
}
