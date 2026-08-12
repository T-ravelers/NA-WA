package me.nawa.appointment.mapper;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
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

    List<AppointmentMember> findActiveMembersByAppointmentId(
            @Param("appointmentId") Long appointmentId
    );
}
