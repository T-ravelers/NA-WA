package me.nawa.auth.oauth.state;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class OAuthReturnPathPolicy {
    private static final String DEFAULT_RETURN_PATH = "/";

    private final Set<String> allowedReturnPaths;

    public OAuthReturnPathPolicy(
            @Value("${auth.frontend.allowed-return-paths:/}")
            String configuredPaths) {
        LinkedHashSet<String> paths = Arrays.stream(
                        configuredPaths.split(",")
                )
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(
                        LinkedHashSet::new,
                        LinkedHashSet::add,
                        LinkedHashSet::addAll
                );

        if (paths.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one OAuth return path is required"
            );
        }
        paths.forEach(this::validateConfiguredPath);
        this.allowedReturnPaths = Set.copyOf(paths);
    }

    public String requireAllowed(String returnPath) {
        String normalizedPath = StringUtils.hasText(returnPath)
                ? returnPath.trim()
                : DEFAULT_RETURN_PATH;
        if (!allowedReturnPaths.contains(normalizedPath)) {
            throw new IllegalArgumentException(
                    "OAuth return path is not allowed"
            );
        }
        return normalizedPath;
    }

    private void validateConfiguredPath(String path) {
        if (path.contains("\r") || path.contains("\n")) {
            throw new IllegalArgumentException(
                    "OAuth return path must not contain line breaks"
            );
        }

        URI uri;
        try {
            uri = URI.create(path);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "OAuth return path is invalid",
                    exception
            );
        }

        if (!path.startsWith("/")
                || path.startsWith("//")
                || uri.isAbsolute()
                || uri.getRawAuthority() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "OAuth return path must be an absolute application path"
            );
        }
    }
}
