package me.nawa.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServletConfigTest {
    @Test
    void addCorsMappings_configuredOrigins_allowsCredentialsForApiOnly() {
        ServletConfig servletConfig = createConfig(
                "http://localhost:5173, https://app.example.com"
        );
        TestCorsRegistry registry = new TestCorsRegistry();

        servletConfig.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations =
                registry.configurations();
        assertEquals(1, configurations.size());
        CorsConfiguration configuration = configurations.get("/api/**");
        assertEquals(
                List.of(
                        "http://localhost:5173",
                        "https://app.example.com"
                ),
                configuration.getAllowedOrigins()
        );
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertEquals(3600L, configuration.getMaxAge());
    }

    @Test
    void addCorsMappings_wildcardOrigin_throwsException() {
        ServletConfig servletConfig = createConfig("*");

        assertThrows(
                IllegalArgumentException.class,
                () -> servletConfig.addCorsMappings(new TestCorsRegistry())
        );
    }

    @Test
    void addCorsMappings_blankOrigins_throwsException() {
        ServletConfig servletConfig = createConfig(" , ");

        assertThrows(
                IllegalArgumentException.class,
                () -> servletConfig.addCorsMappings(new TestCorsRegistry())
        );
    }

    private ServletConfig createConfig(String allowedOrigins) {
        ServletConfig servletConfig = new ServletConfig();
        ReflectionTestUtils.setField(
                servletConfig,
                "allowedOrigins",
                allowedOrigins
        );
        return servletConfig;
    }

    private static final class TestCorsRegistry extends CorsRegistry {
        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
