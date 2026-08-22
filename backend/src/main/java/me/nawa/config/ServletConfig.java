package me.nawa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableWebMvc
@PropertySource("classpath:/application.properties")
@ComponentScan(basePackages = {
    "me.nawa.common.exception",
    "me.nawa.auth.controller",
    "me.nawa.appointment.controller",
    "me.nawa.review.controller",
    "me.nawa.member.controller",
    "me.nawa.explore.controller",
    "me.nawa.journey.controller",
    "me.nawa.report.controller",
    "me.nawa.map.controller",
    "me.nawa.wallet.controller",
    "me.nawa.settlement.controller",
    "me.nawa.deposit.controller",
    "me.nawa.ingest.controller",
    "me.nawa.observability.controller"
})
public class ServletConfig implements WebMvcConfigurer {

    /*
     * 파일 업로드(multipart) 요청을 해석하는 객체.
     * - 이 프로젝트는 Spring Boot가 아니라서 application.properties의 multipart 설정이 통하지 않는다.
     * - 빈 이름이 반드시 "multipartResolver"여야 Spring MVC가 찾아 쓴다.
     * - 실제 크기 제한은 WebConfig에서 서블릿에 직접 건다.
     */
    @Bean(name = "multipartResolver")
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
//        registry.addViewController("/")
//                .setViewName("forward:/resources/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry
                .addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
