package me.nawa.wallet.service;

import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentExecuteRequest;
import me.nawa.wallet.dto.request.QrPaymentPreviewRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
import me.nawa.wallet.dto.response.QrPaymentExecuteResponse;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;
import me.nawa.wallet.dto.response.QrPaymentStatusResponse;

public interface QrPaymentService {

    QrPaymentCreateResponse createPaymentQr(
        Long memberId,
        QrPaymentCreateRequest request
    );

    QrPaymentResolveResponse resolvePaymentQr(
        Long memberId,
        QrPaymentResolveRequest request
    );

    QrPaymentPreviewResponse previewPayment(
        Long memberId,
        QrPaymentPreviewRequest request
    );

    QrPaymentExecuteResponse executePayment(
        Long memberId,
        String idempotencyKey,
        QrPaymentExecuteRequest request
    );

    QrPaymentStatusResponse getPaymentStatus(
        Long memberId,
        Long transferId
    );
}
