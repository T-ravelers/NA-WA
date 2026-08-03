package me.nawa.auth.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowedOriginPolicyTest {
    @Test
    void constructor_configuredOrigins_trimsAndDeduplicates() {
        AllowedOriginPolicy policy = new AllowedOriginPolicy(
                "http://localhost:5173, https://app.example.com, "
                        + "http://localhost:5173"
        );

        assertEquals(
                List.of(
                        "http://localhost:5173",
                        "https://app.example.com"
                ),
                policy.getAllowedOrigins()
        );
        assertTrue(policy.allows("http://localhost:5173"));
        assertFalse(policy.allows("http://localhost:5174"));
        assertFalse(policy.allows(null));
    }

    @Test
    void constructor_wildcardOrigin_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AllowedOriginPolicy("*")
        );
    }

    @Test
    void constructor_blankOrigins_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AllowedOriginPolicy(" , ")
        );
    }
}
