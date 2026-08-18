package me.nawa.appointment.mapper;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MyOngoingAppointment;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.deposit.domain.AttendanceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppointmentMapper {

    String findAvailableItemType(@Param("itemId") Long itemId);

    int insertAppointment(Appointment appointment);

    int insertAppointmentMember(AppointmentMember appointmentMember);

    // fromStatus 조건이 안 맞으면 0행을 반환한다 — 낙관적 상태 전이 가드.
    int updateAppointmentStatus(
            @Param("appointmentId") Long appointmentId,
            @Param("fromStatus") AppointmentStatus fromStatus,
            @Param("toStatus") AppointmentStatus toStatus
    );

    int markMemberActive(
            @Param("appointmentMemberId") Long appointmentMemberId
    );

    // 참여 마감 시각이 지난 RECRUITING 약속을 일괄로 CLOSED로 전환한다.
    // 정원이 차서 CLOSED가 되는 경로는 시간과 무관해 joinAppointment가 동기로
    // 처리하므로 여기서 다루지 않는다.
    int closeExpiredRecruitingAppointments();

    // 활동 시작 시각이 된 CLOSED 약속을 일괄로 IN_PROGRESS로 전환한다.
    int startDueClosedAppointments();

    List<Appointment> searchAppointments(
            @Param("request") AppointmentSearchRequest request,
            @Param("offset") int offset
    );

    long countAppointments(
            @Param("request") AppointmentSearchRequest request
    );

    Appointment findAppointmentById(
            @Param("appointmentId") Long appointmentId
    );

    Appointment findAppointmentByIdForUpdate(
            @Param("appointmentId") Long appointmentId
    );

    AppointmentMember findMemberByAppointmentAndMemberForUpdate(
            @Param("appointmentId") Long appointmentId,
            @Param("memberId") Long memberId
    );

    AppointmentMember findMemberByAppointmentAndMember(
            @Param("appointmentId") Long appointmentId,
            @Param("memberId") Long memberId
    );

    AppointmentMember findMemberByIdForUpdate(
            @Param("appointmentId") Long appointmentId,
            @Param("appointmentMemberId") Long appointmentMemberId
    );

    int markMemberLeft(
            @Param("appointmentMemberId") Long appointmentMemberId
    );

    // 참여 취소(LEFT) 후 재참여를 위해 기존 행을 되돌린다. appointment_id·
    // member_id UNIQUE 제약 때문에 재참여 시 새 행을 만들 수 없어 재활용한다.
    int reviveLeftMember(
            @Param("appointmentMemberId") Long appointmentMemberId
    );

    List<AppointmentMember> findActiveMembersByAppointmentId(
            @Param("appointmentId") Long appointmentId
    );

    List<MyOngoingAppointment> findMyOngoingAppointments(
        @Param("memberId") Long memberId,
        @Param("includeAll") boolean includeAll
    );

    // ACTIVE·PENDING(출석 미확정) 회원만 대상으로 한다 — 출석 확정은 한 번만
    // 허용하는 낙관적 상태 전이 가드.
    int updateAttendance(
            @Param("appointmentMemberId") Long appointmentMemberId,
            @Param("attendanceStatus") AttendanceStatus attendanceStatus,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );
}
