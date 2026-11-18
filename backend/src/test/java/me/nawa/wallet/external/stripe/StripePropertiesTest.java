package me.nawa.wallet.external.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StripePropertiesTest {

    @Test
    void 켜져_있고_키가_모두_있으면_생성된다() {
        StripeProperties properties = new StripeProperties(true, "sk_test_value", "whsec_value");

        assertTrue(properties.isEnabled());
        assertEquals("sk_test_value", properties.getSecretKey());
        assertEquals("whsec_value", properties.getWebhookSecret());
    }

    @Test
    void 꺼져_있으면_키가_비어_있어도_생성된다() {
        StripeProperties properties = new StripeProperties(false, "", "");

        assertFalse(properties.isEnabled());
    }

    @Test
    void 켜져_있는데_시크릿_키가_비어_있으면_시작할_때_멈춘다() {
        assertThrows(IllegalStateException.class, () -> new StripeProperties(true, "", "whsec_value"));
        assertThrows(IllegalStateException.class, () -> new StripeProperties(true, "  ", "whsec_value"));
        assertThrows(IllegalStateException.class, () -> new StripeProperties(true, null, "whsec_value"));
    }

    @Test
    void 켜져_있는데_웹훅_시크릿이_비어_있으면_시작할_때_멈춘다() {
        assertThrows(IllegalStateException.class, () -> new StripeProperties(true, "sk_test_value", ""));
    }

    @Test
    void 멈출_때_메시지에_키_값을_담지_않는다() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new StripeProperties(true, "sk_test_secret_value", "")
        );

        assertFalse(exception.getMessage().contains("sk_test_secret_value"));
        assertTrue(exception.getMessage().contains("PAYMENT_ENABLED=false"));
    }
}
