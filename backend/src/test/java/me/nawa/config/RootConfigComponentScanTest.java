package me.nawa.config;

import me.nawa.auth.profile.AuthMeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

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

    @Test
    void mapperScan_includesDepositMapperPackage() {
        MapperScan mapperScan = RootConfig.class.getAnnotation(
                MapperScan.class
        );

        assertTrue(Arrays.asList(mapperScan.basePackages()).contains(
                "me.nawa.deposit.mapper"
        ));
    }
}
