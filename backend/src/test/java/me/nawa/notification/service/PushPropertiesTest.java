package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PushPropertiesTest {

    @Test
    void bothKeysBlank_turnsPushOffAndStillStarts() {
        PushProperties properties = new PushProperties("", "  ", null);

        assertFalse(properties.isEnabled());
    }

    /** 한쪽만 채우면 멀쩡히 뜬 뒤 알림이 생길 때에야 실패한다. 시작할 때 막아야 한다. */
    @Test
    void onlyOneKey_failsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new PushProperties("pub", "", ""));
        assertThrows(IllegalStateException.class, () -> new PushProperties("", "priv", ""));
    }

    @Test
    void bothKeys_enablePushAndFallBackToTheServiceUrlAsSubject() {
        PushProperties properties = new PushProperties(" pub ", " priv ", " ");

        assertTrue(properties.isEnabled());
        assertEquals("pub", properties.getPublicKey());
        assertEquals("priv", properties.getPrivateKey());
        assertEquals("https://na-wa.cloud", properties.getSubject());
    }

    @Test
    void subject_mustBeMailtoOrHttpsWhenPushIsOn() {
        assertEquals("mailto:ops@example.test",
            new PushProperties("pub", "priv", "mailto:ops@example.test").getSubject());
        assertThrows(IllegalStateException.class, () -> new PushProperties("pub", "priv", "ops@example.test"));
        // 꺼져 있으면 쓰지 않는 값이라 따지지 않는다.
        assertFalse(new PushProperties("", "", "garbage").isEnabled());
    }
}
