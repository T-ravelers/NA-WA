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
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.domain.DepositPayoutBatch;
import me.nawa.deposit.domain.ResolutionReason;
import me.nawa.deposit.mapper.DepositMapper;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyItem;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.mapper.JourneyMapper;
import me.nawa.wallet.domain.SystemWalletCode;
import me.nawa.wallet.domain.enums.TransferType;
import me.nawa.wallet.service.WalletTransferService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final DepositPayoutBatchMapper depositPayoutBatchMapper;
    private final WalletTransferService walletTransferService;
    private final JourneyMapper journeyMapper;

    @Transactional
    public Appointment createAppointment(
            Long memberId,
            AppointmentCreateRequest request) {
        validateCreateRequest(memberId, request);
        validateJourneyLink(memberId, request);

        // 활동 시작·종료는 visitDate 하루 위에서만 조립된다. 그래서 시작·종료가
        // 같은 날짜인지 별도로 검사할 필요가 없다 — 애초에 다른 날짜가 될 수 없다.
        LocalDateTime activityStartAt = LocalDateTime.of(
                request.getVisitDate(), request.getActivityStartTime());
        LocalDateTime activityEndAt = LocalDateTime.of(
                request.getVisitDate(), request.getActivityEndTime());

        Appointment appointment = Appointment.builder()
                .itemId(request.getItemId())
                .hostMemberId(memberId)
                .languageCode(request.getLanguageCode())
                .appointmentName(request.getAppointmentName().trim())
                .maxMembers(request.getMaxMembers())
                .joinDeadline(request.getJoinDeadline())
                .depositAmount(request.getDepositAmount())
                .appointmentStatus(AppointmentStatus.PAYMENT_PENDING)
                .meetingPlace(request.getMeetingPlace().trim())
                .meetingAddress(request.getMeetingAddress())
                .activityStartAt(activityStartAt)
                .activityEndAt(activityEndAt)
                .build();
        if (appointmentMapper.insertAppointment(appointment) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        confirmJourneyItem(appointment, request);

        AppointmentMember host = AppointmentMember.builder()
                .appointmentId(appointment.getAppointmentId())
                .memberId(memberId)
                .membershipStatus(MembershipStatus.PENDING)
                .attendanceStatus(AttendanceStatus.PENDING)
                .build();
        if (appointmentMapper.insertAppointmentMember(host) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        holdDepositForNewMember(memberId, host, request.getDepositAmount());

        if (appointmentMapper.updateAppointmentStatus(
                appointment.getAppointmentId(),
                AppointmentStatus.PAYMENT_PENDING,
                AppointmentStatus.RECRUITING
        ) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        appointment.setAppointmentStatus(AppointmentStatus.RECRUITING);
        appointment.setCurrentMemberCount(1);
        return appointment;
    }

    @Transactional(readOnly = true)
    public AppointmentDetailResponse toCreatedResponse(
            Appointment appointment) {
        List<AppointmentMemberResponse> members = appointmentMapper
                .findActiveMembersByAppointmentId(appointment.getAppointmentId())
                .stream()
                .map(AppointmentService::toMemberResponse)
                .toList();
        return toDetailResponse(appointment, members);
    }

    @Transactional
    public AppointmentMemberResponse joinAppointment(
            Long memberId,
            Long appointmentId) {
        validateIdentifiers(memberId, appointmentId);
        Appointment appointment = requireAppointmentForUpdate(appointmentId);

        if (appointment.getAppointmentStatus() != AppointmentStatus.RECRUITING
                || appointment.getJoinDeadline().isBefore(LocalDateTime.now())
                || appointment.getCurrentMemberCount() >= appointment.getMaxMembers()) {
            throw new BusinessException(AppointmentErrorCode.JOIN_NOT_AVAILABLE);
        }

        AppointmentMember existing = appointmentMapper
                .findMemberByAppointmentAndMemberForUpdate(appointmentId, memberId);
        if (existing != null && existing.getMembershipStatus() != MembershipStatus.LEFT) {
            throw new BusinessException(AppointmentErrorCode.ALREADY_JOINED);
        }

        AppointmentMember member;
        if (existing == null) {
            member = AppointmentMember.builder()
                    .appointmentId(appointmentId)
                    .memberId(memberId)
                    .membershipStatus(MembershipStatus.PENDING)
                    .attendanceStatus(AttendanceStatus.PENDING)
                    .build();
            if (appointmentMapper.insertAppointmentMember(member) != 1) {
                throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
            holdDepositForNewMember(memberId, member, appointment.getDepositAmount());
        } else {
            // 참여 취소(LEFT) 후 마감 시각 전 재참여다. appointment_id·
            // member_id, appointment_member_id UNIQUE 제약 때문에 새 행을
            // 만들 수 없어 기존 참여·보증금 행을 재활용한다.
            member = existing;
            reviveLeftMemberAndHoldDeposit(memberId, member);
        }

        // 이번 참여로 정원이 다 찼으면 마감 시각을 기다리지 않고 바로 CLOSED로
        // 전환한다. 정원 도달은 마감 시각과 달리 스케줄러가 아니라 이 트랜잭션이
        // 이미 약속 행을 잠그고 있는 지금 시점에 정확히 알 수 있다.
        if (appointment.getCurrentMemberCount() + 1 >= appointment.getMaxMembers()) {
            if (appointmentMapper.updateAppointmentStatus(
                    appointmentId,
                    AppointmentStatus.RECRUITING,
                    AppointmentStatus.CLOSED
            ) != 1) {
                throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        AppointmentMember active = appointmentMapper.findMemberByIdForUpdate(
                appointmentId, member.getAppointmentMemberId()
        );
        return toMemberResponse(active);
    }

    // 신규 참여자(방장 포함)의 보증금을 회원 지갑 -> DEPOSIT_POOL로 즉시 예치한다.
    // 약속·참여 행 생성과 같은 트랜잭션에서 호출되어, 실패하면 참여 자체가 롤백된다.
    private void holdDepositForNewMember(
            Long memberId,
            AppointmentMember member,
            BigDecimal depositAmount) {
        Deposit deposit = Deposit.pending(
                member.getAppointmentMemberId(), depositAmount
        );
        if (depositMapper.insert(deposit) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        holdDeposit(memberId, member, deposit);
    }

    // 참여 취소(LEFT) 이력이 있는 회원의 재참여다. appointment_member_id·
    // appointment_id+member_id가 각각 UNIQUE라 새 참여·보증금 행을 만들 수
    // 없으므로, 기존 행을 PENDING/REFUNDED에서 되돌려 재사용한다.
    private void reviveLeftMemberAndHoldDeposit(
            Long memberId,
            AppointmentMember member) {
        if (appointmentMapper.reviveLeftMember(member.getAppointmentMemberId()) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        member.setMembershipStatus(MembershipStatus.PENDING);

        Deposit deposit = depositMapper.findByAppointmentMemberId(
                member.getAppointmentMemberId()
        );
        if (deposit == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        deposit.revive();
        if (depositMapper.revive(deposit.getDepositId()) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        holdDeposit(memberId, member, deposit);
    }

    // 회원 지갑 -> DEPOSIT_POOL 이체를 실행하고 보증금·참여 행을 HELD/ACTIVE로
    // 확정한다. 신규 참여와 재참여 양쪽에서 공유하는 마지막 단계다.
    private void holdDeposit(
            Long memberId,
            AppointmentMember member,
            Deposit deposit) {
        long transferId = walletTransferService.transferToSystemWallet(
                memberId,
                memberId,
                SystemWalletCode.DEPOSIT_POOL,
                deposit.getAmount(),
                TransferType.DEPOSIT_HOLD.name(),
                "약속 보증금 예치"
        );

        LocalDateTime heldAt = LocalDateTime.now();
        deposit.hold(transferId, heldAt);
        if (depositMapper.markHeld(deposit.getDepositId(), transferId, heldAt) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (appointmentMapper.markMemberActive(member.getAppointmentMemberId()) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        member.setMembershipStatus(MembershipStatus.ACTIVE);
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
        // 방장은 자기 참여를 취소할 수 없다. 상태와 무관하게 즉시 차단한다.
        if (memberId.equals(appointment.getHostMemberId())) {
            throw new BusinessException(
                    AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
            );
        }
        // 참여 취소는 참여 마감 시각 전까지만 가능하다. joinDeadline은 항상
        // activityStartAt보다 늦을 수 없으므로(생성 시 검증), 이 조건 하나로
        // 활동 시작 이후(IN_PROGRESS/COMPLETED) 취소도 함께 막힌다.
        if (LocalDateTime.now().isAfter(appointment.getJoinDeadline())) {
            throw new BusinessException(
                    AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
            );
        }

        Deposit deposit = depositMapper.findByAppointmentMemberId(
                member.getAppointmentMemberId()
        );
        if (deposit != null && deposit.isPending()) {
            cancelPendingDeposit(deposit);
        } else if (deposit != null && deposit.isHeld()) {
            refundHeldDeposit(memberId, deposit);
        }
        if (appointmentMapper.markMemberLeft(
                member.getAppointmentMemberId()
        ) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
        // 정원 도달로 CLOSED된 약속에서 빈자리가 생겼다. joinDeadline은 이미
        // 위에서 지나지 않았음을 확인했으므로(활동 시작 전) 시간 기준으로는
        // 항상 재모집 가능한 상태다 — 마감 시각 경과로 CLOSED된 경우라면
        // 애초에 이 지점까지 오지 못한다.
        if (appointment.getAppointmentStatus() == AppointmentStatus.CLOSED) {
            appointmentMapper.updateAppointmentStatus(
                    appointmentId,
                    AppointmentStatus.CLOSED,
                    AppointmentStatus.RECRUITING
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
        if (request == null
                || request.getMembers() == null
                || request.getMembers().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Appointment appointment = requireAppointmentForUpdate(appointmentId);
        if (!hostMemberId.equals(appointment.getHostMemberId())) {
            throw new BusinessException(
                    AppointmentErrorCode.APPOINTMENT_FORBIDDEN
            );
        }
        if (appointment.getAppointmentStatus()
                != AppointmentStatus.IN_PROGRESS) {
            throw new BusinessException(
                    AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION
            );
        }

        Map<Long, AttendanceStatus> requestedByMemberId =
                toRequestedAttendanceMap(request);
        // 출석자가 한 명도 없으면 노쇼 보증금을 나눠 줄 대상이 없어 정산 자체가
        // 성립하지 않는다. 정산 배치를 만들기 전에 여기서 미리 거부한다(16절).
        if (requestedByMemberId.values().stream()
                .noneMatch(status -> status == AttendanceStatus.ATTENDED)) {
            throw new BusinessException(
                    AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION
            );
        }
        List<AppointmentMember> activeMembers = appointmentMapper
                .findActiveMembersByAppointmentId(appointmentId);
        if (activeMembers.size() != requestedByMemberId.size()) {
            throw new BusinessException(
                    AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION
            );
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        BigDecimal totalHeldAmount = BigDecimal.ZERO;
        for (AppointmentMember member : activeMembers) {
            AttendanceStatus status =
                    requestedByMemberId.get(member.getMemberId());
            if (status == null) {
                throw new BusinessException(
                        AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION
                );
            }
            if (appointmentMapper.updateAttendance(
                    member.getAppointmentMemberId(), status, confirmedAt
            ) != 1) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR
                );
            }
            Deposit deposit = depositMapper.findByAppointmentMemberId(
                    member.getAppointmentMemberId()
            );
            if (deposit == null || !deposit.isHeld()) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR
                );
            }
            totalHeldAmount = totalHeldAmount.add(deposit.getAmount());
        }

        if (appointmentMapper.updateAppointmentStatus(
                appointmentId,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.COMPLETED
        ) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        // 실제 지갑 이체는 하지 않고 배치만 PENDING으로 남긴다. 이후 별도
        // 비동기 처리(16절)가 이 배치를 집어 환급·노쇼 분배 이체를 실행한다.
        DepositPayoutBatch batch = DepositPayoutBatch.pending(
                appointmentId,
                ResolutionReason.APPOINTMENT_COMPLETED,
                totalHeldAmount,
                confirmedAt,
                "APPOINTMENT_COMPLETION-" + appointmentId
        );
        if (depositPayoutBatchMapper.insert(batch) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    // 요청의 회원별 출석 상태를 맵으로 정리하면서, 값 누락·PENDING 지정·중복
    // memberId를 여기서 한 번에 걸러낸다.
    private static Map<Long, AttendanceStatus> toRequestedAttendanceMap(
            AppointmentAttendanceRequest request) {
        Map<Long, AttendanceStatus> requestedByMemberId = new HashMap<>();
        for (AppointmentAttendanceRequest.MemberAttendance entry
                : request.getMembers()) {
            if (entry.getMemberId() == null
                    || entry.getAttendanceStatus() == null
                    || entry.getAttendanceStatus()
                            == AttendanceStatus.PENDING
                    || requestedByMemberId.put(
                            entry.getMemberId(),
                            entry.getAttendanceStatus()
                    ) != null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }
        return requestedByMemberId;
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

    /**
     * scope=ONGOING은 기존 계약 그대로 진행 중 약속만 다가오는 순으로,
     * scope=ALL은 취소를 제외한 전체를 예정(임박한 순) 먼저, 지난 약속(최근 순)
     * 순서로 돌려준다(프로필의 약속 목록용).
     */
    @Transactional(readOnly = true)
    public List<MyOngoingAppointmentResponse> getMyOngoingAppointments(
            Long memberId, String scope){
        if(memberId == null || memberId <= 0){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if(!"ONGOING".equals(scope) && !"ALL".equals(scope)){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return appointmentMapper.findMyOngoingAppointments(memberId, "ALL".equals(scope))
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

    // DEPOSIT_POOL -> 회원 지갑으로 보증금을 되돌리고 보증금 상태를 REFUNDED로
    // 반영한다. 참여 취소 시 이미 예치(HELD)된 보증금을 환급하는 경로다.
    private void refundHeldDeposit(Long memberId, Deposit deposit) {
        long transferId = walletTransferService.transferFromSystemWallet(
                memberId,
                SystemWalletCode.DEPOSIT_POOL,
                memberId,
                deposit.getAmount(),
                TransferType.DEPOSIT_REFUND.name(),
                "약속 참여 취소 보증금 환급"
        );
        LocalDateTime resolvedAt = LocalDateTime.now();
        deposit.refund(resolvedAt);
        if (depositMapper.markRefunded(deposit.getDepositId(), resolvedAt) != 1) {
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
                .appointmentStatus(resolveDisplayStatus(appointment))
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
                .appointmentStatus(resolveDisplayStatus(appointment))
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

    // 목록·상세 조회에서 실제로 보여줄 상태를 시간 기준으로 즉시 계산한다.
    // 스케줄러(60초 주기)가 DB 컬럼을 아직 못 따라잡았어도, 사용자에게는 여기서
    // 계산한 값을 곧바로 보여준다. DB의 실제 appointment_status는 스케줄러가
    // 뒤에서 계속 따라잡으므로, 이 메서드는 화면 표시에만 쓰고 트립 연결·QR
    // 공동결제처럼 실제 DB 상태 일관성이 중요한 로직(findMyOngoingAppointments
    // 등)에는 쓰지 않는다.
    private static AppointmentStatus resolveDisplayStatus(
            Appointment appointment) {
        AppointmentStatus status = appointment.getAppointmentStatus();
        LocalDateTime now = LocalDateTime.now();

        if (status == AppointmentStatus.RECRUITING
                && (now.isAfter(appointment.getJoinDeadline())
                        || appointment.getCurrentMemberCount()
                                >= appointment.getMaxMembers())) {
            status = AppointmentStatus.CLOSED;
        }
        if (status == AppointmentStatus.CLOSED
                && !now.isBefore(appointment.getActivityStartAt())) {
            status = AppointmentStatus.IN_PROGRESS;
        }
        return status;
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

    private void validateCreateRequest(
            Long memberId,
            AppointmentCreateRequest request) {
        if (memberId == null || memberId <= 0 || request == null
                || request.getItemId() == null || request.getItemId() <= 0
                || request.getItemType() == null
                || request.getTripId() == null || request.getTripId() <= 0
                || request.getVisitDate() == null
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
                || request.getActivityStartTime() == null
                || request.getActivityEndTime() == null
                || !request.getActivityStartTime().isBefore(
                        request.getActivityEndTime()
                )) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // activityStartAt/activityEndAt은 visitDate 위에서만 조립되므로, 종료가
        // 시작보다 늦은지는 시각 비교(위)만으로 항상 하루 안에서 성립한다.
        LocalDateTime activityStartAt = LocalDateTime.of(
                request.getVisitDate(), request.getActivityStartTime());
        if (request.getJoinDeadline().isAfter(activityStartAt)
                || activityStartAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (!request.getItemType().equals(
                appointmentMapper.findAvailableItemType(request.getItemId())
        )) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 여정 소유자인지, 방문 날짜가 여정 기간 안인지 확인한다. 실제 trip_items
    // 저장은 insertAppointment로 appointmentId를 확보한 뒤 confirmJourneyItem에서
    // 한다 — 여기서는 조기에 실패시키는 역할만 한다.
    private void validateJourneyLink(
            Long memberId,
            AppointmentCreateRequest request) {
        Journey journey = journeyMapper.findJourneyByIdForUpdate(request.getTripId());
        if (journey == null) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_NOT_FOUND);
        }
        if (!journey.getMemberId().equals(memberId)) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_FORBIDDEN);
        }
        if (request.getVisitDate().isBefore(journey.getStartDate())
                || request.getVisitDate().isAfter(journey.getEndDate())) {
            throw new BusinessException(
                    JourneyErrorCode.JOURNEY_ITEM_DATE_OUT_OF_RANGE
            );
        }
        if (journeyMapper.existsJourneyItem(
                request.getTripId(),
                request.getItemId(),
                request.getVisitDate()
        )) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_ITEM_DUPLICATE);
        }
    }

    // 같은 트랜잭션 안에서 여정 항목을 CONFIRMED로 만든다. validateJourneyLink가
    // 미리 중복을 걸렀더라도, 같은 계정의 다른 세션이 그 사이 먼저 확정해버리는
    // 드문 경쟁 상태가 있을 수 있어 유니크 제약 위반을 별도로 처리한다.
    private void confirmJourneyItem(
            Appointment appointment,
            AppointmentCreateRequest request) {
        JourneyItem journeyItem = JourneyItem.builder()
                .tripId(request.getTripId())
                .itemId(request.getItemId())
                .visitDate(request.getVisitDate())
                .tripItemStatus("CONFIRMED")
                .displayOrder(0)
                .appointmentId(appointment.getAppointmentId())
                .build();
        try {
            journeyMapper.insertConfirmedJourneyItem(journeyItem);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    JourneyErrorCode.JOURNEY_ITEM_DUPLICATE,
                    exception
            );
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
