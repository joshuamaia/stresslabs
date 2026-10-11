package br.com.joshua.stresslabsservice.shared.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.joshua.stresslabsservice.shared.config.LabProperties;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LabProperties properties;

    public WebConfig(LabProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .exposedHeaders("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After");
    }

    @Bean
    FilterRegistrationBean<LocalAccessFilter> localAccessFilter() {
        var registration = new FilterRegistrationBean<>(
                new LocalAccessFilter(properties.security().adminLocalOnly()));
        registration.addUrlPatterns("/api/admin/*", "/api/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    FilterRegistrationBean<InternalTokenFilter> internalTokenFilter() {
        var registration = new FilterRegistrationBean<>(new InternalTokenFilter(properties.internalToken()));
        registration.addUrlPatterns("/api/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        return registration;
    }
}
