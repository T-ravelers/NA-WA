package me.nawa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;

public class WebConfig extends AbstractAnnotationConfigDispatcherServletInitializer {

    // 이 클래스는 스프링 빈이 아니라 서블릿 부팅 코드라서 롬복의 @Slf4j를 쓰지 않고
    // 로거를 직접 만든다.
    private static final Logger LOGGER = LoggerFactory.getLogger(WebConfig.class);

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
        return new Class[]{RootConfig.class, MetricsConfig.class};
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

    /*
     * 값이 이상하면 기본값으로 돌아가되 반드시 흔적을 남긴다. 조용히 넘어가면 오타 하나로
     * 상한이 8MB로 되돌아간 것을 아무도 모른 채 "왜 큰 사진만 실패하지"를 헤매게 된다.
     */
    private long resolveMaxUploadBytes() {
        String configured = System.getenv(MAX_UPLOAD_BYTES_ENV);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_UPLOAD_BYTES;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            if (parsed > 0) {
                return parsed;
            }
            warnFallback(configured, "0보다 커야 합니다");
        } catch (NumberFormatException exception) {
            warnFallback(configured, "숫자가 아닙니다");
        }
        return DEFAULT_MAX_UPLOAD_BYTES;
    }

    private void warnFallback(String configured, String reason) {
        LOGGER.warn(
            "{} 값이 올바르지 않아({}) 기본값 {}바이트를 사용합니다. 설정값={}",
            MAX_UPLOAD_BYTES_ENV, reason, DEFAULT_MAX_UPLOAD_BYTES, configured
        );
    }
}
