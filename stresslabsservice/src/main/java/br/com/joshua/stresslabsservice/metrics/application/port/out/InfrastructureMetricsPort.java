package br.com.joshua.stresslabsservice.metrics.application.port.out;

import br.com.joshua.stresslabsservice.metrics.domain.InfrastructureMetrics;

public interface InfrastructureMetricsPort {

    InfrastructureMetrics read();
}
