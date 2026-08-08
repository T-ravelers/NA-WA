package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class JourneyMapperXmlTest {

    private static final String MAPPER_RESOURCE =
        "me/nawa/journey/mapper/JourneyMapper.xml";

    @Test
    void mapperXml_registersJourneyStatements() throws Exception {
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

        String namespace = "me.nawa.journey.mapper.JourneyMapper.";
        assertTrue(configuration.hasStatement(namespace + "insertJourney"));
        assertTrue(configuration.hasStatement(namespace + "insertRegions"));
        assertTrue(configuration.hasStatement(
            namespace + "findJourneysByMemberId"
        ));
        assertTrue(configuration.hasStatement(namespace + "findJourneyById"));
        assertTrue(configuration.hasStatement(
            namespace + "findRegionsByTripId"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "findTimelineItemsByTripId"
        ));

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
        assertTrue(timelineSql.contains("LEFT JOIN appointments a"));
        assertTrue(timelineSql.contains("ti.deleted_at IS NULL"));
        assertTrue(timelineSql.contains("ei.deleted_at IS NULL"));
        assertTrue(timelineSql.contains(
            "ORDER BY ti.visit_date ASC, ti.display_order ASC, "
                + "ti.trip_item_id ASC"
        ));
    }
}
