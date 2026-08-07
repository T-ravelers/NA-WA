package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
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
        assertTrue(configuration.hasStatement(namespace + "findJourneyById"));
        assertTrue(configuration.hasStatement(
            namespace + "findRegionsByTripId"
        ));
    }
}
