package me.nawa.explore.mapper;

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
