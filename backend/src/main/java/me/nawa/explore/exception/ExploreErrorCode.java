package me.nawa.explore.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExploreErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "EXPLORE-001",
        "Event를 찾을 수 없습니다."
    ),

    PLACE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "EXPLORE-002",
        "Place를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
