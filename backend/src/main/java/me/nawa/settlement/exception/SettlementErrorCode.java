package me.nawa.settlement.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 정산 오류 코드
 *
 * 정산 처리 중 발생하는 오류의 HTTP 상태와 응답 코드를 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {

    /**
     * 정산 정보 없음
     *
     * 요청한 정산이 존재하지 않거나 접근할 수 없을 때 사용합니다.
     */
    SETTLEMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT-001",
        "정산 정보를 찾을 수 없습니다."
    ),

    SETTLEMENT_PAYMENT_NOT_ALLOWED(
        HttpStatus.CONFLICT,
        "SETTLEMENT-002",
        "현재 상태에서는 정산 결제를 진행할 수 없습니다."
    ),

    SETTLEMENT_PAYMENT_NOT_FOUND(
        HttpStatus.FORBIDDEN,
        "SETTLEMENT-003",
        "본인의 정산 부담금을 찾을 수 없습니다."
    ),

    SETTLEMENT_SOURCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT-004",
        "정산 가능한 원거래를 찾을 수 없습니다."
    ),

    SETTLEMENT_CREATE_INVALID(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT-005",
        "정산 생성 정보가 올바르지 않습니다."
    ),

    SETTLEMENT_IDEMPOTENCY_CONFLICT(
        HttpStatus.CONFLICT,
        "SETTLEMENT-009",
        "같은 멱등성 키로 다른 정산 생성 요청을 처리할 수 없습니다."
    ),

    SETTLEMENT_SOURCE_ALREADY_USED(
        HttpStatus.CONFLICT,
        "SETTLEMENT-010",
        "이미 정산에 사용된 원거래입니다."
    ),

    SETTLEMENT_PAYMENT_IDEMPOTENCY_CONFLICT(
        HttpStatus.CONFLICT,
        "SETTLEMENT-014",
        "정산 결제가 이미 다른 멱등성 키로 처리되었습니다."
    ),

    SETTLEMENT_IDEMPOTENCY_KEY_INVALID(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT-015",
        "멱등성 키가 올바르지 않습니다."
    ),

    /**
     * 영수증 형식 불가
     *
     * 허용하지 않는 이미지 형식이거나, 확장자만 이미지이고 내용은 아닐 때 사용합니다.
     */
    SETTLEMENT_RECEIPT_FORMAT_INVALID(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT-016",
        "지원하지 않는 영수증 이미지 형식입니다."
    ),

    /**
     * 영수증 연결 불가
     *
     * 남이 올린 영수증이거나 이미 다른 정산에 쓰인 영수증을 연결하려 할 때 사용합니다.
     */
    SETTLEMENT_RECEIPT_NOT_LINKABLE(
        HttpStatus.CONFLICT,
        "SETTLEMENT-017",
        "이 영수증은 정산에 연결할 수 없습니다."
    ),

    /**
     * 영수증 없음
     *
     * 정산에 연결된 영수증이 없거나 조회 권한이 없을 때 사용합니다.
     */
    SETTLEMENT_RECEIPT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT-018",
        "영수증을 찾을 수 없습니다."
    ),

    /**
     * 영수증 저장소 오류
     *
     * S3 업로드나 조회가 실패했을 때 사용합니다.
     */
    SETTLEMENT_RECEIPT_STORAGE_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SETTLEMENT-019",
        "영수증 저장소를 사용할 수 없습니다."
    ),

    /**
     * 영수증 보관 기한 만료
     *
     * 사진은 보관 기한이 지나면 저장소에서 사라진다. "처음부터 없었다"(018)와 구분하려고
     * 코드를 따로 둔다. 둘이 같은 코드로 나가면 장애 조사 때 어느 쪽인지 알 수 없다.
     */
    SETTLEMENT_RECEIPT_EXPIRED(
        HttpStatus.GONE,
        "SETTLEMENT-020",
        "영수증 보관 기한이 지나 더 이상 볼 수 없습니다."
    ),

    /**
     * 영수증 파일 읽기 실패
     *
     * 업로드된 파일을 서버가 읽어내지 못한 경우다. 파일 형식 문제(016)가 아니라 서버 쪽
     * 문제이므로 분리한다. 형식 오류로 뭉뚱그리면 사용자 잘못으로 오해하게 된다.
     */
    SETTLEMENT_RECEIPT_READ_FAILED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "SETTLEMENT-021",
        "영수증 파일을 읽지 못했습니다."
    ),

    /**
     * 글자 인식이 지원하지 않는 이미지 형식
     *
     * 올릴 때는 통과했지만 글자 인식이 다루지 못하는 형식(webp)일 때 사용합니다. 사진을
     * 다시 찍으면 해결되므로 저장소 오류(019)와 구분합니다.
     */
    SETTLEMENT_RECEIPT_OCR_FORMAT_UNSUPPORTED(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT-022",
        "이 형식의 사진은 글자를 읽을 수 없습니다."
    ),

    /**
     * 영수증에서 품목을 읽지 못함
     *
     * 사진은 인식했지만 품목 줄을 하나도 찾지 못한 경우입니다. 사용자가 다시 찍거나 직접
     * 입력하면 되는 상황이라, 서버 쪽 문제인 024·025와 구분합니다.
     */
    SETTLEMENT_RECEIPT_OCR_UNREADABLE(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "SETTLEMENT-023",
        "영수증에서 품목을 읽지 못했습니다."
    ),

    /**
     * 글자 인식 시간 초과
     *
     * 정해진 시간 안에 인식 결과가 오지 않았습니다. 그대로 다시 시도할 만한 상황이라
     * 아예 부르지 못한 경우(025)와 나눕니다.
     */
    SETTLEMENT_RECEIPT_OCR_TIMEOUT(
        HttpStatus.GATEWAY_TIMEOUT,
        "SETTLEMENT-024",
        "영수증 글자 인식이 시간 안에 끝나지 않았습니다."
    ),

    /**
     * 글자 인식 서비스 사용 불가
     *
     * 인식 서버에 닿지 못했거나 오류를 응답했습니다. 접속 정보가 설정되지 않은 경우도
     * 여기에 들어갑니다. 사용자가 할 수 있는 일이 없으므로 직접 입력으로 안내합니다.
     */
    SETTLEMENT_RECEIPT_OCR_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SETTLEMENT-025",
        "영수증 글자 인식을 사용할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
