package me.nawa.wallet.service;

import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;

public interface QrPaymentService {

    QrPaymentCreateResponse createPaymentQr(
        Long memberId,
        QrPaymentCreateRequest request
    );

    QrPaymentResolveResponse resolvePaymentQr(
        Long memberId,
        QrPaymentResolveRequest request
    );
}
