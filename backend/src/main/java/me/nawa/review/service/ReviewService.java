package me.nawa.review.service;

import lombok.RequiredArgsConstructor;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.review.domain.MemberReview;
import me.nawa.review.domain.ReviewCategory;
import me.nawa.review.domain.ReviewKeywordCode;
import me.nawa.review.dto.request.MemberReviewCreateRequest;
import me.nawa.review.exception.ReviewErrorCode;
import me.nawa.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_KEYWORDS = 5;
    private static final Set<ReviewCategory> REQUIRED_CATEGORIES = Set.of(
            ReviewCategory.PUNCTUALITY,
            ReviewCategory.MANNERS,
            ReviewCategory.COMMUNICATION
    );

    private final AppointmentMapper appointmentMapper;
    private final ReviewMapper reviewMapper;

    @Transactional
    public void createReview(
            Long memberId,
            Long appointmentId,
            MemberReviewCreateRequest request) {
        validateRequest(memberId, appointmentId, request);
        Appointment appointment = appointmentMapper
                .findAppointmentByIdForUpdate(appointmentId);
        if (appointment == null) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_NOT_FOUND
            );
        }
        if (appointment.getAppointmentStatus()
                != AppointmentStatus.COMPLETED) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ALLOWED);
        }

        AppointmentMember reviewer = appointmentMapper
                .findMemberByAppointmentAndMemberForUpdate(
                        appointmentId,
                        memberId
                );
        AppointmentMember reviewed = appointmentMapper
                .findMemberByIdForUpdate(
                        appointmentId,
                        request.getReviewedAppointmentMemberId()
                );
        if (!isReviewableMember(reviewer) || !isReviewableMember(reviewed)
                || reviewer.getAppointmentMemberId().equals(
                reviewed.getAppointmentMemberId()
        )) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (reviewMapper.countReviewPair(
                appointmentId,
                reviewer.getAppointmentMemberId(),
                reviewed.getAppointmentMemberId()
        ) > 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_DUPLICATE);
        }

        List<ReviewKeywordCode> keywords = request.getKeywordCodes();
        if (!keywords.isEmpty()
                && reviewMapper.countActiveKeywords(keywords)
                != keywords.size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        MemberReview review = MemberReview.builder()
                .appointmentId(appointmentId)
                .reviewerAppointmentMemberId(
                        reviewer.getAppointmentMemberId()
                )
                .reviewedAppointmentMemberId(
                        reviewed.getAppointmentMemberId()
                )
                .build();
        if (reviewMapper.insertReview(review) != 1
                || review.getReviewId() == null
                || reviewMapper.insertScores(
                review.getReviewId(),
                request.getScores()
        ) != REQUIRED_CATEGORIES.size()) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
        if (!keywords.isEmpty()
                && reviewMapper.insertKeywordSelections(
                review.getReviewId(),
                keywords
        ) != keywords.size()) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    private static void validateRequest(
            Long memberId,
            Long appointmentId,
            MemberReviewCreateRequest request) {
        if (memberId == null || memberId <= 0
                || appointmentId == null || appointmentId <= 0
                || request == null
                || request.getReviewedAppointmentMemberId() == null
                || request.getReviewedAppointmentMemberId() <= 0
                || !validScores(request.getScores())
                || !validKeywords(request.getKeywordCodes())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private static boolean validScores(
            Map<ReviewCategory, Integer> scores) {
        return scores != null
                && scores.keySet().equals(REQUIRED_CATEGORIES)
                && scores.values().stream().allMatch(rating ->
                rating != null
                        && rating >= MIN_RATING
                        && rating <= MAX_RATING
        );
    }

    private static boolean validKeywords(List<ReviewKeywordCode> keywords) {
        return keywords != null
                && keywords.size() <= MAX_KEYWORDS
                && new HashSet<>(keywords).size() == keywords.size();
    }

    private static boolean isReviewableMember(AppointmentMember member) {
        return member != null
                && member.getMembershipStatus() == MembershipStatus.ACTIVE
                && member.getAttendanceStatus() == AttendanceStatus.ATTENDED;
    }
}
