package me.nawa.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@EnableWebMvc
@PropertySource("classpath:/application.properties")
@ComponentScan(basePackages = {
    "me.nawa.common.exception",
    "me.nawa.auth.controller",
    "me.nawa.member.controller",
    "me.nawa.explore.controller",
    "me.nawa.journey.controller",
    "me.nawa.report.controller",
    "me.nawa.map.controller",
    "me.nawa.wallet.controller",
    "me.nawa.settlement.controller",
    "me.nawa.deposit.controller"
})
public class ServletConfig implements WebMvcConfigurer {
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
