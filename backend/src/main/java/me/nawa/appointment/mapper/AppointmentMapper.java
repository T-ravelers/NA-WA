package me.nawa.appointment.mapper;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
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
