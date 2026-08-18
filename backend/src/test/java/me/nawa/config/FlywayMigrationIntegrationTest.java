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

        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals("11", current.getVersion().getVersion());
        assertTrue(current.getState().isApplied());

        verifyAppointmentParticipationSchema(flyway);
        verifyReviewKeywordSeed(flyway);
        verifyDepositPoolWalletSeed(flyway);
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
