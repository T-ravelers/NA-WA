package me.nawa.explore.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploreItemLikeMapperXmlTest {
    private static final String MAPPER_RESOURCE =
            "me/nawa/explore/mapper/ExploreItemLikeMapper.xml";

    private Configuration parsedConfiguration() throws Exception {
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

    @Test
    void mapperXml_parsesEveryLikeStatement() throws Exception {
        Configuration configuration = parsedConfiguration();

        for (String statement : new String[] {
                "findVisibleItemType",
                "findItemType",
                "reviveLike",
                "insertLike",
                "softDeleteLike",
                "adjustEventFavoriteCount",
                "adjustPlaceFavoriteCount",
        }) {
            assertTrue(configuration.hasStatement(
                    "me.nawa.explore.mapper.ExploreItemLikeMapper." + statement
            ), statement);
        }
    }
}
