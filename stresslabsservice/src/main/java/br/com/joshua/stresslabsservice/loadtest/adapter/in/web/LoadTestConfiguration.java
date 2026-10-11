package br.com.joshua.stresslabsservice.loadtest.adapter.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestLimits;
import br.com.joshua.stresslabsservice.shared.config.LabProperties;

@Configuration
class LoadTestConfiguration {

    @Bean
    LoadTestLimits loadTestLimits(LabProperties properties) {
        LabProperties.Limits limits = properties.limits();
        return new LoadTestLimits(limits.maxRequests(), limits.maxConcurrency(), limits.maxDurationSeconds(),
                limits.maxRps(), limits.minTimeoutMs(), limits.maxTimeoutMs());
    }
}
