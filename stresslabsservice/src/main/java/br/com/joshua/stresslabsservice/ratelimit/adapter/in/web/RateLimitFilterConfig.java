package br.com.joshua.stresslabsservice.ratelimit.adapter.in.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService;

@Configuration
class RateLimitFilterConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitService rateLimitService) {
        var registration = new FilterRegistrationBean<>(new RateLimitFilter(rateLimitService));
        registration.addUrlPatterns("/api/target/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
