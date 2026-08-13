package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(configuration.hasStatement(
            namespace + "findAvailableExploreItemById"
        ));
        assertTrue(configuration.hasStatement(namespace + "existsJourneyItem"));
        assertTrue(configuration.hasStatement(namespace + "insertJourneyItem"));
        assertTrue(configuration.hasStatement(
            namespace + "findJourneyItemById"
        ));

        MappedStatement journeysStatement = configuration.getMappedStatement(
            namespace + "findJourneysByMemberId"
        );
        String journeysSql = journeysStatement
            .getBoundSql(Map.of("memberId", 1L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();

        assertTrue(journeysSql.contains("AS event_count"));
        assertTrue(journeysSql.contains("AS place_count"));
        assertTrue(journeysSql.contains("JOIN event e"));
        assertTrue(journeysSql.contains("JOIN place p"));
        assertTrue(journeysSql.contains("ei.item_type = 'EVENT'"));
        assertTrue(journeysSql.contains("ei.item_type = 'PLACE'"));
        assertTrue(journeysSql.contains("ti.trip_id = trips.trip_id"));
        assertTrue(journeysSql.contains("ti.deleted_at IS NULL"));
        assertTrue(journeysSql.contains("ei.deleted_at IS NULL"));
        assertTrue(journeysSql.contains("e.deleted_at IS NULL"));
        assertTrue(journeysSql.contains("p.deleted_at IS NULL"));

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

        MappedStatement availableItemStatement = configuration
            .getMappedStatement(namespace + "findAvailableExploreItemById");
        String availableItemSql = availableItemStatement
            .getBoundSql(Map.of("itemId", 1L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(availableItemSql.contains("ei.approval_status = 'APPROVED'"));
        assertTrue(availableItemSql.contains("ei.visibility_status = 'VISIBLE'"));
        assertTrue(availableItemSql.contains(
            "(e.end_date IS NULL OR e.end_date >= CURRENT_DATE())"
        ));
        assertFalse(availableItemSql.contains(
            "e.status IN ('SCHEDULED', 'ONGOING')"
        ));
        assertTrue(availableItemSql.contains("p.is_active = TRUE"));
        assertTrue(availableItemSql.contains("e.deleted_at IS NULL"));
        assertTrue(availableItemSql.contains("p.deleted_at IS NULL"));

        MappedStatement duplicateStatement = configuration
            .getMappedStatement(namespace + "existsJourneyItem");
        String duplicateSql = duplicateStatement
            .getBoundSql(Map.of(
                "tripId", 1L,
                "itemId", 2L,
                "visitDate", java.time.LocalDate.of(2026, 8, 8)
            ))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(duplicateSql.contains("FROM trip_items"));
        assertFalse(duplicateSql.contains("deleted_at IS NULL"));

        MappedStatement insertStatement = configuration
            .getMappedStatement(namespace + "insertJourneyItem");
        String insertSql = insertStatement
            .getBoundSql(Map.of(
                "tripId", 1L,
                "itemId", 2L,
                "visitDate", java.time.LocalDate.of(2026, 8, 8),
                "displayOrder", 0,
                "note", "note"
            ))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(insertSql.contains("INSERT INTO trip_items"));
        assertTrue(insertSql.contains("'ADDED'"));
        assertTrue(insertSql.contains("NULL"));
    }
}
