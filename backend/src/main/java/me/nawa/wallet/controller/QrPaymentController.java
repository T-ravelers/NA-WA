package me.nawa.wallet.controller;


import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentPreviewRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;
import me.nawa.wallet.service.QrPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet/qr")
@RequiredArgsConstructor
public class QrPaymentController {

    private final QrPaymentService qrPaymentService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QrPaymentCreateResponse> createPaymentQr(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody QrPaymentCreateRequest request
        ){
        return ApiResponse.success(
            qrPaymentService.createPaymentQr(member.getMemberId(), request)
        );
    }

    @PostMapping("/resolve")
    public ApiResponse<QrPaymentResolveResponse> resolvePaymentQr(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody QrPaymentResolveRequest request
    ) {
        return ApiResponse.success(
            qrPaymentService.resolvePaymentQr(member.getMemberId(), request)
        );
    }

    // 요청에 단순 식별자 외에도 결제 시도 조건이 들어가기 때문에 POST
    @PostMapping("/payment/preview")
    public ApiResponse<QrPaymentPreviewResponse> previewPayment(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody QrPaymentPreviewRequest request
    ) {
        return ApiResponse.success(
            qrPaymentService.previewPayment(member.getMemberId(), request)
        );
    }

}
