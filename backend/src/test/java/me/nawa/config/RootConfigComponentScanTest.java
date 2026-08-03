package me.nawa.config;

import me.nawa.auth.profile.AuthMeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RootConfigComponentScanTest {

    @Test
    void componentScan_includesCurrentMemberServicePackage() {
        ComponentScan componentScan = RootConfig.class.getAnnotation(
                ComponentScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                AuthMeServiceImpl.class.getPackageName()
        ));
    }
}
