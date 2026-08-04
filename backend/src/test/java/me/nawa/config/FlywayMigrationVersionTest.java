package me.nawa.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FlywayMigrationVersionTest {
    private static final Path MIGRATION_DIRECTORY = Path.of(
            "src/main/resources/db/migration"
    );
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile(
            "^V([0-9]+(?:\\.[0-9]+)*)__.+\\.sql$"
    );

    @Test
    void versionedMigrations_haveUniqueVersions() throws IOException {
        Map<String, String> fileByVersion = new HashMap<>();

        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(fileName -> registerVersion(
                            fileByVersion,
                            fileName
                    ));
        }
    }

    private void registerVersion(
            Map<String, String> fileByVersion,
            String fileName) {
        Matcher matcher = VERSIONED_MIGRATION.matcher(fileName);
        assertTrue(
                matcher.matches(),
                () -> "Invalid Flyway migration file name: " + fileName
        );

        String version = matcher.group(1);
        String existingFile = fileByVersion.putIfAbsent(version, fileName);
        if (existingFile != null) {
            fail(
                    "Duplicate Flyway version V" + version
                            + ": " + existingFile + ", " + fileName
            );
        }
    }
}
