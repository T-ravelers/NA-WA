package me.nawa.common.response;

import lombok.Getter;
import me.nawa.common.exception.ErrorCode;

/**
 * 실패 응답에 포함할 오류 정보로
 * ErrorCode에 정의된 코드와 메세지를 담고 있습니다.
*/
@Getter
public class ApiError {

    private final String code;
    private final String message;

    private ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * API 오류 응답을 생성합니다.
     */
    public static ApiError from(ErrorCode errorCode) {
        return new ApiError(
            errorCode.getCode(),
            errorCode.getMessage()
        );
    }
}
