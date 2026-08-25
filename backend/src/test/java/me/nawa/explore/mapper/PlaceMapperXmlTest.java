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
import org.apache.ibatis.mapping.ParameterMapping;
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

    /**
     * 목록·개수·상세가 모두 번역 테이블을 조인해야 한다.
     *
     * <p>이 조인이 없어서 어떤 언어로 요청해도 한국어 원문만 나갔다(#531). LEFT JOIN이라
     * 빠져 있어도 쿼리는 성공하고 오류도 남지 않아 조용히 회귀한다.
     */
    @Test
    void placeStatements_joinTranslationsForRequestedLanguage() throws Exception {
        Configuration configuration = configuration();

        Map<String, Object> listParameters = new HashMap<>();
        listParameters.put("request", new PlaceSearchRequest());
        listParameters.put("offset", 0);
        listParameters.put("limit", 20);

        for (String statementId : List.of(
            "searchPlaces",
            "countPlaces"
        )) {
            String sql = normalizedSql(configuration, statementId, listParameters);

            assertTrue(
                sql.contains("LEFT JOIN place_translations pt"),
                statementId + "가 번역 테이블을 조인하지 않는다"
            );
            assertTrue(sql.contains("pt.deleted_at IS NULL"));
            assertTrue(
                boundProperties(configuration, statementId, listParameters)
                    .contains("request.language"),
                statementId + "가 request.language를 바인딩하지 않는다"
            );
        }

        Map<String, Object> detailParameters = Map.of(
            "placeId", 1L, "language", "zh-TW"
        );
        String detailSql = normalizedSql(configuration, "findPlaceDetail", detailParameters);
        assertTrue(detailSql.contains("LEFT JOIN place_translations pt"));
        assertTrue(
            boundProperties(configuration, "findPlaceDetail", detailParameters)
                .contains("language")
        );
    }

    /**
     * 번역이 없거나 빈 문자열이면 영어로, 영어도 없으면 한국어 원문으로 돌아가야 한다.
     *
     * <p>영어 폴백은 Journey 타임라인(#536)과 맞춘 것이다.
     */
    @Test
    void placeStatements_fallBackToEnglishThenKoreanColumns() throws Exception {
        Configuration configuration = configuration();

        String detailSql = normalizedSql(
            configuration, "findPlaceDetail", Map.of("placeId", 1L, "language", "ja")
        );

        assertTrue(detailSql.contains(
            "COALESCE(NULLIF(TRIM(pt.name), ''), NULLIF(TRIM(pt_en.name), ''), p.name) AS name"
        ));
        assertTrue(detailSql.contains(
            "COALESCE(NULLIF(TRIM(pt.address_display), ''), NULLIF(TRIM(pt_en.address_display), ''), "
                + "p.address_road) AS address_road"
        ));
        assertTrue(detailSql.contains(
            "COALESCE(NULLIF(TRIM(pt.menu_summary), ''), NULLIF(TRIM(pt_en.menu_summary), ''), "
                + "p.menu_summary) AS menu_summary"
        ));
        // 영어 조인은 항상 'en' 고정이지, 요청 언어 파라미터를 재사용하지 않는다.
        assertTrue(detailSql.contains("pt_en.language_code = 'en'"));
        /*
         * 번역 쪽 영업시간·휴무일은 TEXT고 응답 DTO는 JSON이다. 그냥 COALESCE하면
         * JsonNodeTypeHandler가 파싱에 실패해 상세 API가 통째로 500이 된다.
         *
         * 감싸는 모양은 원문을 따른다 — 영업시간은 OBJECT, 휴무일은 ARRAY다. 번역이 붙은
         * 항목과 붙지 않은 항목이 같은 모양으로 나가야 클라이언트가 한 가지 형태만 다루면
         * 된다. 화면은 두 모양을 같게 그리므로(#534) 이 단정은 표시 결과가 아니라 응답
         * 형태의 일관성을 고정한다.
         */
        assertTrue(detailSql.contains("JSON_OBJECT('raw', pt.opening_hours_text)"));
        assertTrue(detailSql.contains("JSON_OBJECT('raw', pt_en.opening_hours_text)"));
        assertTrue(
            detailSql.contains("JSON_ARRAY(pt.closed_days_text)"),
            "휴무일 원문이 ARRAY이므로 번역도 배열이어야 응답 형태가 갈리지 않는다"
        );
        assertTrue(detailSql.contains("JSON_ARRAY(pt_en.closed_days_text)"));
        assertFalse(
            detailSql.contains("JSON_OBJECT('raw', pt.closed_days_text)"),
            "휴무일을 객체로 감싸면 번역 여부에 따라 응답 형태가 달라진다"
        );
    }

    /** 화면에 번역 이름이 보이는데 그 이름으로는 검색되지 않는 상태를 막는다. */
    @Test
    void keywordSearch_matchesTranslatedNameAndKoreanOriginal() throws Exception {
        Configuration configuration = configuration();

        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setKeyword("Coffee");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);
        parameters.put("limit", 20);

        String listSql = normalizedSql(configuration, "searchPlaces", parameters);
        String countSql = normalizedSql(configuration, "countPlaces", parameters);

        // 표시값과 달리 COALESCE로 감싸지 않는다 — 폴백 갈래를 원문 조건이 덮는다(#531 리뷰).
        assertTrue(listSql.contains("NULLIF(TRIM(pt.name), '') LIKE"));
        // 요청 언어 번역이 없어 영어 이름이 보이는 Place도 그 영어 이름으로 찾아야 한다(#536).
        assertTrue(listSql.contains("NULLIF(TRIM(pt_en.name), '') LIKE"));
        assertTrue(listSql.contains("p.name LIKE"));
        // 목록과 개수가 다른 조건을 보면 totalElements가 어긋나 페이지네이션이 틀어진다.
        assertTrue(countSql.contains("NULLIF(TRIM(pt.name), '') LIKE"));
        assertTrue(countSql.contains("NULLIF(TRIM(pt_en.name), '') LIKE"));
        assertTrue(countSql.contains("LEFT JOIN place_translations pt"));
    }

    private static List<String> boundProperties(
        Configuration configuration,
        String statementName,
        Map<String, Object> parameters
    ) {
        return configuration
            .getMappedStatement("me.nawa.explore.mapper.PlaceMapper." + statementName)
            .getBoundSql(parameters)
            .getParameterMappings()
            .stream()
            .map(ParameterMapping::getProperty)
            .toList();
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
