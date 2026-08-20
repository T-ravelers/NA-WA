package me.nawa.config;

import me.nawa.common.ocr.ReceiptOcrClient;
import me.nawa.common.storage.ReceiptStorageService;
import me.nawa.member.service.MemberProfileServiceImpl;
import me.nawa.report.mapper.ReportMapper;
import me.nawa.report.service.ReportService;
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

    @Test
    void componentScan_includesReceiptStoragePackage() {
        ComponentScan componentScan = RootConfig.class.getAnnotation(
                ComponentScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                ReceiptStorageService.class.getPackageName()
        ));
    }

    /**
     * 이 패키지가 빠지면 글자 인식 빈이 만들어지지 않아 영수증 인식 요청이 통째로 실패한다.
     * 컴파일로는 드러나지 않고 실행해 봐야 알 수 있는 종류의 누락이라 여기서 붙잡는다.
     */
    @Test
    void componentScan_includesReceiptOcrPackage() {
        ComponentScan componentScan = RootConfig.class.getAnnotation(
                ComponentScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                ReceiptOcrClient.class.getPackageName()
        ));
    }

    @Test
    void reportPackages_areRegisteredInRootConfig() {
        ComponentScan componentScan = RootConfig.class.getAnnotation(
                ComponentScan.class
        );
        MapperScan mapperScan = RootConfig.class.getAnnotation(
                MapperScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                ReportService.class.getPackageName()
        ));
        assertTrue(Arrays.asList(mapperScan.basePackages()).contains(
                ReportMapper.class.getPackageName()
        ));
    }
}
