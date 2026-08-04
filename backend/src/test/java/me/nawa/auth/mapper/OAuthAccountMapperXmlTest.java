package me.nawa.auth.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthAccountMapperXmlTest {
    private static final String MAPPER_RESOURCE =
            "me/nawa/auth/mapper/OAuthAccountMapper.xml";
    private static final String NAMESPACE =
            "me.nawa.auth.mapper.OAuthAccountMapper.";

    @Test
    void mapperXml_parsesAllOAuthAccountStatements() throws Exception {
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
                NAMESPACE + "findLoginAccount"
        ));
        assertTrue(configuration.hasStatement(
                NAMESPACE + "insertMember"
        ));
        assertTrue(configuration.hasStatement(
                NAMESPACE + "insertSocialAccount"
        ));
    }
}
