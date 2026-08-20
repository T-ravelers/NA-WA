package me.nawa.settlement.controller;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.response.ApiResponse;
import me.nawa.common.storage.StoredReceipt;
import me.nawa.settlement.dto.response.SettlementReceiptOcrResponse;
import me.nawa.settlement.dto.response.SettlementReceiptUploadResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.service.SettlementReceiptOcrService;
import me.nawa.settlement.service.SettlementReceiptService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 정산 영수증 사진의 업로드, 글자 인식과 조회 API다.
 *
 * 업로드 경로가 정산 아래에 있지 않은 이유는, 사진을 먼저 올리고 정산을 나중에 만들기
 * 때문이다. 올리는 시점에는 붙일 정산이 아직 없다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementReceiptController {

    private final SettlementReceiptService settlementReceiptService;
    private final SettlementReceiptOcrService settlementReceiptOcrService;

    /**
     * 영수증 업로드
     *
     * 사진을 저장하고 정산 생성 요청에 실어 보낼 `receiptId`를 돌려줍니다.
     */
    @PostMapping(
        value = "/settlement-receipts",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<SettlementReceiptUploadResponse> uploadReceipt(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(settlementReceiptService.upload(
            member.getMemberId(), file.getContentType(), readBytes(file)
        ));
    }

    /**
     * 영수증 글자 인식
     *
     * 올려 둔 사진에서 품목 초안을 읽어 돌려줍니다. 결과는 저장하지 않습니다.
     *
     * 읽기만 하는데 POST인 이유는 두 가지다. 부를 때마다 바깥 서비스에 요금이 나가므로
     * 브라우저나 중간 서버가 임의로 다시 부르면 안 되고, 사진 크기에 따라 응답이 수 초씩
     * 걸려 캐시에 남아서도 안 된다.
     */
    @PostMapping("/settlement-receipts/{receiptId}/ocr")
    public ApiResponse<SettlementReceiptOcrResponse> recognizeReceipt(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long receiptId
    ) {
        return ApiResponse.success(
            settlementReceiptOcrService.recognize(member.getMemberId(), receiptId)
        );
    }

    /**
     * 영수증 조회
     *
     * 이미지 바이트를 그대로 돌려줍니다. 공통 응답 봉투를 쓰지 않습니다.
     */
    @GetMapping("/settlements/{settlementId}/receipt")
    public ResponseEntity<byte[]> getReceipt(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        StoredReceipt receipt = settlementReceiptService.getReceipt(
            member.getMemberId(), settlementId
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, receipt.contentType())
            // 사용자가 올린 파일이므로 브라우저가 형식을 임의로 재해석하지 못하게 막는다.
            .header("X-Content-Type-Options", "nosniff")
            // 다른 참여자에게도 보이면 안 되는 사진이라 중간 캐시에 남기지 않는다.
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(receipt.content());
    }

    /**
     * 파일이 비어 있는 것은 사용자 잘못이지만, 올라온 파일을 서버가 읽어내지 못한 것은
     * 서버 잘못이다. 둘을 같은 오류로 내보내면 장애 조사 때 사용자 탓으로 오해하게 되므로
     * 코드를 나누고, 원인 예외를 함께 실어 로그에 스택이 남게 한다.
     */
    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_FORMAT_INVALID);
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                SettlementErrorCode.SETTLEMENT_RECEIPT_READ_FAILED, exception
            );
        }
    }
}
