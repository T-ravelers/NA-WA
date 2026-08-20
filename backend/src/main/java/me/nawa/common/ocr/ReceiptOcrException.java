package me.nawa.common.ocr;

/**
 * 글자 인식에 실패했을 때 던진다.
 *
 * 실패 사유를 나눠 두는 이유는, 셋의 성격이 완전히 달라서다. 사진을 못 읽은 것은 사용자가
 * 다시 찍으면 되고, 시간이 초과된 것은 그대로 다시 시도할 만하며, 서버가 응답하지 않는 것은
 * 사용자가 할 수 있는 일이 없다. 한 덩어리로 묶으면 화면이 셋 다 같은 말을 하게 된다.
 *
 * 어떤 오류 코드로 사용자에게 나갈지는 이 계층이 정하지 않는다. 여기는 어떤 기능이 쓰는지
 * 모르는 공용 계층이라, 사유만 알려주고 판단은 부르는 쪽에 맡긴다.
 */
public class ReceiptOcrException extends RuntimeException {

    public enum Reason {
        /** 사진에서 글자를 읽어내지 못했다. */
        UNREADABLE,
        /** 정해진 시간 안에 응답이 오지 않았다. */
        TIMEOUT,
        /** 인식 서버에 닿지 못했거나 오류 응답을 받았다. 설정이 비어 있는 경우도 포함한다. */
        UNAVAILABLE
    }

    private final Reason reason;

    public ReceiptOcrException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ReceiptOcrException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
