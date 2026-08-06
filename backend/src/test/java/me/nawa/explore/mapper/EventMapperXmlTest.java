package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
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
}
