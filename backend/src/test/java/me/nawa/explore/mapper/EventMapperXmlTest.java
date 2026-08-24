package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.nawa.explore.dto.request.EventSearchRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
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
        assertTrue(configuration.hasStatement(
            "me.nawa.explore.mapper.EventMapper.realignEventStatuses"
        ));
        assertTrue(configuration.hasResultMap(
            "me.nawa.explore.mapper.EventMapper.eventDetailResultMap"
        ));
    }

    // 저장 status 정정은 조회와 같은 식을 써야 한다. 두 곳이 갈라지면 화면과 DB가
    // 다른 말을 하는데, 어느 쪽이 틀렸는지 화면만 보고는 알 수 없다.
    @Test
    void realignEventStatuses_usesSameDerivationAndSkipsMatchingRows()
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

        String sql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.realignEventStatuses",
            Map.of("today", LocalDate.of(2026, 8, 20))
        );

        assertFalse(sql.contains("CURRENT_DATE()"));
        assertTrue(sql.contains("THEN 'SCHEDULED'"));
        assertTrue(sql.contains("THEN 'ENDED'"));
        assertTrue(sql.contains("ELSE 'ONGOING'"));
        // 이미 맞는 행까지 SET하면 event 전체가 매번 쓰기 대상이 된다.
        assertTrue(sql.contains("e.status <>"));
        assertTrue(sql.contains("e.deleted_at IS NULL"));
    }

    @Test
    void categoryFilters_joinTheSectorAndActivityConditionsWithOr() throws Exception {
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
        request.setSectorIds(List.of(2L));
        request.setActivityIds(List.of(2L));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);

        MappedStatement statement = configuration.getMappedStatement(
            "me.nawa.explore.mapper.EventMapper.searchEvents"
        );
        String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        /*
         * AND로 묶으면 한 대분류를 통째로 고르고 다른 대분류를 일부만 고른 조합에서
         * 교집합만 남아 결과가 거의 0건이 된다.
         */
        assertTrue(sql.contains(") OR EXISTS ("));
    }

    @Test
    void categoryFilters_leaveNoDanglingOr_whenOnlyActivitiesAreGiven() throws Exception {
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
        request.setActivityIds(List.of(9L));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);

        MappedStatement statement = configuration.getMappedStatement(
            "me.nawa.explore.mapper.EventMapper.searchEvents"
        );
        String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        /* 대분류가 없으면 앞의 OR가 남아 문법이 깨진다. trim이 지워야 한다. */
        assertFalse(sql.contains("AND ( OR EXISTS"));
        assertTrue(sql.contains("filter_ea.activity_id IN"));
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
    void savedColumn_switchesOnMemberPresence() throws Exception {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                input,
                configuration,
                MAPPER_RESOURCE,
                configuration.getSqlFragments()
            ).parse();
        }

        Map<String, Object> memberParameters = new HashMap<>();
        memberParameters.put("request", new EventSearchRequest());
        memberParameters.put("offset", 0);
        memberParameters.put("memberId", 7L);
        String memberSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            memberParameters
        );
        assertTrue(memberSql.contains("FROM explore_item_likes saved_like"));
        assertTrue(memberSql.contains(") AS saved"));

        Map<String, Object> anonymousParameters = new HashMap<>();
        anonymousParameters.put("request", new EventSearchRequest());
        anonymousParameters.put("offset", 0);
        anonymousParameters.put("memberId", null);
        String anonymousSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            anonymousParameters
        );
        assertTrue(anonymousSql.contains("FALSE AS saved"));

        Map<String, Object> detailParameters = new HashMap<>();
        detailParameters.put("eventId", 1L);
        detailParameters.put("language", "en");
        detailParameters.put("memberId", 7L);
        String detailSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.findEventDetail",
            detailParameters
        );
        assertTrue(detailSql.contains(") AS saved"));
    }

    @Test
    void eventVisibilityAndStatus_useSuppliedDateInsteadOfStoredStatus()
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

        // 날짜 프리셋 분기는 제거됐다(#275). 프론트가 환원해 보낸 날짜 범위가
        // 파라미터 조건으로 들어가고, 저장된 status 컬럼은 여전히 쓰지 않는다.
        EventSearchRequest oneDayRequest = new EventSearchRequest();
        oneDayRequest.setStartDate(LocalDate.of(2026, 8, 20));
        oneDayRequest.setEndDate(LocalDate.of(2026, 8, 20));
        Map<String, Object> oneDayParameters = new HashMap<>();
        oneDayParameters.put("request", oneDayRequest);
        oneDayParameters.put("offset", 0);
        String oneDaySql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            oneDayParameters
        );

        // 노출 조건과 status 파생이 모두 애플리케이션이 넘긴 기준일을 본다.
        // DB 세션 시간대에 기대는 CURRENT_DATE()는 어느 쪽에도 남지 않는다.
        assertFalse(oneDaySql.contains("CURRENT_DATE()"));
        assertTrue(oneDaySql.contains("(e.end_date IS NULL OR e.end_date >= ?)"));
        assertTrue(oneDaySql.contains("e.start_date <= ?"));
        assertFalse(oneDaySql.contains("datePreset"));
        // 저장 status 컬럼은 읽지도 내보내지도 않는다.
        assertFalse(oneDaySql.contains("e.status"));
        assertTrue(oneDaySql.contains("END AS status"));
        assertTrue(oneDaySql.contains("THEN 'SCHEDULED'"));
        assertTrue(oneDaySql.contains("THEN 'ENDED'"));
        assertTrue(oneDaySql.contains("ELSE 'ONGOING'"));

        EventSearchRequest openEndedRequest = new EventSearchRequest();
        openEndedRequest.setStartDate(LocalDate.of(2026, 8, 21));
        Map<String, Object> openEndedParameters = new HashMap<>();
        openEndedParameters.put("request", openEndedRequest);
        openEndedParameters.put("offset", 0);
        String openEndedSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            openEndedParameters
        );

        assertTrue(openEndedSql.contains("(e.end_date IS NULL OR e.end_date >= ?)"));
        assertFalse(openEndedSql.contains("e.start_date <= ?"));
        assertFalse(openEndedSql.contains("e.status"));

        EventSearchRequest otherRegionRequest = new EventSearchRequest();
        otherRegionRequest.setRegion1(List.of("서울"));
        otherRegionRequest.setRegion2Other(true);
        otherRegionRequest.setKnownRegion2Values(List.of("성수", "홍대"));
        Map<String, Object> otherRegionParameters = new HashMap<>();
        otherRegionParameters.put("request", otherRegionRequest);
        otherRegionParameters.put("offset", 0);
        String otherRegionSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            otherRegionParameters
        );
        assertTrue(otherRegionSql.contains("OR e.region2 NOT IN"));

        String detailSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.findEventDetail",
            Map.of("eventId", 1L, "language", "en")
        );
        assertFalse(detailSql.contains("CURRENT_DATE()"));
        assertTrue(detailSql.contains("(e.end_date IS NULL OR e.end_date >= ?)"));
        assertFalse(detailSql.contains("e.status"));
        assertTrue(detailSql.contains("END AS status"));
    }

    /**
     * 목록·개수·상세가 모두 번역 테이블을 조인해야 한다.
     *
     * <p>이 조인이 없어서 어떤 언어로 요청해도 한국어 원문만 나갔다(#531). LEFT JOIN이라
     * 빠져 있어도 쿼리는 성공하고 오류도 남지 않아, 화면을 열어 보기 전까지 드러나지 않는다.
     */
    @Test
    void eventStatements_joinTranslationsForRequestedLanguage() throws Exception {
        Configuration configuration = parsedConfiguration();

        Map<String, Object> listParameters = new HashMap<>();
        listParameters.put("request", new EventSearchRequest());
        listParameters.put("offset", 0);
        listParameters.put("today", LocalDate.of(2026, 8, 25));

        for (String statementId : List.of(
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            "me.nawa.explore.mapper.EventMapper.countEvents"
        )) {
            String sql = normalizedSql(configuration, statementId, listParameters);

            assertTrue(
                sql.contains("LEFT JOIN event_translations et"),
                statementId + "가 번역 테이블을 조인하지 않는다"
            );
            assertTrue(sql.contains("et.deleted_at IS NULL"));
            // 언어는 request 안에 있다. 경로를 잘못 적으면 NULL로 조인돼 조용히 원문만 나간다.
            assertTrue(
                boundProperties(configuration, statementId, listParameters)
                    .contains("request.language"),
                statementId + "가 request.language를 바인딩하지 않는다"
            );
        }

        String detailSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.findEventDetail",
            Map.of("eventId", 1L, "language", "zh-TW", "today", LocalDate.of(2026, 8, 25))
        );
        assertTrue(detailSql.contains("LEFT JOIN event_translations et"));
        assertTrue(
            boundProperties(
                configuration,
                "me.nawa.explore.mapper.EventMapper.findEventDetail",
                Map.of("eventId", 1L, "language", "zh-TW", "today", LocalDate.of(2026, 8, 25))
            ).contains("language")
        );
    }

    /** 번역이 없거나 빈 문자열이면 한국어 원문으로 돌아가야 한다. */
    @Test
    void eventStatements_fallBackToKoreanColumns() throws Exception {
        Configuration configuration = parsedConfiguration();

        Map<String, Object> listParameters = new HashMap<>();
        listParameters.put("request", new EventSearchRequest());
        listParameters.put("offset", 0);
        listParameters.put("today", LocalDate.of(2026, 8, 25));

        String listSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            listParameters
        );
        assertTrue(listSql.contains(
            "COALESCE(NULLIF(TRIM(et.title), ''), e.title) AS title"
        ));

        String detailSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.findEventDetail",
            Map.of("eventId", 1L, "language", "en", "today", LocalDate.of(2026, 8, 25))
        );
        assertTrue(detailSql.contains(
            "COALESCE(NULLIF(TRIM(et.title), ''), e.title) AS title"
        ));
        assertTrue(detailSql.contains(
            "COALESCE(NULLIF(TRIM(et.venue_detail), ''), e.venue_name) AS venue_name"
        ));
        assertTrue(detailSql.contains(
            "COALESCE(NULLIF(TRIM(et.address_display), ''), e.address_road) AS address_road"
        ));
        /*
         * 번역 쪽 operating_hours는 TEXT고 응답 DTO는 JSON이다. 그냥 COALESCE하면
         * JsonNodeTypeHandler가 파싱에 실패해 상세 API가 통째로 500이 된다.
         */
        assertTrue(detailSql.contains("JSON_OBJECT('raw', et.operating_hours)"));
    }

    /**
     * 화면에 영어 제목이 보이는데 그 영어 제목으로 검색하면 0건이 나오는 상태를 막는다.
     * 한국어 원문 조건도 함께 남겨, 번역이 붙은 Event를 한국어로 찾던 경로가 죽지 않게 한다.
     */
    @Test
    void keywordSearch_matchesTranslatedTitleAndKoreanOriginal() throws Exception {
        Configuration configuration = parsedConfiguration();

        EventSearchRequest request = new EventSearchRequest();
        request.setKeyword("Lantern");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", request);
        parameters.put("offset", 0);
        parameters.put("today", LocalDate.of(2026, 8, 25));

        String listSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.searchEvents",
            parameters
        );
        String countSql = normalizedSql(
            configuration,
            "me.nawa.explore.mapper.EventMapper.countEvents",
            parameters
        );

        assertTrue(listSql.contains("COALESCE(NULLIF(TRIM(et.title), ''), e.title) LIKE"));
        assertTrue(listSql.contains("e.title LIKE"));
        /*
         * 목록만 번역 제목으로 찾고 개수는 원문만 세면 totalElements가 어긋나
         * 페이지네이션이 틀어진다. 두 구문이 같은 조건을 봐야 한다.
         */
        assertTrue(countSql.contains("COALESCE(NULLIF(TRIM(et.title), ''), e.title) LIKE"));
        assertTrue(countSql.contains("LEFT JOIN event_translations et"));
    }

    private static Configuration parsedConfiguration() throws Exception {
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

    private static List<String> boundProperties(
        Configuration configuration,
        String statementId,
        Map<String, Object> parameters
    ) {
        return configuration
            .getMappedStatement(statementId)
            .getBoundSql(parameters)
            .getParameterMappings()
            .stream()
            .map(ParameterMapping::getProperty)
            .toList();
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
