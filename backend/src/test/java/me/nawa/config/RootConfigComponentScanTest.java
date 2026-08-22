package me.nawa.config;

import me.nawa.common.ocr.ReceiptOcrClient;
import me.nawa.common.storage.ReceiptStorageService;
import me.nawa.ingest.controller.IngestController;
import me.nawa.ingest.mapper.IngestMapper;
import me.nawa.ingest.service.IngestService;
import me.nawa.member.service.MemberProfileServiceImpl;
import me.nawa.observability.controller.MetricsController;
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
    void ingestPackages_areRegisteredInRootConfig() {
        // 등록을 빠뜨리면 컨트롤러가 스캔되지 않아 매핑 자체가 생기지 않는다.
        // 실제로 #284 가 이 상태로 배포돼 적재 경로가 404 였다. 클래스는 WAR
        // 안에 있는데 Spring 이 "No mapping for POST" 를 남긴다.
        //
        // 이 프로젝트는 Spring Boot 가 아니라 패키지를 명시 등록해야 한다.
        ComponentScan componentScan = RootConfig.class.getAnnotation(
                ComponentScan.class
        );
        MapperScan mapperScan = RootConfig.class.getAnnotation(
                MapperScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                IngestService.class.getPackageName()
        ));
        assertTrue(Arrays.asList(mapperScan.basePackages()).contains(
                IngestMapper.class.getPackageName()
        ));
    }

    @Test
    void ingestController_isRegisteredInServletConfig() {
        // 컨트롤러는 서블릿 컨텍스트에서 스캔한다. RootConfig 와 별개다.
        ComponentScan componentScan = ServletConfig.class.getAnnotation(
                ComponentScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                IngestController.class.getPackageName()
        ));
    }

    @Test
    void observabilityController_isRegisteredInServletConfig() {
        // 지표 엔드포인트가 스캔에서 빠지면 레지스트리는 멀쩡한데 수집만 조용히 끊긴다.
        ComponentScan componentScan = ServletConfig.class.getAnnotation(
                ComponentScan.class
        );

        assertTrue(Arrays.asList(componentScan.basePackages()).contains(
                MetricsController.class.getPackageName()
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
