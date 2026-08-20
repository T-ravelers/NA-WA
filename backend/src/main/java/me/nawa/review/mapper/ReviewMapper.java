package me.nawa.review.mapper;

import me.nawa.review.domain.MemberReview;
import me.nawa.review.domain.ReviewCategory;
import me.nawa.review.domain.ReviewKeywordCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewMapper {
    List<Long> findReviewedAppointmentMemberIds(
            @Param("appointmentId") Long appointmentId,
            @Param("reviewerAppointmentMemberId") Long reviewerId
    );

    int countReviewPair(
            @Param("appointmentId") Long appointmentId,
            @Param("reviewerAppointmentMemberId") Long reviewerId,
            @Param("reviewedAppointmentMemberId") Long reviewedId
    );

    int countActiveKeywords(
            @Param("keywordCodes") List<ReviewKeywordCode> keywordCodes
    );

    int insertReview(MemberReview review);

    int insertScores(
            @Param("reviewId") Long reviewId,
            @Param("scores") Map<ReviewCategory, Integer> scores
    );

    int insertKeywordSelections(
            @Param("reviewId") Long reviewId,
            @Param("keywordCodes") List<ReviewKeywordCode> keywordCodes
    );
}
