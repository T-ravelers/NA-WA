package me.nawa.review.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.review.domain.ReviewCategory;
import me.nawa.review.domain.ReviewKeywordCode;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class MemberReviewCreateRequest {
    private Long reviewedAppointmentMemberId;
    private Map<ReviewCategory, Integer> scores;
    private List<ReviewKeywordCode> keywordCodes;
}
