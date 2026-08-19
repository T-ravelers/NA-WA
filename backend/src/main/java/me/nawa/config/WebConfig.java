package me.nawa.config;

import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;

public class WebConfig extends AbstractAnnotationConfigDispatcherServletInitializer {

    /*
     * 영수증 사진 업로드 크기 상한.
     * - 이 클래스는 스프링 빈이 아니라 서블릿 부팅 코드라서 @Value로 값을 주입받지 못한다.
     *   그래서 환경변수를 직접 읽는다.
     * - nginx의 client_max_body_size가 이 값보다 작으면 요청이 서버에 닿기도 전에 잘린다.
     */
    private static final String MAX_UPLOAD_BYTES_ENV = "RECEIPT_MAX_UPLOAD_BYTES";

    private static final long DEFAULT_MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

    // 파일 본체 외에 multipart 요청이 달고 오는 경계 문자열과 헤더 몫이다.
    private static final long REQUEST_OVERHEAD_BYTES = 1024L * 1024;

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

    // POST body 문자 인코딩 필터 설정 - UTF-8 설정
//    protected Filter[] getServletFilters() {
//        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
//
//        characterEncodingFilter.setEncoding("UTF-8");
//        characterEncodingFilter.setForceEncoding(true);
//
//        return new Filter[]{characterEncodingFilter};
//    }

    @Override
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        long maxUploadBytes = resolveMaxUploadBytes();
        registration.setMultipartConfig(new MultipartConfigElement(
            null,
            maxUploadBytes,
            maxUploadBytes + REQUEST_OVERHEAD_BYTES,
            0
        ));
    }

    private long resolveMaxUploadBytes() {
        String configured = System.getenv(MAX_UPLOAD_BYTES_ENV);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_UPLOAD_BYTES;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            return parsed > 0 ? parsed : DEFAULT_MAX_UPLOAD_BYTES;
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_UPLOAD_BYTES;
        }
    }
}
