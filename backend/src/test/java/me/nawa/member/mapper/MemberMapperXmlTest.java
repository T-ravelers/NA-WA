package me.nawa.member.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberMapperXmlTest {
    private static final String MAPPER_RESOURCE =
            "me/nawa/member/mapper/MemberMapper.xml";

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
    void mapperXml_parsesFindAuthStateStatement() throws Exception {
        assertTrue(parsedConfiguration().hasStatement(
                "me.nawa.member.mapper.MemberMapper.findAuthState"
        ));
    }

    @Test
    void mapperXml_parsesFindProfileStatement() throws Exception {
        assertTrue(parsedConfiguration().hasStatement(
                "me.nawa.member.mapper.MemberMapper.findProfile"
        ));
    }

    @Test
    void mapperXml_parsesMarkAsMerchantStatement() throws Exception {
        assertTrue(parsedConfiguration().hasStatement(
                "me.nawa.member.mapper.MemberMapper.markAsMerchant"
        ));
    }

    @Test
    void mapperXml_parsesExistsActiveCurrencyStatement() throws Exception {
        assertTrue(parsedConfiguration().hasStatement(
                "me.nawa.member.mapper.MemberMapper.existsActiveCurrency"
        ));
    }

    @Test
    void mapperXml_parsesUpdateProfileStatement() throws Exception {
        assertTrue(parsedConfiguration().hasStatement(
                "me.nawa.member.mapper.MemberMapper.updateProfile"
        ));
    }

    @Test
    void mapperXml_parsesCompleteOnboardingStatement() throws Exception {
        assertTrue(parsedConfiguration().hasStatement(
                "me.nawa.member.mapper.MemberMapper.completeOnboarding"
        ));
    }
}
