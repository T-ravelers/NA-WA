package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.nawa.explore.dto.request.EventSearchRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class EventMapperXmlTest {

    private static final String MAPPER_RESOURCE =
        "me/nawa/explore/mapper/EventMapper.xml";

    @Test
    void mapperXml_registersEventListStatements() throws Exception {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                input,
                configuration,
                MAPPER_RESOURCE,
                configuration.getSqlFragments()
            ).parse();
        }

        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.EventMapper.searchEvents"
        ));
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.EventMapper.countEvents"
        ));
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.EventMapper.findEventDetail"
        ));
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.EventMapper.findEventActivities"
        ));
        assertTrue(configuration.hasResultMap(
            "me.nawa.explore.mapper.EventMapper.eventDetailResultMap"
        ));
    }

    @Test
    void eventListOtherAreas_usesUnclassifiedAreaCondition() throws Exception {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                input,
                configuration,
                MAPPER_RESOURCE,
                configuration.getSqlFragments()
            ).parse();
        }

        EventSearchRequest request = new EventSearchRequest();
        request.setRegion1(List.of("Gyeonggi"));
        request.setRegion2Other(true);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);

        MappedStatement statement = configuration.getMappedStatement(
            "me.nawa.explore.mapper.EventMapper.searchEvents"
        );
        BoundSql boundSql = statement.getBoundSql(parameters);
        String sql = boundSql.getSql();

        assertTrue(sql.contains("e.region2 IS NULL"));
        assertTrue(sql.contains("TRIM(e.region2) = ''"));
    }

    @Test
    void eventVisibility_usesCurrentDatesInsteadOfStoredStatus()
        throws Exception {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                input,
                configuration,
                MAPPER_RESOURCE,
                configuration.getSqlFragments()
            ).parse();
        }

        EventSearchRequest ongoingRequest = new EventSearchRequest();
        ongoingRequest.setDatePreset("ONGOING");
        Map<String, Object> ongoingParameters = new HashMap<>();
        ongoingParameters.put("request", ongoingRequest);
        ongoingParameters.put("offset", 0);
        String ongoingSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            ongoingParameters
        );

        assertTrue(ongoingSql.contains(
            "(e.end_date IS NULL OR e.end_date >= CURRENT_DATE())"
        ));
        assertTrue(ongoingSql.contains(
            "e.start_date <= CURRENT_DATE()"
        ));
        assertFalse(ongoingSql.contains("e.status = 'ONGOING'"));
        assertFalse(ongoingSql.contains(
            "e.status IN ('SCHEDULED', 'ONGOING')"
        ));

        EventSearchRequest openingSoonRequest = new EventSearchRequest();
        openingSoonRequest.setDatePreset("OPENING_SOON");
        Map<String, Object> openingSoonParameters = new HashMap<>();
        openingSoonParameters.put("request", openingSoonRequest);
        openingSoonParameters.put("offset", 0);
        String openingSoonSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            openingSoonParameters
        );

        assertTrue(openingSoonSql.contains(
            "e.start_date > CURRENT_DATE()"
        ));
        assertFalse(openingSoonSql.contains("e.status = 'SCHEDULED'"));

        String detailSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.findEventDetail",
            Map.of("eventId", 1L, "language", "en")
        );
        assertTrue(detailSql.contains(
            "(e.end_date IS NULL OR e.end_date >= CURRENT_DATE())"
        ));
        assertFalse(detailSql.contains(
            "e.status IN ('SCHEDULED', 'ONGOING')"
        ));
    }

    private static String normalizedSql(
        Configuration configuration,
        String statementId,
        Map<String, Object> parameters
    ) {
        return configuration
            .getMappedStatement(statementId)
            .getBoundSql(parameters)
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
    }
}
