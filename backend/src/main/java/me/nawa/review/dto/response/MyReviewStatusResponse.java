package me.nawa.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 로그인한 회원이 이 약속에서 이미 후기를 작성한 대상 목록입니다.
 *
 * 후기 작성 화면이 "누구에게 이미 썼는지"만 알면 되므로 점수·키워드는
 * 담지 않습니다. 후기 내용을 남에게 보여주는 조회는 별도 API의 몫입니다.
 */
@Getter
@Builder
@AllArgsConstructor
public class MyReviewStatusResponse {
    private final List<Long> reviewedAppointmentMemberIds;
}
