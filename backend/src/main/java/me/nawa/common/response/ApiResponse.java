package me.nawa.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import me.nawa.common.exception.ErrorCode;

/**
 * 모든 일반 JSON API가 사용하는 공통 응답 형식으로
 * 성공여부, 데이터, 오류정보를 가지고 있습니다.
 *
 * 성공하면 data만 포함하고,
 * 실패하면 error만 포함합니다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;

    private ApiResponse(
        boolean success,
        T data,
        ApiError error
    ) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    /**
     * 반환 데이터가 있는 성공 응답을 생성합니다(error 정보 미포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 반환 데이터가 없는 200 성공 응답을 생성합니다.
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * ErrorCode를 이용해 실패 응답을 생성합니다(데이터 미포함)
     *
     * HTTP 상태는 GlobalExceptionHandler에서 설정합니다.
     */
    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return new ApiResponse<>(
            false,
            null,
            ApiError.from(errorCode)
        );
    }
}
