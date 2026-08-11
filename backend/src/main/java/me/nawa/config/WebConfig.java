package me.nawa.config;

import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;

public class WebConfig extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class[]{RootConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[]{ServletConfig.class, SwaggerConfig.class};
    }

    // 스프링의 FrontController인 DispatcherServlet이 담당할 Url 매핑 패턴, / : 모든 요청에 대해 매핑
    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }

    @Override
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        registration.setMultipartConfig(multipartConfigElement());
    }

    MultipartConfigElement multipartConfigElement() {
        long maxFileSize = longSetting(
            "settlement.receipt.max-file-size-bytes",
            "SETTLEMENT_RECEIPT_MAX_FILE_SIZE_BYTES",
            5L * 1024 * 1024
        );
        long maxRequestSize = longSetting(
            "settlement.receipt.max-request-size-bytes",
            "SETTLEMENT_RECEIPT_MAX_REQUEST_SIZE_BYTES",
            6L * 1024 * 1024
        );
        int threshold = Math.toIntExact(longSetting(
            "settlement.receipt.file-size-threshold-bytes",
            "SETTLEMENT_RECEIPT_FILE_SIZE_THRESHOLD_BYTES",
            0L
        ));
        String location = setting(
            "settlement.receipt.upload-temp-dir",
            "SETTLEMENT_RECEIPT_UPLOAD_TEMP_DIR",
            System.getProperty("java.io.tmpdir")
        );
        return new MultipartConfigElement(location, maxFileSize, maxRequestSize, threshold);
    }

    private long longSetting(String systemProperty, String environmentVariable, long defaultValue) {
        String value = setting(systemProperty, environmentVariable, Long.toString(defaultValue));
        long parsed = Long.parseLong(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(systemProperty + " must not be negative");
        }
        return parsed;
    }

    private String setting(String systemProperty, String environmentVariable, String defaultValue) {
        String systemValue = System.getProperty(systemProperty);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(environmentVariable);
        return environmentValue == null || environmentValue.isBlank() ? defaultValue : environmentValue;
    }

    // POST body 문자 인코딩 필터 설정 - UTF-8 설정
//    protected Filter[] getServletFilters() {
//        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
//
//        characterEncodingFilter.setEncoding("UTF-8");
//        characterEncodingFilter.setForceEncoding(true);
//
//        return new Filter[]{characterEncodingFilter};
//    }
}
