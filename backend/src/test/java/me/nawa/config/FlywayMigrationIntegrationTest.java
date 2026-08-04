package me.nawa.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
        assertEquals("5", current.getVersion().getVersion());
        assertTrue(current.getState().isApplied());
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
