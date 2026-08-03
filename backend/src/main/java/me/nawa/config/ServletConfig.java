package me.nawa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.*;

import java.util.Arrays;

@Configuration
@EnableWebMvc
@PropertySource("classpath:/application.properties")
@ComponentScan(basePackages = {
    "me.nawa.common.exception",
    "me.nawa.auth.controller",
    "me.nawa.member.controller",
    "me.nawa.event.controller",
    "me.nawa.journey.controller",
    "me.nawa.map.controller",
    "me.nawa.wallet.controller",
    "me.nawa.settlement.controller",
    "me.nawa.deposit.controller"

})
public class ServletConfig implements WebMvcConfigurer {
    @Value("${auth.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0) {
            throw new IllegalArgumentException(
                    "At least one auth allowed origin is required"
            );
        }
        if (Arrays.asList(origins).contains("*")) {
            throw new IllegalArgumentException(
                    "Wildcard origin is not allowed with credentials"
            );
        }

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
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
