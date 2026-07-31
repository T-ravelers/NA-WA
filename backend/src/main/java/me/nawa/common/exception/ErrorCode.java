package me.nawa.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    /**
     * 해당 오류에 대응하는 HTTP 상태를 나타냅니다.
     */
    HttpStatus getStatus();

    /**
     * 프론트엔드가 오류를 구분할 때 사용하는 고유 코드입니다
     */
    String getCode();

    /**
     * 사용자에게 노출할 기본 오류 메세지입니다(자세한 정보는 미포함)
     */
    String getMessage();

}
