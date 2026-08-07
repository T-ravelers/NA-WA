package me.nawa.config;

import me.nawa.member.service.MemberProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RootConfigComponentScanTest {

    @Test
    void componentScan_includesMemberProfileServicePackage() {
        ComponentScan componentScan = RootConfig.class.getAnnotation(
                ComponentScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                MemberProfileServiceImpl.class.getPackageName()
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
