package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class PlaceMapperXmlTest {

    private static final String RESOURCE =
        "me/nawa/explore/mapper/PlaceMapper.xml";

    @Test
    void mapperXml_registersPlaceStatements() throws Exception {
        Configuration configuration = configuration();
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.PlaceMapper.searchPlaces"
        ));
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.PlaceMapper.countPlaces"
        ));
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.PlaceMapper.findPlaceDetail"
        ));
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.PlaceMapper.findPlaceActivities"
        ));
    }

    @Test
    void categoryFilters_joinTheSectorAndActivityConditionsWithOr() throws Exception {
        Configuration configuration = configuration();

        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setSectorIds(List.of(2L));
        request.setActivityIds(List.of(2L));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("memberId", null);

        MappedStatement statement = configuration.getMappedStatement(
            "me.nawa.explore.mapper.PlaceMapper.searchPlaces"
        );
        String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        assertTrue(sql.contains(") OR EXISTS ("));
    }

    @Test
    void categoryFilters_leaveNoDanglingOr_whenOnlyActivitiesAreGiven() throws Exception {
        Configuration configuration = configuration();

        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setActivityIds(List.of(9L));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("memberId", null);

        MappedStatement statement = configuration.getMappedStatement(
            "me.nawa.explore.mapper.PlaceMapper.searchPlaces"
        );
        String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        /* 대분류가 없으면 앞의 OR가 남아 문법이 깨진다. trim이 지워야 한다. */
        assertFalse(sql.contains("AND ( OR EXISTS"));
        assertTrue(sql.contains("filter_pa.activity_id IN"));
    }

    @Test
    void placeList_usesExistsForClassificationAndSavedFilters()
        throws Exception {
        Configuration configuration = configuration();
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setSectorIds(List.of(1L));
        request.setActivityIds(List.of(10L));
        request.setSavedOnly(true);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("memberId", 7L);
        MappedStatement statement = configuration.getMappedStatement(
            "me.nawa.explore.mapper.PlaceMapper.searchPlaces"
        );

        BoundSql boundSql = statement.getBoundSql(parameters);
        String sql = boundSql.getSql();

        assertTrue(sql.contains("FROM place_activity filter_pa"));
        assertTrue(sql.contains("FROM explore_item_likes saved_like"));
        assertTrue(sql.contains("p.is_active = TRUE"));
    }

    @Test
    void savedColumn_switchesOnMemberPresence() throws Exception {
        Configuration configuration = configuration();

        Map<String, Object> memberParameters = new HashMap<>();
        memberParameters.put("request", new PlaceSearchRequest());
        memberParameters.put("offset", 0);
        memberParameters.put("limit", 20);
        memberParameters.put("memberId", 7L);
        String memberSql = normalizedSql(
            configuration, "searchPlaces", memberParameters
        );
        assertTrue(memberSql.contains("FROM explore_item_likes saved_like"));
        assertTrue(memberSql.contains(") AS saved"));

        Map<String, Object> anonymousParameters = new HashMap<>();
        anonymousParameters.put("request", new PlaceSearchRequest());
        anonymousParameters.put("offset", 0);
        anonymousParameters.put("limit", 20);
        anonymousParameters.put("memberId", null);
        String anonymousSql = normalizedSql(
            configuration, "searchPlaces", anonymousParameters
        );
        assertTrue(anonymousSql.contains("FALSE AS saved"));

        Map<String, Object> detailParameters = new HashMap<>();
        detailParameters.put("placeId", 1L);
        detailParameters.put("memberId", 7L);
        String detailSql = normalizedSql(
            configuration, "findPlaceDetail", detailParameters
        );
        assertTrue(detailSql.contains(") AS saved"));
    }

    private String normalizedSql(
        Configuration configuration,
        String statementName,
        Map<String, Object> parameters
    ) {
        return configuration
            .getMappedStatement("me.nawa.explore.mapper.PlaceMapper." + statementName)
            .getBoundSql(parameters)
            .getSql()
            .replaceAll("\\s+", " ")
            .trim();
    }

    @Test
    void placeList_appliesOtherRegionOptions() throws Exception {
        Configuration configuration = configuration();
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setRegion1(List.of("서울"));
        request.setRegion2(List.of("성수"));
        request.setRegion2Other(true);
        request.setKnownRegion2Values(List.of("성수", "홍대"));
        request.setHasParking(true);
        request.setLanguage("en");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("memberId", null);

        BoundSql boundSql = configuration.getMappedStatement(
            "me.nawa.explore.mapper.PlaceMapper.searchPlaces"
        ).getBoundSql(parameters);
        String sql = boundSql.getSql();

        assertTrue(sql.contains("OR p.region2 IS NULL"));
        assertTrue(sql.contains("OR p.region2 NOT IN"));
        assertTrue(sql.contains("p.has_parking = TRUE"));
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                input, configuration, RESOURCE, configuration.getSqlFragments()
            ).parse();
        }
        return configuration;
    }
}
