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
