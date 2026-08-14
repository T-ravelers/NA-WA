package me.nawa.appointment.service;

import lombok.RequiredArgsConstructor;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentAttendanceRequest;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.appointment.dto.response.AppointmentDetailResponse;
import me.nawa.appointment.dto.response.AppointmentListResponse;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.dto.response.AppointmentParticipationResponse;
import me.nawa.appointment.dto.response.AppointmentSummaryResponse;
import me.nawa.appointment.dto.response.MyOngoingAppointmentResponse;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.mapper.DepositMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 10;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final BigDecimal MIN_DEPOSIT = BigDecimal.valueOf(5_000);
    private static final BigDecimal MAX_DEPOSIT = BigDecimal.valueOf(50_000);
    private static final Set<String> ITEM_TYPES = Set.of("EVENT", "PLACE");
    private static final Set<String> LANGUAGES = Set.of(
            "en",
            "ja",
            "zh-TW",
            "vi"
    );
    private static final Set<AppointmentStatus> LIST_STATUSES = Set.of(
            AppointmentStatus.RECRUITING,
            AppointmentStatus.CLOSED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED
    );

    private final AppointmentMapper appointmentMapper;
    private final DepositMapper depositMapper;

    @Transactional
    public Appointment createAppointment(
            Long memberId,
            AppointmentCreateRequest request) {
        validateCreateRequest(memberId, request);
        throw new BusinessException(
                AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED
        );
    }

    public AppointmentDetailResponse toCreatedResponse(
            Appointment appointment) {
        return toDetailResponse(appointment, List.of());
    }

    @Transactional
    public AppointmentMemberResponse joinAppointment(
            Long memberId,
            Long appointmentId) {
        validateIdentifiers(memberId, appointmentId);
        throw new BusinessException(
                AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED
        );
    }

    @Transactional
    public void leaveAppointment(Long memberId, Long appointmentId) {
        validateIdentifiers(memberId, appointmentId);
        Appointment appointment = requireAppointmentForUpdate(appointmentId);
        AppointmentMember member = appointmentMapper
                .findMemberByAppointmentAndMemberForUpdate(
                        appointmentId,
                        memberId
                );
        if (member == null) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_MEMBER_NOT_FOUND
            );
        }
        if (member.getMembershipStatus() == MembershipStatus.LEFT) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_MEMBER_NOT_FOUND
            );
        }
        if (member.getMembershipStatus() != MembershipStatus.PENDING) {
            throw new BusinessException(
                    AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
            );
        }
        if (appointment.getAppointmentStatus()
                == AppointmentStatus.IN_PROGRESS
                || appointment.getAppointmentStatus()
                == AppointmentStatus.COMPLETED
                || appointment.getAppointmentStatus()
                == AppointmentStatus.CANCELLED) {
            throw new BusinessException(
                    AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
            );
        }

        if (memberId.equals(appointment.getHostMemberId())) {
            AppointmentMember successor = appointmentMapper
                    .findHostSuccessorForUpdate(appointmentId, memberId);
            if (successor == null || appointmentMapper.updateHostMember(
                    appointmentId,
                    memberId,
                    successor.getMemberId()
            ) != 1) {
                throw new BusinessException(
                        AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
                );
            }
        }
        Deposit deposit = depositMapper.findByAppointmentMemberId(
                member.getAppointmentMemberId()
        );
        if (deposit != null && deposit.isPending()) {
            cancelPendingDeposit(deposit);
        }
        if (appointmentMapper.markMemberLeft(
                member.getAppointmentMemberId()
        ) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Transactional(readOnly = true)
    public AppointmentParticipationResponse getMyParticipation(
            Long memberId,
            Long appointmentId) {
        validateIdentifiers(memberId, appointmentId);
        if (appointmentMapper.findAppointmentById(appointmentId) == null) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_NOT_FOUND
            );
        }
        AppointmentMember member = appointmentMapper
                .findMemberByAppointmentAndMember(
                        appointmentId,
                        memberId
                );
        if (member == null) {
            return AppointmentParticipationResponse.notJoined();
        }
        return AppointmentParticipationResponse.builder()
                .joined(true)
                .appointmentMemberId(member.getAppointmentMemberId())
                .membershipStatus(member.getMembershipStatus())
                .attendanceStatus(member.getAttendanceStatus())
                .host(Boolean.TRUE.equals(member.getHost()))
                .build();
    }

    @Transactional
    public void confirmAttendance(
            Long hostMemberId,
            Long appointmentId,
            AppointmentAttendanceRequest request) {
        validateIdentifiers(hostMemberId, appointmentId);
        throw new BusinessException(
                AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED
        );
    }

    @Transactional(readOnly = true)
    public AppointmentListResponse searchAppointments(
            AppointmentSearchRequest request) {
        normalizeAndValidateSearch(request);

        long offsetLong = (long) request.getPage() * request.getSize();
        if (offsetLong > Integer.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        List<AppointmentSummaryResponse> content = appointmentMapper
                .searchAppointments(request, (int) offsetLong)
                .stream()
                .map(AppointmentService::toSummaryResponse)
                .toList();
        long totalElements = appointmentMapper.countAppointments(request);
        int totalPages = calculateTotalPages(totalElements, request.getSize());

        return new AppointmentListResponse(
                content,
                request.getPage(),
                request.getSize(),
                totalElements,
                totalPages,
                request.getPage() + 1 < totalPages
        );
    }

    @Transactional(readOnly = true)
    public AppointmentDetailResponse getAppointment(
            Long memberId,
            Long appointmentId) {
        if (memberId == null || memberId <= 0
                || appointmentId == null || appointmentId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Appointment appointment = appointmentMapper.findAppointmentById(
                appointmentId
        );
        if (appointment == null || isPrivateFromMember(appointment, memberId)) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_NOT_FOUND
            );
        }

        List<AppointmentMemberResponse> members = appointmentMapper
                .findActiveMembersByAppointmentId(appointmentId)
                .stream()
                .map(AppointmentService::toMemberResponse)
                .toList();
        return toDetailResponse(appointment, members);
    }

    @Transactional(readOnly = true)
    public List<AppointmentMemberResponse> getAppointmentMembers(
            Long memberId,
            Long appointmentId) {
        return getAppointment(memberId, appointmentId).getMembers();
    }

    @Transactional(readOnly = true)
    public List<MyOngoingAppointmentResponse> getMyOngoingAppointments(Long memberId){
        if(memberId == null || memberId <= 0){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return appointmentMapper.findMyOngoingAppointments(memberId)
            .stream()
            .map(MyOngoingAppointmentResponse::from)
            .toList();
    }

    private static void normalizeAndValidateSearch(
            AppointmentSearchRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (request.getPage() < 0) {
            request.setPage(DEFAULT_PAGE);
        }
        if (request.getSize() <= 0) {
            request.setSize(DEFAULT_SIZE);
        }
        if (request.getSize() > MAX_SIZE
                || (request.getItemId() != null
                && request.getItemId() <= 0)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        request.setItemType(normalizeUppercase(request.getItemType()));
        if (request.getItemType() != null
                && !ITEM_TYPES.contains(request.getItemType())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        request.setLanguage(normalizeOptional(request.getLanguage()));
        if (request.getLanguage() != null
                && !LANGUAGES.contains(request.getLanguage())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        request.setKeyword(normalizeOptional(request.getKeyword()));
        if ((request.getKeyword() != null
                && request.getKeyword().length() > 100)
                || (request.getStatus() != null
                && !LIST_STATUSES.contains(request.getStatus()))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private Appointment requireAppointmentForUpdate(Long appointmentId) {
        Appointment appointment = appointmentMapper
                .findAppointmentByIdForUpdate(appointmentId);
        if (appointment == null) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_NOT_FOUND
            );
        }
        return appointment;
    }

    private void cancelPendingDeposit(Deposit deposit) {
        if (depositMapper.markCancelled(
                deposit.getDepositId(),
                LocalDateTime.now()
        ) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    private static void validateIdentifiers(
            Long memberId,
            Long appointmentId) {
        if (memberId == null || memberId <= 0
                || appointmentId == null || appointmentId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private static boolean isPrivateFromMember(
            Appointment appointment,
            Long memberId) {
        return (appointment.getAppointmentStatus()
                == AppointmentStatus.PAYMENT_PENDING
                || appointment.getAppointmentStatus()
                == AppointmentStatus.CANCELLED)
                && !memberId.equals(appointment.getHostMemberId());
    }

    private static AppointmentSummaryResponse toSummaryResponse(
            Appointment appointment) {
        return AppointmentSummaryResponse.builder()
                .appointmentId(appointment.getAppointmentId())
                .itemId(appointment.getItemId())
                .itemType(appointment.getItemType())
                .appointmentName(appointment.getAppointmentName())
                .languageCode(appointment.getLanguageCode())
                .maxMembers(appointment.getMaxMembers())
                .currentMemberCount(appointment.getCurrentMemberCount())
                .depositAmount(appointment.getDepositAmount())
                .appointmentStatus(appointment.getAppointmentStatus())
                .meetingPlace(appointment.getMeetingPlace())
                .activityStartAt(appointment.getActivityStartAt())
                .activityEndAt(appointment.getActivityEndAt())
                .joinDeadline(appointment.getJoinDeadline())
                .hostDisplayName(appointment.getHostDisplayName())
                .build();
    }

    private static AppointmentDetailResponse toDetailResponse(
            Appointment appointment,
            List<AppointmentMemberResponse> members) {
        return AppointmentDetailResponse.builder()
                .appointmentId(appointment.getAppointmentId())
                .itemId(appointment.getItemId())
                .itemType(appointment.getItemType())
                .appointmentName(appointment.getAppointmentName())
                .languageCode(appointment.getLanguageCode())
                .maxMembers(appointment.getMaxMembers())
                .currentMemberCount(appointment.getCurrentMemberCount())
                .depositAmount(appointment.getDepositAmount())
                .appointmentStatus(appointment.getAppointmentStatus())
                .meetingPlace(appointment.getMeetingPlace())
                .meetingAddress(appointment.getMeetingAddress())
                .description(appointment.getAppointmentDescription())
                .activityStartAt(appointment.getActivityStartAt())
                .activityEndAt(appointment.getActivityEndAt())
                .joinDeadline(appointment.getJoinDeadline())
                .hostDisplayName(appointment.getHostDisplayName())
                .members(members)
                .build();
    }

    private static AppointmentMemberResponse toMemberResponse(
            AppointmentMember member) {
        return AppointmentMemberResponse.builder()
                .appointmentMemberId(member.getAppointmentMemberId())
                .memberId(member.getMemberId())
                .displayName(member.getDisplayName())
                .profileImageUrl(member.getProfileImageUrl())
                .preferredLanguage(member.getPreferredLanguage())
                .membershipStatus(member.getMembershipStatus())
                .attendanceStatus(member.getAttendanceStatus())
                .isHost(Boolean.TRUE.equals(member.getHost()))
                .build();
    }

    private static int calculateTotalPages(long totalElements, int size) {
        return totalElements == 0
                ? 0
                : (int) ((totalElements + size - 1) / size);
    }

    private static String normalizeUppercase(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private static void validateCreateRequest(
            Long memberId,
            AppointmentCreateRequest request) {
        if (memberId == null || memberId <= 0 || request == null
                || request.getItemId() == null || request.getItemId() <= 0
                || request.getItemType() == null
                || request.getLanguageCode() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!ITEM_TYPES.contains(request.getItemType())
                || !LANGUAGES.contains(request.getLanguageCode())
                || isBlank(request.getAppointmentName())
                || request.getAppointmentName().trim().length() > 100
                || request.getMaxMembers() == null
                || request.getMaxMembers() < MIN_MEMBERS
                || request.getMaxMembers() > MAX_MEMBERS
                || request.getDepositAmount() == null
                || request.getDepositAmount().scale() > 0
                || request.getDepositAmount().compareTo(MIN_DEPOSIT) < 0
                || request.getDepositAmount().compareTo(MAX_DEPOSIT) > 0
                || isBlank(request.getMeetingPlace())
                || request.getMeetingPlace().trim().length() > 200
                || lengthExceeds(request.getMeetingAddress(), 500)
                || request.getJoinDeadline() == null
                || request.getActivityStartAt() == null
                || request.getActivityEndAt() == null
                || request.getJoinDeadline().isAfter(
                        request.getActivityStartAt()
                )
                || !request.getActivityStartAt().isBefore(
                        request.getActivityEndAt()
                )) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean lengthExceeds(String value, int maxLength) {
        return value != null && value.trim().length() > maxLength;
    }

    private static String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }

}
