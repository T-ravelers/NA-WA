package me.nawa.appointment.mapper;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MyOngoingAppointment;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.journey.domain.JourneyExploreItem;
import me.nawa.deposit.domain.AttendanceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppointmentMapper {

    // 항목 타입과 운영 기간을 함께 읽는다. 타입만 읽던 시절에는 약속 생성이 이벤트
    // 운영 기간을 볼 방법이 없어, 끝난 축제 날짜로도 약속이 만들어졌다.
    JourneyExploreItem findAvailableItem(@Param("itemId") Long itemId);

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

    // 활동 시작 시각이 된 약속을 일괄로 IN_PROGRESS로 전환한다. 정원이 차서
    // FULL이 된 약속뿐 아니라 정원이 차지 않은 RECRUITING 약속도 대상이다 —
    // 참여 마감 시각이 없어진 뒤로 정원 미달 약속은 FULL을 거치지 않으므로,
    // RECRUITING을 빼면 그런 약속이 활동 시작 뒤에도 모집 중으로 남는다.
    // 정원이 차서 FULL이 되는 경로는 시간과 무관해 joinAppointment가 동기로
    // 처리하므로 여기서 다루지 않는다.
    //
    // 비교 기준 시각은 DB의 CURRENT_TIMESTAMP가 아니라 애플리케이션이 넘긴
    // now를 쓴다. activity_start_at은 애플리케이션의 LocalDateTime.now()
    // 기준으로 저장되는데, DB 서버 컨테이너의 시간대가 애플리케이션
    // (TZ=Asia/Seoul)과 다르면 CURRENT_TIMESTAMP가 그만큼 어긋나 시작 전환이
    // 실제보다 늦게(또는 빠르게) 일어난다.
    int startDueAppointments(@Param("now") LocalDateTime now);

    // 활동 종료 시각이 지난 IN_PROGRESS 약속을 AWAITING_ATTENDANCE로 옮긴다.
    // 시각 비교 기준을 애플리케이션에서 받는 이유는 위와 같다.
    int endDueAppointments(@Param("now") LocalDateTime now);

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
    // 재참여 때 여정을 다시 고르므로 trip_id도 함께 갱신한다.
    int reviveLeftMember(
            @Param("appointmentMemberId") Long appointmentMemberId,
            @Param("tripId") Long tripId
    );

    List<AppointmentMember> findActiveMembersByAppointmentId(
            @Param("appointmentId") Long appointmentId
    );

    // 활동 중 탈퇴로 노쇼가 굳은 LEFT 회원. 보증금이 HELD로 남아 있어 출석
    // 확정 합산과 정산 분배가 ACTIVE 회원과 함께 이 목록을 봐야 한다.
    List<AppointmentMember> findLeftNoShowMembersByAppointmentId(
            @Param("appointmentId") Long appointmentId
    );

    // includeAll=true의 예정/지난 분류 기준도 DB의 NOW()가 아니라 애플리케이션이 넘긴
    // now를 쓴다. 이유는 startDueAppointments와 같다 — activity_start_at은
    // 애플리케이션의 LocalDateTime.now() 기준으로 저장되는데, DB 서버 컨테이너의 시간대가
    // 애플리케이션(TZ=Asia/Seoul)과 다르면 그 시차만큼 경계가 어긋나 지금 시각 근처의
    // 약속이 예정/지난 반대쪽으로 정렬된다.
    List<MyOngoingAppointment> findMyOngoingAppointments(
        @Param("memberId") Long memberId,
        @Param("includeAll") boolean includeAll,
        @Param("now") LocalDateTime now
    );

    // ACTIVE·PENDING(출석 미확정) 회원만 대상으로 한다 — 출석 확정은 한 번만
    // 허용하는 낙관적 상태 전이 가드.
    int updateAttendance(
            @Param("appointmentMemberId") Long appointmentMemberId,
            @Param("attendanceStatus") AttendanceStatus attendanceStatus,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );
}
