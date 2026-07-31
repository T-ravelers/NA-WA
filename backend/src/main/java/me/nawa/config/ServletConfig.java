package me.nawa.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {
    "me.nawa.common.exception",
//    "me.nawa.auth.controller",
//    "me.nawa.member.controller",
//    "me.nawa.event.controller",
//    "me.nawa.journey.controller",
//    "me.nawa.map.controller",
//    "me.nawa.wallet.controller"
})
public class ServletConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
//        registry.addViewController("/")
//                .setViewName("forward:/resources/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry
//                .addResourceHandler("/resources/**")     // url이 /resources/로 시작하는 모든 경로
//                .addResourceLocations("/resources/");    // webapp/resources/경로로 매핑
    }
}
