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
            namespace + "findJourneyByIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(namespace + "updateJourney"));
        assertTrue(configuration.hasStatement(
            namespace + "hasJourneyItemsOutsideRange"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "softDeleteRegionsByTripId"
        ));
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
        assertTrue(configuration.hasStatement(
            namespace + "findJourneyItemForUpdate"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "findConfirmedJourneyItemsForUpdate"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "softDeleteJourneyItem"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "softDeleteJourneyItemsByTripId"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "softDeleteReportsByTripId"
        ));
        assertTrue(configuration.hasStatement(
            namespace + "softDeleteExpenseLinksByTripId"
        ));
        assertTrue(configuration.hasStatement(namespace + "softDeleteJourney"));

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
        assertTrue(duplicateSql.contains("deleted_at IS NULL"));

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

        MappedStatement conflictStatement = configuration
            .getMappedStatement(namespace + "hasJourneyItemsOutsideRange");
        String conflictSql = conflictStatement
            .getBoundSql(Map.of(
                "tripId", 1L,
                "startDate", java.time.LocalDate.of(2026, 8, 1),
                "endDate", java.time.LocalDate.of(2026, 8, 10)
            ))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(conflictSql.contains("deleted_at IS NULL"));
        assertTrue(conflictSql.contains("visit_date < ?"));
        assertTrue(conflictSql.contains("visit_date > ?"));

        MappedStatement lockedJourneyStatement = configuration
            .getMappedStatement(namespace + "findJourneyByIdForUpdate");
        String lockedJourneySql = lockedJourneyStatement
            .getBoundSql(Map.of("tripId", 1L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(lockedJourneySql.endsWith("FOR UPDATE"));

        MappedStatement regionsStatement = configuration
            .getMappedStatement(namespace + "insertRegions");
        String regionsSql = regionsStatement
            .getBoundSql(Map.of("regions", java.util.List.of(Map.of(
                "tripId", 1L,
                "regionCode", "SEOUL",
                "regionName", "Seoul",
                "displayOrder", 0
            ))))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(regionsSql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(regionsSql.contains("deleted_at = NULL"));

        MappedStatement lockedItemStatement = configuration
            .getMappedStatement(namespace + "findJourneyItemForUpdate");
        String lockedItemSql = lockedItemStatement
            .getBoundSql(Map.of(
                "tripId", 1L,
                "tripItemId", 2L,
                "memberId", 3L
            ))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(lockedItemSql.contains("a.host_member_id"));
        assertTrue(lockedItemSql.contains("am.membership_status"));
        assertTrue(lockedItemSql.contains("am.member_id = ?"));
        assertTrue(lockedItemSql.contains("ti.trip_id = ?"));
        assertTrue(lockedItemSql.endsWith("FOR UPDATE"));

        MappedStatement confirmedItemsStatement = configuration
            .getMappedStatement(
                namespace + "findConfirmedJourneyItemsForUpdate"
            );
        String confirmedItemsSql = confirmedItemsStatement
            .getBoundSql(Map.of("tripId", 1L, "memberId", 3L))
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(confirmedItemsSql.contains(
            "ti.trip_item_status = 'CONFIRMED'"
        ));
        assertTrue(confirmedItemsSql.contains("am.membership_status"));
        assertTrue(confirmedItemsSql.contains("am.member_id = ?"));
        assertTrue(confirmedItemsSql.endsWith("FOR UPDATE"));

        assertSoftDeleteSql(
            configuration,
            namespace + "softDeleteJourneyItem",
            Map.of("tripId", 1L, "tripItemId", 2L),
            "UPDATE trip_items"
        );
        assertSoftDeleteSql(
            configuration,
            namespace + "softDeleteJourneyItemsByTripId",
            Map.of("tripId", 1L),
            "UPDATE trip_items"
        );
        assertSoftDeleteSql(
            configuration,
            namespace + "softDeleteReportsByTripId",
            Map.of("tripId", 1L),
            "UPDATE reports"
        );
        assertSoftDeleteSql(
            configuration,
            namespace + "softDeleteExpenseLinksByTripId",
            Map.of("tripId", 1L),
            "UPDATE trip_expense_links"
        );
        assertSoftDeleteSql(
            configuration,
            namespace + "softDeleteJourney",
            Map.of("tripId", 1L),
            "UPDATE trips"
        );
    }

    private static void assertSoftDeleteSql(
        Configuration configuration,
        String statementId,
        Map<String, Long> parameters,
        String updateClause
    ) {
        String sql = configuration.getMappedStatement(statementId)
            .getBoundSql(parameters)
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
        assertTrue(sql.startsWith(updateClause));
        assertTrue(sql.contains("deleted_at = CURRENT_TIMESTAMP"));
        assertTrue(sql.contains("deleted_at IS NULL"));
    }
}
