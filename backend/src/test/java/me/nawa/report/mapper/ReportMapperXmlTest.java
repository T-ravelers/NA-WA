package me.nawa.report.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ReportMapperXmlTest {

    private static final String MAPPER_RESOURCE =
        "me/nawa/report/mapper/ReportMapper.xml";

    @Test
    void mapperXml_registersReportStatementsAndContracts() throws Exception {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(
            MAPPER_RESOURCE
        )) {
            new XMLMapperBuilder(
                input,
                configuration,
                MAPPER_RESOURCE,
                configuration.getSqlFragments()
            ).parse();
        }

        String namespace = "me.nawa.report.mapper.ReportMapper.";
        assertTrue(configuration.hasStatement(namespace + "findJourneyForUpdate"));
        assertTrue(configuration.hasStatement(namespace + "findActiveReportByTripId"));
        assertTrue(configuration.hasStatement(namespace + "findTimelineItemsByTripId"));
        assertTrue(configuration.hasStatement(namespace + "insertReport"));
        assertTrue(configuration.hasStatement(namespace + "findReportById"));
        assertTrue(configuration.hasStatement(namespace + "findReportsByMemberId"));

        MappedStatement lockStatement = configuration.getMappedStatement(
            namespace + "findJourneyForUpdate"
        );
        String lockSql = lockStatement
            .getBoundSql(Map.of("tripId", 1L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(lockSql.contains("FROM trips"));
        assertTrue(lockSql.contains("deleted_at IS NULL"));
        assertTrue(lockSql.endsWith("FOR UPDATE"));

        MappedStatement timelineStatement = configuration.getMappedStatement(
            namespace + "findTimelineItemsByTripId"
        );
        String timelineSql = timelineStatement
            .getBoundSql(Map.of("tripId", 1L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(timelineSql.contains("FROM trip_items ti"));
        assertTrue(timelineSql.contains("JOIN explore_items ei"));
        assertTrue(timelineSql.contains("LEFT JOIN event e"));
        assertTrue(timelineSql.contains("LEFT JOIN place p"));
        assertTrue(timelineSql.contains("ti.deleted_at IS NULL"));
        assertTrue(timelineSql.contains("ei.deleted_at IS NULL"));
        assertTrue(timelineSql.contains(
            "ORDER BY ti.visit_date ASC, ti.display_order ASC, "
                + "ti.trip_item_id ASC"
        ));

        MappedStatement listStatement = configuration.getMappedStatement(
            namespace + "findReportsByMemberId"
        );
        String listSql = listStatement
            .getBoundSql(Map.of("memberId", 1L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(listSql.contains("r.report_content") == false);
        assertTrue(listSql.contains("ORDER BY r.created_at DESC, r.report_id DESC"));
    }
}
