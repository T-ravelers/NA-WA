package me.nawa.appointment.mapper;

import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentMapperXmlTest {
    private static final String MAPPER_RESOURCE =
            "me/nawa/appointment/mapper/AppointmentMapper.xml";

    @Test
    void mapperXml_registersAppointmentReadStatements() throws Exception {
        Configuration configuration = configuration();

        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findAvailableItemType"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.insertAppointment"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.insertAppointmentMember"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.searchAppointments"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.countAppointments"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findAppointmentById"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findActiveMembersByAppointmentId"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findAppointmentByIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findMemberByAppointmentAndMemberForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findMemberByAppointmentAndMember"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findMemberByIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.markMemberLeft"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findMyOngoingAppointments"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.updateAppointmentStatus"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.markMemberActive"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.startDueAppointments"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.updateAttendance"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.findLeftNoShowMembersByAppointmentId"
        ));
    }

    // 활동 중 탈퇴로 굳은 노쇼만 골라야 한다 — 마감 전 정상 탈퇴(LEFT +
    // attendance PENDING)가 섞이면 환급이 끝난 보증금을 다시 분배하려 든다.
    @Test
    void findLeftNoShowMembers_onlyTargetsLeftNoShowMembers() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("appointmentId", 1L);

        String sql = boundSql("findLeftNoShowMembersByAppointmentId", parameters);

        assertTrue(sql.contains("membership_status = 'LEFT'"));
        assertTrue(sql.contains("attendance_status = 'NO_SHOW'"));
    }

    @Test
    void updateAttendance_onlyTargetsActivePendingMembers() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("appointmentMemberId", 1L);
        parameters.put("attendanceStatus", me.nawa.deposit.domain.AttendanceStatus.ATTENDED);
        parameters.put("confirmedAt", LocalDateTime.now());

        String sql = boundSql("updateAttendance", parameters);

        assertTrue(sql.contains("attendance_status = ?"));
        assertTrue(sql.contains("membership_status = 'ACTIVE'"));
        assertTrue(sql.contains("attendance_status = 'PENDING'"));
    }

    // 정원이 차지 않아 RECRUITING에 남아 있는 약속도 활동 시작 시각이 되면
    // IN_PROGRESS로 넘어가야 한다. 참여 마감이 없어진 뒤로 그런 약속은 FULL을
    // 거치지 않으므로, IN 목록에서 RECRUITING이 빠지면 영원히 모집 중으로 남는다.
    @Test
    void startDueAppointments_targetsRecruitingAndFullPastActivityStart()
            throws Exception {
        String sql = boundSql(
                "startDueAppointments",
                Map.of("now", LocalDateTime.now())
        );

        assertTrue(sql.contains("appointment_status = 'IN_PROGRESS'"));
        assertTrue(sql.contains("appointment_status IN ('RECRUITING', 'FULL')"));
        assertTrue(sql.contains("activity_start_at <="));
    }

    @Test
    void updateAppointmentStatus_guardsOnFromStatus() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("appointmentId", 1L);
        parameters.put("fromStatus", me.nawa.appointment.domain.AppointmentStatus.PAYMENT_PENDING);
        parameters.put("toStatus", me.nawa.appointment.domain.AppointmentStatus.RECRUITING);

        String sql = boundSql("updateAppointmentStatus", parameters);

        assertTrue(sql.contains("appointment_status = ?"));
        assertTrue(sql.contains("WHERE appointment_id = ?"));
    }

    @Test
    void markMemberActive_onlyTransitionsFromPending() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("appointmentMemberId", 1L);

        String sql = boundSql("markMemberActive", parameters);

        assertTrue(sql.contains("membership_status = 'ACTIVE'"));
        assertTrue(sql.contains("membership_status = 'PENDING'"));
    }

    @Test
    void appointmentList_withoutOptionalFilters_keepsOnlyBaseConditions()
            throws Exception {
        AppointmentSearchRequest request = new AppointmentSearchRequest();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);

        String sql = boundSql("searchAppointments", parameters);

        assertTrue(sql.contains("a.deleted_at IS NULL"));
        assertFalse(sql.contains("a.item_id = ?"));
        assertFalse(sql.contains("a.appointment_status = ?"));
    }

    @Test
    void activeMemberList_excludesPendingAndLeftMembers() throws Exception {
        String sql = boundSql(
                "findActiveMembersByAppointmentId",
                Map.of("appointmentId", 1L)
        );

        assertTrue(sql.contains("am.membership_status = 'ACTIVE'"));
        assertTrue(sql.contains("am.deleted_at IS NULL"));
    }

    @Test
    void myOngoingAppointmentList_filtersByActiveTripLinkedInProgressMembership()
            throws Exception {
        String sql = boundSql(
                "findMyOngoingAppointments",
                Map.of("memberId", 1L)
        );

        assertTrue(sql.contains("am.membership_status = 'ACTIVE'"));
        assertTrue(sql.contains("am.trip_id IS NOT NULL"));
        // 활동이 끝나도 방장이 출석을 확정하기 전까지는 공동결제 대상으로 남는다.
        // 종료 전이를 DB에 적기 시작하면서 그 구간의 상태가 갈렸을 뿐이라, 한
        // 상태만 보면 활동이 끝나는 순간 목록에서 사라진다.
        assertTrue(sql.contains(
                "a.appointment_status IN ('IN_PROGRESS', 'AWAITING_ATTENDANCE')"
        ));
    }

    @Test
    void myOngoingAppointmentList_classifiesByBoundParameterNotDatabaseClock()
            throws Exception {
        String sql = boundSql(
                "findMyOngoingAppointments",
                Map.of("memberId", 1L, "includeAll", true, "now", LocalDateTime.now())
        );

        // 예정/지난 경계는 애플리케이션이 넘긴 값으로 갈려야 한다. DB 시계로 돌아가면
        // DB 컨테이너 시간대가 어긋난 만큼 목록 순서가 뒤집힌다.
        assertFalse(sql.contains("NOW()"));
        assertTrue(sql.contains("ORDER BY (a.activity_start_at < ?)"));
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    MAPPER_RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }
        return configuration;
    }

    private static String boundSql(
            String statementId,
            Map<String, Object> parameters) throws Exception {
        MappedStatement statement = configuration().getMappedStatement(
                "me.nawa.appointment.mapper.AppointmentMapper." + statementId
        );
        BoundSql boundSql = statement.getBoundSql(parameters);
        return boundSql.getSql();
    }
}
