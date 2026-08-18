package me.nawa.appointment.mapper;

import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
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
                "me.nawa.appointment.mapper.AppointmentMapper.closeExpiredRecruitingAppointments"
        ));
        assertTrue(configuration.hasStatement(
                "me.nawa.appointment.mapper.AppointmentMapper.startDueClosedAppointments"
        ));
    }

    @Test
    void closeExpiredRecruitingAppointments_onlyTargetsExpiredRecruiting()
            throws Exception {
        String sql = boundSql(
                "closeExpiredRecruitingAppointments", Map.of()
        );

        assertTrue(sql.contains("appointment_status = 'CLOSED'"));
        assertTrue(sql.contains("appointment_status = 'RECRUITING'"));
        assertTrue(sql.contains("join_deadline <"));
    }

    @Test
    void startDueClosedAppointments_onlyTargetsClosedPastActivityStart()
            throws Exception {
        String sql = boundSql(
                "startDueClosedAppointments", Map.of()
        );

        assertTrue(sql.contains("appointment_status = 'IN_PROGRESS'"));
        assertTrue(sql.contains("appointment_status = 'CLOSED'"));
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
        assertTrue(sql.contains("a.appointment_status = 'IN_PROGRESS'"));
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
