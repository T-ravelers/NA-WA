package me.nawa.auth.oauth.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthReturnPathPolicyTest {
    @Test
    void requireAllowed_blankPath_returnsDefaultRootPath() {
        OAuthReturnPathPolicy policy = new OAuthReturnPathPolicy(
                "/,/journeys"
        );

        assertEquals("/", policy.requireAllowed(null));
        assertEquals("/", policy.requireAllowed(" "));
    }

    @Test
    void requireAllowed_configuredPath_returnsExactPath() {
        OAuthReturnPathPolicy policy = new OAuthReturnPathPolicy(
                "/,/journeys"
        );

        assertEquals(
                "/journeys",
                policy.requireAllowed(" /journeys ")
        );
    }

    @Test
    void requireAllowed_unconfiguredPath_throwsException() {
        OAuthReturnPathPolicy policy = new OAuthReturnPathPolicy("/");

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.requireAllowed("/admin")
        );
    }

    @Test
    void constructor_externalOrAmbiguousPath_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthReturnPathPolicy("https://evil.example")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthReturnPathPolicy("//evil.example")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthReturnPathPolicy("/callback?next=/admin")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthReturnPathPolicy("/callback#fragment")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthReturnPathPolicy("/callback\nInjected")
        );
    }

    @Test
    void constructor_blankConfiguration_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthReturnPathPolicy(" , ")
        );
    }
}
