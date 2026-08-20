package me.nawa.ingest.mapper;

import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 적재 SQL 이 의도한 모양으로 만들어지는지 봅니다.
 *
 * <p>DB 없이 확인합니다. 여기서 걸러야 할 사고는 두 가지입니다. 앱이 소유하는
 * 값을 파이프라인이 덮어쓰는 것, 그리고 목록이 늘어나도 문장이 한 번만 나가는지.
 */
class IngestMapperXmlTest {

    private static final String MAPPER_RESOURCE =
            "me/nawa/ingest/mapper/IngestMapper.xml";
    private static final String NS = "me.nawa.ingest.mapper.IngestMapper.";

    @Test
    void mapperXml_registersAllIngestStatements() throws Exception {
        Configuration configuration = configuration();

        for (String id : List.of(
                "findExistingEventPipelineIds",
                "findExistingPlacePipelineIds",
                "findMaxExploreItemId",
                "insertExploreItems",
                "insertEvents",
                "updateEvents",
                "insertPlaces",
                "updatePlaces",
                "upsertEventTranslations",
                "upsertPlaceTranslations")) {
            assertTrue(configuration.hasStatement(NS + id), id + " 문장이 없습니다");
        }
    }

    @Test
    void updateEvents_leavesAppOwnedColumnsAlone() throws Exception {
        String sql = sqlOf("updateEvents", Map.of("items", List.of(event("a"), event("b"))));

        // 앱이 세는 값이다. 파이프라인이 건드리면 사용자 활동 기록이 지워진다.
        assertFalse(sql.contains("view_count"), "view_count 를 갱신하고 있습니다");
        assertFalse(sql.contains("favorite_count"), "favorite_count 를 갱신하고 있습니다");
        assertFalse(sql.contains("created_at"), "created_at 을 갱신하고 있습니다");
    }

    @Test
    void updatePlaces_leavesAppOwnedColumnsAlone() throws Exception {
        String sql = sqlOf("updatePlaces", Map.of("items", List.of(place("a"))));

        assertFalse(sql.contains("view_count"), "view_count 를 갱신하고 있습니다");
        assertFalse(sql.contains("favorite_count"), "favorite_count 를 갱신하고 있습니다");
        assertFalse(sql.contains("created_at"), "created_at 을 갱신하고 있습니다");
    }

    @Test
    void updateEvents_buildsOneStatementForTheWholeBatch() throws Exception {
        String sql = sqlOf("updateEvents", Map.of("items", List.of(event("a"), event("b"), event("c"))));

        // 건마다 문장을 날리면 왕복이 늘고 트랜잭션이 길어진다.
        assertEquals(1, countOccurrences(sql, "UPDATE event e"), "UPDATE 가 여러 번 나갑니다");
        assertEquals(2, countOccurrences(sql, "UNION ALL"), "행 수만큼 UNION ALL 이 있어야 합니다");
    }

    @Test
    void upsertEventTranslations_onlyTouchesRowsWithABody() throws Exception {
        EventTranslationIngestItem item = new EventTranslationIngestItem();
        item.setPipelineId("a");
        item.setLanguageCode("en");

        String sql = sqlOf("upsertEventTranslations", Map.of("items", List.of(item)));

        // JOIN 이 본체 없는 번역을 걸러 준다. 고아 행이 생기면 안 된다.
        assertTrue(sql.contains("JOIN event e ON e.pipeline_id = s.pipeline_id"),
                "본체와 JOIN 하지 않고 있습니다");
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), "재적재가 실패합니다");
        // 다시 보내면 노출 상태로 되돌아와야 한다.
        assertTrue(sql.contains("deleted_at      = NULL"), "deleted_at 을 되돌리지 않습니다");
    }

    @Test
    void insertEvents_assignsExplicitItemIds() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("items", List.of(event("a"), event("b")));
        parameters.put("itemIds", List.of(10L, 11L));

        String sql = sqlOf("insertEvents", parameters);

        assertTrue(sql.startsWith("INSERT INTO event"), "INSERT 문이 아닙니다");
        assertEquals(1, countOccurrences(sql, "INSERT INTO event"), "INSERT 가 여러 번 나갑니다");
    }

    private static EventIngestItem event(String pipelineId) {
        EventIngestItem item = new EventIngestItem();
        item.setPipelineId(pipelineId);
        item.setTitle("제목");
        return item;
    }

    private static PlaceIngestItem place(String pipelineId) {
        PlaceIngestItem item = new PlaceIngestItem();
        item.setPipelineId(pipelineId);
        item.setName("이름");
        return item;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count += 1;
            from = at + needle.length();
        }
    }

    private static String sqlOf(String statementId, Map<String, Object> parameters) throws Exception {
        MappedStatement statement = configuration().getMappedStatement(NS + statementId);
        return statement.getBoundSql(parameters).getSql();
    }

    private static Configuration configuration() throws Exception {
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
}
