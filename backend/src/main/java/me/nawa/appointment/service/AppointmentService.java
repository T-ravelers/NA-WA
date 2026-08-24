package me.nawa.appointment.service;

import lombok.RequiredArgsConstructor;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentJoinRequest;
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
import me.nawa.journey.domain.JourneyExploreItem;
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
import java.time.LocalDate;
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
            AppointmentStatus.FULL,
            AppointmentStatus.IN_PROGRESS,
            // DB에 저장되는 값이라 다른 상태와 똑같이 검색 조건이 될 수 있다.
            AppointmentStatus.AWAITING_ATTENDANCE,
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
                .depositAmount(request.getDepositAmount())
                .appointmentStatus(AppointmentStatus.PAYMENT_PENDING)
                .meetingPlace(request.getMeetingPlace().trim())
                .activityStartAt(activityStartAt)
                .activityEndAt(activityEndAt)
                .build();
        if (appointmentMapper.insertAppointment(appointment) != 1) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        linkJourneyItem(appointment, request.getTripId());

        // 방장이 고른 여정을 멤버십에도 남긴다. trip_items 쪽만 연결하고 여기를 비우면
        // 진행 중인 약속 목록(am.trip_id IS NOT NULL로 거른다)에서 방장이 통째로 빠지고,
        // QR 공동결제도 여행이 연결되지 않았다며 거절한다. 값은 validateJourneyLink가
        // 본인 여정인지·visitDate가 기간 안인지까지 이미 확인한 것이다.
        AppointmentMember host = AppointmentMember.builder()
                .appointmentId(appointment.getAppointmentId())
                .memberId(memberId)
                .tripId(request.getTripId())
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
            Long appointmentId,
            AppointmentJoinRequest request) {
        validateIdentifiers(memberId, appointmentId);
        // 생성과 같은 기준이다 — 여정을 고르지 않은 참여는 받지 않는다.
        if (request == null || request.getTripId() == null || request.getTripId() <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        Appointment appointment = requireAppointmentForUpdate(appointmentId);

        // 참여는 활동이 시작되기 전까지만 받는다. 참여 마감 시각을 따로 두지
        // 않으므로 이 비교가 유일한 시간 경계다.
        if (appointment.getAppointmentStatus() != AppointmentStatus.RECRUITING
                || !LocalDateTime.now().isBefore(appointment.getActivityStartAt())
                || appointment.getCurrentMemberCount() >= appointment.getMaxMembers()) {
            throw new BusinessException(AppointmentErrorCode.JOIN_NOT_AVAILABLE);
        }

        AppointmentMember existing = appointmentMapper
                .findMemberByAppointmentAndMemberForUpdate(appointmentId, memberId);
        if (existing != null && existing.getMembershipStatus() != MembershipStatus.LEFT) {
            throw new BusinessException(AppointmentErrorCode.ALREADY_JOINED);
        }

        // 참여자도 자기 여정에 약속을 건다. 멤버십의 trip_id를 비워 두면 진행 중인
        // 약속 목록(am.trip_id IS NOT NULL로 거른다)에서 빠지고 QR 공동결제도 거절한다.
        Long tripId = request.getTripId();
        validateJoinJourneyLink(memberId, appointment, tripId);

        AppointmentMember member;
        if (existing == null) {
            member = AppointmentMember.builder()
                    .appointmentId(appointmentId)
                    .memberId(memberId)
                    .tripId(tripId)
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
            member.setTripId(tripId);
            reviveLeftMemberAndHoldDeposit(memberId, member);
        }
        linkJourneyItem(appointment, tripId);

        // 이번 참여로 정원이 다 찼으면 바로 FULL로 전환한다. 정원 도달은 시간
        // 기반 전이와 달리 스케줄러를 기다릴 필요가 없다 — 이 트랜잭션이 이미
        // 약속 행을 잠그고 있어 지금 시점에 정확히 알 수 있다.
        if (appointment.getCurrentMemberCount() + 1 >= appointment.getMaxMembers()) {
            if (appointmentMapper.updateAppointmentStatus(
                    appointmentId,
                    AppointmentStatus.RECRUITING,
                    AppointmentStatus.FULL
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
        if (appointmentMapper.reviveLeftMember(
                member.getAppointmentMemberId(),
                member.getTripId()
        ) != 1) {
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
        // 참여 취소는 활동 종료 시각 전까지 가능하다. 종료 후에는 출석 확정
        // 흐름이 전원을 처리하므로 나갈 길이 없다 — 나갈 수 있으면 노쇼 확정을
        // 피해 몰수를 빠져나가는 구멍이 된다.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityEndAt = appointment.getActivityEndAt();
        if (activityEndAt == null || !now.isBefore(activityEndAt)) {
            throw new BusinessException(
                    AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
            );
        }
        // 활동 시작 전 탈퇴는 보증금을 환급한다.
        // 활동 시작~종료 사이 탈퇴는 노쇼로 확정된다 — 보증금을 환급하지 않고
        // HELD로 남겨, 출석 확정 후 정산 배치가 출석 회원에게 분배한다(16절).
        LocalDateTime activityStartAt = appointment.getActivityStartAt();
        boolean noShowLeave = activityStartAt != null
                && !now.isBefore(activityStartAt);

        Deposit deposit = depositMapper.findByAppointmentMemberId(
                member.getAppointmentMemberId()
        );
        if (noShowLeave
                && member.getMembershipStatus() == MembershipStatus.ACTIVE) {
            // ACTIVE인데 예치(HELD)가 없으면 결제 트랜잭션 정합성이 깨진 것이다.
            if (deposit == null || !deposit.isHeld()) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR
                );
            }
            // 출석 상태를 먼저 굳힌다 — updateAttendance는 ACTIVE 행만 받으므로
            // markMemberLeft보다 앞서야 한다.
            if (appointmentMapper.updateAttendance(
                    member.getAppointmentMemberId(),
                    AttendanceStatus.NO_SHOW,
                    now
            ) != 1) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR
                );
            }
        } else if (deposit != null && deposit.isPending()) {
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
        // 참여하며 여정에 걸어 둔 약속 항목도 함께 내린다. 남겨 두면 참여하지도
        // 않는 약속이 여정에 남는다. 방장은 여기 오지 못하므로(위에서 차단) 이
        // 삭제가 방장의 여정 항목을 건드릴 일은 없다. 담아 두기만 했던 항목을
        // 승격시킨 경우까지 함께 사라지는데, 되살리려면 승격 전 상태를 따로
        // 기억해야 해서 이번 범위 밖으로 둔다.
        if (member.getTripId() != null) {
            journeyMapper.softDeleteJourneyItemByAppointment(
                    member.getTripId(),
                    appointmentId
            );
        }
        // 정원이 차서 FULL이던 약속에서 빈자리가 생겼을 때만 재모집으로
        // 되돌린다. 활동이 이미 시작됐다면 새로 참여할 수 없으므로 빈자리가
        // 생겨도 FULL을 유지한다 — 참여 게이트와 같은 경계다.
        if (appointment.getAppointmentStatus() == AppointmentStatus.FULL
                && now.isBefore(appointment.getActivityStartAt())) {
            appointmentMapper.updateAppointmentStatus(
                    appointmentId,
                    AppointmentStatus.FULL,
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
        // 활동이 끝나면 스케줄러가 AWAITING_ATTENDANCE로 옮긴다. 옮기기 전 몇 초
        // 사이에도 화면은 이미 출석 확정을 열어 주므로 두 상태를 다 받는다.
        AppointmentStatus currentStatus = appointment.getAppointmentStatus();
        if (currentStatus != AppointmentStatus.IN_PROGRESS
                && currentStatus != AppointmentStatus.AWAITING_ATTENDANCE) {
            throw new BusinessException(
                    AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION
            );
        }
        // IN_PROGRESS는 활동 '시작' 시각에 스케줄러가 바꾼다. 상태만 보면 활동이
        // 진행되는 도중에도 확정이 통과해, 아직 오는 중인 참여자가 노쇼로 굳는다.
        // 확정에는 되돌리는 상태 전이가 없고 노쇼는 보증금 몰수로 이어지므로
        // 활동이 끝났는지 함께 본다. 화면에도 같은 조건이 있지만(#285) 화면에만
        // 있는 조건은 화면을 거치지 않는 요청을 막지 못한다.
        LocalDateTime activityEndAt = appointment.getActivityEndAt();
        if (activityEndAt == null
                || LocalDateTime.now().isBefore(activityEndAt)) {
            throw new BusinessException(
                    AppointmentErrorCode.ATTENDANCE_NOT_ENDED
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

        // 활동 중에 나가 노쇼로 굳은 LEFT 회원의 보증금도 HELD로 남아 있다.
        // 이 배치가 그 분배까지 책임지므로 합산에 함께 넣는다 — 빠뜨리면
        // 정산 총액과 실제 이체 합이 어긋난다.
        for (AppointmentMember leftNoShow : appointmentMapper
                .findLeftNoShowMembersByAppointmentId(appointmentId)) {
            Deposit deposit = depositMapper.findByAppointmentMemberId(
                    leftNoShow.getAppointmentMemberId()
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
                currentStatus,
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

        return appointmentMapper
            .findMyOngoingAppointments(
                memberId, "ALL".equals(scope), LocalDateTime.now())
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
                .description(appointment.getAppointmentDescription())
                .activityStartAt(appointment.getActivityStartAt())
                .activityEndAt(appointment.getActivityEndAt())
                .hostDisplayName(appointment.getHostDisplayName())
                .members(members)
                .build();
    }

    // 목록·상세 조회에서 실제로 보여줄 상태를 시간 기준으로 즉시 계산한다.
    // 스케줄러가 같은 규칙으로 DB 컬럼을 따라잡지만, 화면이 그 주기를 기다리지
    // 않게 여기서 한 번 더 본다 — 스케줄러가 늦거나 멈춰도 화면은 정확하다.
    //
    // 이 값은 화면 표시에만 쓴다. 트립 연결·QR 공동결제처럼 저장된 값으로 걸러야
    // 하는 로직(findMyOngoingAppointments 등)은 컬럼을 직접 본다.
    private static AppointmentStatus resolveDisplayStatus(
            Appointment appointment) {
        AppointmentStatus status = appointment.getAppointmentStatus();
        LocalDateTime now = LocalDateTime.now();

        if (status == AppointmentStatus.RECRUITING
                && appointment.getCurrentMemberCount()
                        >= appointment.getMaxMembers()) {
            status = AppointmentStatus.FULL;
        }
        // 정원이 차지 않아 RECRUITING인 약속도 활동 시작 시각이 지나면 진행
        // 중으로 보여야 한다. FULL을 거치지 않는 경로라 두 상태를 함께 본다.
        if ((status == AppointmentStatus.RECRUITING
                || status == AppointmentStatus.FULL)
                && !now.isBefore(appointment.getActivityStartAt())) {
            status = AppointmentStatus.IN_PROGRESS;
        }
        // 활동이 끝났는데 방장이 아직 출석을 확정하지 않은 약속. 스케줄러가 같은
        // 조건으로 DB도 옮기므로 여기까지 오는 것은 그 주기 안의 몇 초뿐이다.
        if (status == AppointmentStatus.IN_PROGRESS
                && appointment.getActivityEndAt() != null
                && !now.isBefore(appointment.getActivityEndAt())) {
            status = AppointmentStatus.AWAITING_ATTENDANCE;
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
        if (activityStartAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        JourneyExploreItem exploreItem = appointmentMapper.findAvailableItem(
                request.getItemId()
        );
        if (exploreItem == null
                || !request.getItemType().equals(exploreItem.getItemType())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 여정 담기와 같은 규칙을 쓴다. 예전에는 이 검사가 없어서, 여정에 담는 것은
        // 막히는 날짜로도 약속은 만들어졌다.
        if (!exploreItem.coversVisitDate(request.getVisitDate())) {
            throw new BusinessException(
                    JourneyErrorCode.JOURNEY_ITEM_OUTSIDE_ITEM_PERIOD
            );
        }
    }

    // 여정 소유자인지, 방문 날짜가 여정 기간 안인지 확인한다. 실제 trip_items
    // 저장은 insertAppointment로 appointmentId를 확보한 뒤 linkJourneyItem에서
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
        // 담아만 둔 자리는 막지 않는다 — linkJourneyItem이 약속 항목으로 올린다.
        // 담아 뒀다는 이유로 약속 생성이 막히면 참여와 앞뒤가 맞지 않는다. 여기서
        // 걸러 내는 것은 다른 약속이 이미 걸린 자리뿐이다. 존재만 보는 조회 대신
        // 행을 잠그고 가져와, 그 사이 다른 세션이 같은 자리를 차지하지 못하게 한다.
        JourneyItem existingItem = journeyMapper.findJourneyItemByItemAndDateForUpdate(
                request.getTripId(),
                request.getItemId(),
                request.getVisitDate()
        );
        if (existingItem != null && existingItem.getAppointmentId() != null) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_ITEM_DUPLICATE);
        }
    }

    /**
     * 참여자가 고른 여정이 이 약속을 담을 수 있는지 본다. 생성과 달리 날짜를 받지
     * 않는다 — 약속이 이미 활동 날짜를 갖고 있어 고를 여지가 없고, 그 날짜가 여정
     * 기간 밖이면 애초에 담을 수 없는 여정이다. 중복은 여기서 막지 않는다.
     * "Add to journey"로 이미 담아 둔 자리는 거절 대신 약속 항목으로 올린다.
     */
    private void validateJoinJourneyLink(
            Long memberId,
            Appointment appointment,
            Long tripId) {
        Journey journey = journeyMapper.findJourneyByIdForUpdate(tripId);
        if (journey == null) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_NOT_FOUND);
        }
        if (!journey.getMemberId().equals(memberId)) {
            throw new BusinessException(JourneyErrorCode.JOURNEY_FORBIDDEN);
        }
        LocalDate visitDate = appointment.getActivityStartAt().toLocalDate();
        if (visitDate.isBefore(journey.getStartDate())
                || visitDate.isAfter(journey.getEndDate())) {
            throw new BusinessException(
                    JourneyErrorCode.JOURNEY_ITEM_DATE_OUT_OF_RANGE
            );
        }
    }

    /**
     * 회원의 여정에 약속을 건다. 방장의 생성과 참여자의 참여가 같은 경로를 쓴다 —
     * 어느 쪽이든 "그 장소를 그 날짜로 여정에 건다"는 동작이 같기 때문이다.
     *
     * (trip_id, item_id, visit_date)는 살아 있는 행에 대해 UNIQUE라, 그 장소를 이미
     * 같은 날짜로 담아 둔 회원은 새 행을 넣을 수 없다 — 담아 뒀다는 이유로 약속이
     * 막히면 앞뒤가 맞지 않으므로, 그 행을 약속 항목으로 올린다. 승격은 새로 넣는
     * 것과 달리 회원이 담을 때 정한 표시 순서와 메모를 그대로 둔다. 다른 약속이
     * 이미 걸려 있을 때만 중복으로 거절한다.
     */
    private void linkJourneyItem(Appointment appointment, Long tripId) {
        LocalDate visitDate = appointment.getActivityStartAt().toLocalDate();
        JourneyItem existingItem = journeyMapper.findJourneyItemByItemAndDateForUpdate(
                tripId,
                appointment.getItemId(),
                visitDate
        );
        if (existingItem != null) {
            if (appointment.getAppointmentId().equals(existingItem.getAppointmentId())) {
                return;
            }
            if (journeyMapper.promoteJourneyItemToAppointment(
                    existingItem.getTripItemId(),
                    appointment.getAppointmentId()
            ) != 1) {
                throw new BusinessException(JourneyErrorCode.JOURNEY_ITEM_DUPLICATE);
            }
            return;
        }

        JourneyItem journeyItem = JourneyItem.builder()
                .tripId(tripId)
                .itemId(appointment.getItemId())
                .visitDate(visitDate)
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

    private static String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }

}
