package me.nawa.wallet.service;

import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;

public interface QrPaymentService {

    QrPaymentCreateResponse createPaymentQr(
        Long memberId,
        QrPaymentCreateRequest request
    );
}
