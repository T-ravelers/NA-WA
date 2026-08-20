package me.nawa.review.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewMapperXmlTest {
    private static final String RESOURCE =
            "me/nawa/review/mapper/ReviewMapper.xml";

    @Test
    void mapperXml_registersReviewStatements() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }

        String namespace = "me.nawa.review.mapper.ReviewMapper.";
        assertTrue(configuration.hasStatement(
                namespace + "findReviewedAppointmentMemberIds"
        ));
        assertTrue(configuration.hasStatement(namespace + "countReviewPair"));
        assertTrue(configuration.hasStatement(namespace + "countActiveKeywords"));
        assertTrue(configuration.hasStatement(namespace + "insertReview"));
        assertTrue(configuration.hasStatement(namespace + "insertScores"));
        assertTrue(configuration.hasStatement(
                namespace + "insertKeywordSelections"
        ));
    }
}
