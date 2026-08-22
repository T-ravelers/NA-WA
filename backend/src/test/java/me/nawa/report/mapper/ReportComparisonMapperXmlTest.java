package me.nawa.report.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** 비교 문장(#398)이 등록되고, 설계가 기대는 조건이 SQL에 실제로 들어 있는지 본다. */
class ReportComparisonMapperXmlTest {

    private static final String MAPPER_RESOURCE =
        "me/nawa/report/mapper/ReportMapper.xml";
    private static final String NAMESPACE = "me.nawa.report.mapper.ReportMapper.";

    @Test
    void mapperXml_registersComparisonStatementsWithTheirContracts() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()
            ).parse();
        }

        String memberSql = boundSql(configuration, "findComparisonMember", Map.of("memberId", 1L));
        assertTrue(memberSql.contains("nationality_code"));
        assertTrue(memberSql.contains("deleted_at IS NULL"));

        // 방장은 trip_items(CONFIRMED)로, 참가자는 활동일이 여정 기간에 드는지로 잇는다.
        // 동료는 ACTIVE 참가자만 세고, 취소된 약속은 통째로 뺀다.
        String peersSql = boundSql(
            configuration, "findComparisonPeerMembers",
            Map.of("tripId", 7L, "memberId", 1L,
                "startDate", LocalDate.of(2026, 8, 1), "endDate", LocalDate.of(2026, 8, 5))
        );
        assertTrue(peersSql.contains("trip_item_status = 'CONFIRMED'"));
        assertTrue(peersSql.contains("DATE(a3.activity_start_at) BETWEEN ? AND ?"));
        assertTrue(peersSql.contains("am3.membership_status = 'ACTIVE'"));
        assertTrue(peersSql.contains("a.appointment_status != 'CANCELLED'"));
        assertTrue(peersSql.contains("am2.membership_status = 'ACTIVE'"));
        assertTrue(peersSql.contains("am2.member_id != ?"));

        // 나와 동료가 같은 정의로 합산되고, 여정 링크 조건은 들어가지 않는다.
        String spendingSql = boundSql(
            configuration, "findComparisonSpending",
            Map.of("memberIds", List.of(1L, 2L),
                "startDate", LocalDate.of(2026, 8, 1), "endDate", LocalDate.of(2026, 8, 5))
        );
        assertTrue(spendingSql.contains("t.initiator_member_id IN ( ? , ? )"));
        assertTrue(spendingSql.contains("transfer_type IN ('QR_PAYMENT', 'SETTLEMENT')"));
        assertTrue(spendingSql.contains("wo.member_id = t.initiator_member_id"));
        assertTrue(spendingSql.contains("GROUP BY t.initiator_member_id"));
        assertTrue(!spendingSql.contains("trip_expense_links"));

        // 회원마다 최신 리포트 하나, analytics만 꺼낸다.
        String cohortSql = boundSql(
            configuration, "findSimilarCohortAnalytics",
            Map.of("nationalityCode", "KR", "memberId", 1L, "limit", 200)
        );
        assertTrue(cohortSql.contains("ROW_NUMBER() OVER"));
        assertTrue(cohortSql.contains("JSON_EXTRACT(r.report_content, '$.analytics')"));
        assertTrue(cohortSql.contains("r.generation_status = 'COMPLETED'"));
        assertTrue(cohortSql.contains("m.account_type = 'TRAVELER'"));
        assertTrue(cohortSql.contains("ranked.rn = 1"));
    }

    private static String boundSql(
        Configuration configuration, String statement, Map<String, Object> parameters
    ) {
        return configuration.getMappedStatement(NAMESPACE + statement)
            .getBoundSql(parameters)
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
    }
}
