package me.nawa.deposit.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DepositMapperDefinitionTest {

    @Test
    void depositMapperXml_registersExpectedStatements() throws IOException {
        Configuration configuration = loadMapper(
            "me/nawa/deposit/mapper/DepositMapper.xml"
        );

        assertStatements(
            configuration,
            "me.nawa.deposit.mapper.DepositMapper",
            List.of(
                "findById",
                "findByIdForUpdate",
                "findByAppointmentMemberId",
                "insert",
                "markHeld",
                "markCancelled",
                "markRefunded",
                "markDistributed"
            )
        );
    }

    @Test
    void depositPayoutBatchMapperXml_registersExpectedStatements()
        throws IOException {
        Configuration configuration = loadMapper(
            "me/nawa/deposit/mapper/DepositPayoutBatchMapper.xml"
        );

        assertStatements(
            configuration,
            "me.nawa.deposit.mapper.DepositPayoutBatchMapper",
            List.of(
                "findPendingOrFailedBatchIds",
                "findById",
                "findByIdForUpdate",
                "findByAppointmentId",
                "findByIdempotencyKey",
                "insert",
                "markProcessing",
                "markCompleted",
                "markFailed"
            )
        );
    }

    @Test
    void depositPayoutMapperXml_registersExpectedStatements()
        throws IOException {
        Configuration configuration = loadMapper(
            "me/nawa/deposit/mapper/DepositPayoutMapper.xml"
        );

        assertStatements(
            configuration,
            "me.nawa.deposit.mapper.DepositPayoutMapper",
            List.of(
                "findById",
                "findByTransferId",
                "findByBatchId",
                "findBySourceDepositId",
                "countByAllocation",
                "insert"
            )
        );
    }

    private static Configuration loadMapper(String resource)
        throws IOException {
        Configuration configuration = new Configuration();

        try (InputStream inputStream = DepositMapperDefinitionTest.class
            .getClassLoader()
            .getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IOException("Mapper resource not found: " + resource);
            }

            new XMLMapperBuilder(
                inputStream,
                configuration,
                resource,
                configuration.getSqlFragments()
            ).parse();
        }

        return configuration;
    }

    private static void assertStatements(
        Configuration configuration,
        String namespace,
        List<String> statementIds
    ) {
        for (String statementId : statementIds) {
            assertTrue(
                configuration.hasStatement(namespace + "." + statementId),
                () -> "Missing MyBatis statement: "
                    + namespace
                    + "."
                    + statementId
            );
        }
    }
}
