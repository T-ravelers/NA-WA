package me.nawa.auth.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthMapperXmlTest {
    private static final String MAPPER_RESOURCE =
            "me/nawa/auth/mapper/AuthMapper.xml";

    @Test
    void mapperXml_parsesCurrentMemberProfileStatement() throws Exception {
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

        assertTrue(configuration.hasStatement(
                "me.nawa.auth.mapper.AuthMapper.findMemberProfile"
        ));
    }
}
