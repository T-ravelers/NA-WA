package me.nawa.review.service;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.review.domain.MemberReview;
import me.nawa.review.domain.ReviewCategory;
import me.nawa.review.domain.ReviewKeywordCode;
import me.nawa.review.dto.request.MemberReviewCreateRequest;
import me.nawa.review.mapper.ReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @InjectMocks
    private ReviewService reviewService;

    @Test
    void createReview_attendedMembers_savesScoresAndKeywords() {
        MemberReviewCreateRequest request = validRequest();
        prepareCompletedAppointment();
        when(reviewMapper.countReviewPair(10L, 20L, 30L)).thenReturn(0);
        when(reviewMapper.countActiveKeywords(request.getKeywordCodes()))
                .thenReturn(2);
        doAnswer(invocation -> {
            MemberReview review = invocation.getArgument(0);
            java.lang.reflect.Field field = MemberReview.class
                    .getDeclaredField("reviewId");
            field.setAccessible(true);
            field.set(review, 40L);
            return 1;
        }).when(reviewMapper).insertReview(any(MemberReview.class));
        when(reviewMapper.insertScores(40L, request.getScores()))
                .thenReturn(3);
        when(reviewMapper.insertKeywordSelections(
                40L,
                request.getKeywordCodes()
        )).thenReturn(2);

        reviewService.createReview(1L, 10L, request);

        verify(reviewMapper).insertScores(40L, request.getScores());
        verify(reviewMapper).insertKeywordSelections(
                40L,
                request.getKeywordCodes()
        );
    }

    @Test
    void createReview_selfReview_rejectsRequest() {
        MemberReviewCreateRequest request = validRequest();
        request.setReviewedAppointmentMemberId(20L);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(Appointment.builder()
                        .appointmentStatus(AppointmentStatus.COMPLETED)
                        .build());
        AppointmentMember reviewer = attendedMember(20L, 1L);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L,
                1L
        )).thenReturn(reviewer);
        when(appointmentMapper.findMemberByIdForUpdate(10L, 20L))
                .thenReturn(reviewer);

        assertThrows(
                BusinessException.class,
                () -> reviewService.createReview(1L, 10L, request)
        );

        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void createReview_duplicate_rejectsRequest() {
        MemberReviewCreateRequest request = validRequest();
        prepareCompletedAppointment();
        when(reviewMapper.countReviewPair(10L, 20L, 30L)).thenReturn(1);

        assertThrows(
                BusinessException.class,
                () -> reviewService.createReview(1L, 10L, request)
        );

        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void createReview_missingScore_rejectsRequest() {
        MemberReviewCreateRequest request = validRequest();
        request.setScores(Map.of(
                ReviewCategory.PUNCTUALITY, 5,
                ReviewCategory.MANNERS, 4
        ));

        assertThrows(
                BusinessException.class,
                () -> reviewService.createReview(1L, 10L, request)
        );

        verify(appointmentMapper, never())
                .findAppointmentByIdForUpdate(anyLong());
    }

    private void prepareCompletedAppointment() {
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(Appointment.builder()
                        .appointmentStatus(AppointmentStatus.COMPLETED)
                        .build());
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L,
                1L
        )).thenReturn(attendedMember(20L, 1L));
        when(appointmentMapper.findMemberByIdForUpdate(10L, 30L))
                .thenReturn(attendedMember(30L, 2L));
    }

    private static AppointmentMember attendedMember(Long id, Long memberId) {
        return AppointmentMember.builder()
                .appointmentMemberId(id)
                .memberId(memberId)
                .membershipStatus(MembershipStatus.ACTIVE)
                .attendanceStatus(AttendanceStatus.ATTENDED)
                .build();
    }

    private static MemberReviewCreateRequest validRequest() {
        MemberReviewCreateRequest request = new MemberReviewCreateRequest();
        request.setReviewedAppointmentMemberId(30L);
        request.setScores(Map.of(
                ReviewCategory.PUNCTUALITY, 5,
                ReviewCategory.MANNERS, 4,
                ReviewCategory.COMMUNICATION, 5
        ));
        request.setKeywordCodes(List.of(
                ReviewKeywordCode.FRIENDLY,
                ReviewKeywordCode.ON_TIME
        ));
        return request;
    }
}
