package me.nawa.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class FlywayMigrationIntegrationTest {
    @Test
    void migrations_applyThroughLatestVersion() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        requiredEnvironment("DATABASE_URL"),
                        requiredEnvironment("DATABASE_USERNAME"),
                        requiredEnvironment("DATABASE_PASSWORD")
                )
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        // 최신 버전을 리터럴로 하드코딩하면 새 마이그레이션이 추가될 때마다
        // 이 값을 함께 올려야 하는데, V13 도입(#254) 때 놓쳐서 게이트 ON
        // 실행이 계속 실패했다(#274). classpath에서 실제로 발견된 마이그레이션
        // 중 가장 높은 버전을 기대값으로 삼아, 새 마이그레이션이 추가돼도
        // 이 단정문을 따로 갱신할 필요가 없게 한다.
        MigrationInfo[] discovered = flyway.info().all();
        assertTrue(discovered.length > 0);
        String latestDiscoveredVersion =
                discovered[discovered.length - 1].getVersion().getVersion();

        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals(latestDiscoveredVersion, current.getVersion().getVersion());
        assertTrue(current.getState().isApplied());

        verifyAppointmentParticipationSchema(flyway);
        verifyReviewKeywordSeed(flyway);
        verifyMemberAccountTypeSchema(flyway);
        verifyDepositPoolWalletSeed(flyway);
        verifySettlementReceiptSchema(flyway);
    }

    private static void verifySettlementReceiptSchema(Flyway flyway) {
        String sql = """
                SELECT
                    (SELECT is_nullable FROM information_schema.columns
                      WHERE table_schema = DATABASE()
                        AND table_name = 'settlement_receipts'
                        AND column_name = 'settlement_id') AS settlement_id_nullable,
                    (SELECT COUNT(*) FROM information_schema.statistics
                      WHERE table_schema = DATABASE()
                        AND table_name = 'settlement_receipts'
                        AND index_name = 'uq_settlement_receipts_settlement'
                        AND non_unique = 0) AS settlement_id_unique
                """;

        try (Connection connection = flyway.getConfiguration()
                .getDataSource()
                .getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            /*
             * settlement_id는 NULL을 허용하면서 UNIQUE여야 한다. NULL이 초안을 뜻하고,
             * MySQL은 UNIQUE 열에 NULL을 여러 개 허용하므로 초안 여러 개가 공존하면서도
             * 정산 하나에는 영수증이 한 장만 붙는다. 둘 중 하나라도 빠지면 이 규칙이 깨진다.
             */
            assertEquals("YES", resultSet.getString("settlement_id_nullable"));
            assertEquals(1, resultSet.getInt("settlement_id_unique"));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to verify settlement receipt migration",
                    exception
            );
        }
    }

    private static void verifyMemberAccountTypeSchema(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration()
                .getDataSource()
                .getConnection()) {
            String columnType = columnType(connection, "members", "account_type");
            assertTrue(columnType.contains("traveler"));
            assertTrue(columnType.contains("merchant"));
            // 기존 회원은 DEFAULT로만 보정된다. 기본값이 빠지면 백필 없이 배포한 회원이 NULL이 된다.
            assertEquals(
                    "TRAVELER",
                    columnDefault(connection, "members", "account_type")
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to verify member account type migration",
                    exception
            );
        }
    }

    private static void verifyAppointmentParticipationSchema(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration()
                .getDataSource()
                .getConnection()) {
            assertTrue(columnType(connection, "appointments", "appointment_status")
                    .contains("payment_pending"));
            assertEquals(
                    "PAYMENT_PENDING",
                    columnDefault(
                            connection,
                            "appointments",
                            "appointment_status"
                    )
            );
            assertTrue(columnType(
                    connection,
                    "appointment_members",
                    "membership_status"
            ).contains("pending"));
            assertEquals(
                    "PENDING",
                    columnDefault(
                            connection,
                            "appointment_members",
                            "membership_status"
                    )
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to verify appointment migration",
                    exception
            );
        }
    }

    private static void verifyReviewKeywordSeed(Flyway flyway) {
        String sql = """
                SELECT COUNT(*)
                FROM member_review_keywords
                WHERE keyword_code IN (
                    'FRIENDLY',
                    'ON_TIME',
                    'CONSIDERATE',
                    'GOOD_COMMUNICATOR',
                    'WOULD_JOIN_AGAIN'
                )
                  AND is_active = TRUE
                """;

        try (Connection connection = flyway.getConfiguration()
                .getDataSource()
                .getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals(5, resultSet.getInt(1));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to verify review keyword seed",
                    exception
            );
        }
    }

    private static void verifyDepositPoolWalletSeed(Flyway flyway) {
        String sql = """
                SELECT w.currency_code, w.wallet_status
                FROM wallet_owners o
                JOIN wallets w ON w.wallet_owner_id = o.wallet_owner_id
                WHERE o.owner_type = 'SYSTEM'
                  AND o.system_code = 'DEPOSIT_POOL'
                  AND o.deleted_at IS NULL
                  AND w.deleted_at IS NULL
                """;

        try (Connection connection = flyway.getConfiguration()
                .getDataSource()
                .getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals("KRW", resultSet.getString("currency_code"));
            assertEquals("ACTIVE", resultSet.getString("wallet_status"));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to verify deposit pool wallet seed",
                    exception
            );
        }
    }

    private static String columnType(
            Connection connection,
            String tableName,
            String columnName) throws SQLException {
        return columnMetadata(connection, tableName, columnName, "COLUMN_TYPE")
                .toLowerCase();
    }

    private static String columnDefault(
            Connection connection,
            String tableName,
            String columnName) throws SQLException {
        return columnMetadata(
                connection,
                tableName,
                columnName,
                "COLUMN_DEFAULT"
        );
    }

    private static String columnMetadata(
            Connection connection,
            String tableName,
            String columnName,
            String selectedColumn) throws SQLException {
        String sql = "SELECT " + selectedColumn + " "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? "
                + "AND COLUMN_NAME = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required for MySQL integration tests"
            );
        }
        return value;
    }
}
