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
import me.nawa.review.dto.response.MyReviewStatusResponse;
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

    /**
     * 로그인한 회원이 이 약속에서 이미 후기를 작성한 대상을 조회합니다.
     *
     * 작성 화면은 이 목록으로 "이미 씀" 상태를 복원합니다. 목록이 없으면
     * 화면이 전원을 미작성으로 보고 재제출을 허용해 REVIEW-002가 납니다.
     *
     * 진입 조건은 작성과 같습니다 — 약속이 COMPLETED이고, 방장이 출석을
     * 확인한(ACTIVE + ATTENDED) 참여자만입니다. 후기를 쓸 자격이 없는 회원은
     * 이 화면에 들어오지 못하므로 조회도 REVIEW-001로 막습니다.
     */
    @Transactional(readOnly = true)
    public MyReviewStatusResponse getMyReviewStatus(
            Long memberId,
            Long appointmentId) {
        if (memberId == null || memberId <= 0
                || appointmentId == null || appointmentId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        Appointment appointment = appointmentMapper
                .findAppointmentById(appointmentId);
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
                .findMemberByAppointmentAndMember(appointmentId, memberId);
        if (!isReviewableMember(reviewer)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ALLOWED);
        }
        return MyReviewStatusResponse.builder()
                .reviewedAppointmentMemberIds(
                        reviewMapper.findReviewedAppointmentMemberIds(
                                appointmentId,
                                reviewer.getAppointmentMemberId()
                        )
                )
                .build();
    }

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
