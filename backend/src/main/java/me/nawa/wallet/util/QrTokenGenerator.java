package me.nawa.wallet.util;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class QrTokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate(){
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
    }

}
