package me.nawa.appointment.service;

import lombok.RequiredArgsConstructor;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.mapper.DepositMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 10;
    private static final BigDecimal MIN_DEPOSIT = BigDecimal.valueOf(5_000);
    private static final BigDecimal MAX_DEPOSIT = BigDecimal.valueOf(50_000);
    private static final Set<String> ITEM_TYPES = Set.of("EVENT", "PLACE");
    private static final Set<String> LANGUAGES = Set.of(
            "en",
            "ja",
            "zh-TW",
            "vi"
    );

    private final AppointmentMapper appointmentMapper;
    private final DepositMapper depositMapper;

    @Transactional
    public Appointment createAppointment(
            Long memberId,
            AppointmentCreateRequest request) {
        validateCreateRequest(memberId, request);

        String storedItemType = appointmentMapper.findAvailableItemType(
                request.getItemId()
        );
        if (!request.getItemType().equals(storedItemType)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Appointment appointment = Appointment.builder()
                .itemId(request.getItemId())
                .itemType(storedItemType)
                .hostMemberId(memberId)
                .languageCode(request.getLanguageCode())
                .appointmentName(request.getAppointmentName().trim())
                .maxMembers(request.getMaxMembers())
                .joinDeadline(request.getJoinDeadline())
                .depositAmount(request.getDepositAmount())
                .appointmentStatus(AppointmentStatus.PAYMENT_PENDING)
                .meetingPlace(request.getMeetingPlace().trim())
                .meetingAddress(normalizeOptional(request.getMeetingAddress()))
                .activityStartAt(request.getActivityStartAt())
                .activityEndAt(request.getActivityEndAt())
                .build();
        appointmentMapper.insertAppointment(appointment);
        requireGeneratedId(appointment.getAppointmentId());

        AppointmentMember host = AppointmentMember.builder()
                .appointmentId(appointment.getAppointmentId())
                .memberId(memberId)
                .membershipStatus(MembershipStatus.PENDING)
                .attendanceStatus(AttendanceStatus.PENDING)
                .host(true)
                .build();
        appointmentMapper.insertAppointmentMember(host);
        requireGeneratedId(host.getAppointmentMemberId());

        Deposit deposit = Deposit.pending(
                host.getAppointmentMemberId(),
                request.getDepositAmount()
        );
        if (depositMapper.insert(deposit) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        appointment.setCurrentMemberCount(0);
        return appointment;
    }

    private static void validateCreateRequest(
            Long memberId,
            AppointmentCreateRequest request) {
        if (memberId == null || memberId <= 0 || request == null
                || request.getItemId() == null || request.getItemId() <= 0
                || !ITEM_TYPES.contains(request.getItemType())
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

    private static void requireGeneratedId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
