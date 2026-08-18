package me.nawa.appointment.mapper;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MyOngoingAppointment;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    List<AppointmentMember> findActiveMembersByAppointmentId(
            @Param("appointmentId") Long appointmentId
    );

    List<MyOngoingAppointment> findMyOngoingAppointments(
        @Param("memberId") Long memberId
    );
}
