package br.com.joshua.loadgen.config;

import java.time.Clock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.joshua.loadgen.security.InternalTokenFilter;
import br.com.joshua.loadgen.security.TargetGuard;

@Configuration
class GeneratorConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TargetGuard targetGuard(GeneratorProperties properties) {
        return new TargetGuard(properties.targetBaseUrl(), properties.allowedPathPrefixes());
    }

    @Bean
    FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(GeneratorProperties properties) {
        var registration = new FilterRegistrationBean<>(new InternalTokenFilter(properties.internalToken()));
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
