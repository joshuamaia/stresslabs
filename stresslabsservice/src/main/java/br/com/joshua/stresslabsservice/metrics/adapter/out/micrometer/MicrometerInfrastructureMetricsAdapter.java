package br.com.joshua.stresslabsservice.metrics.adapter.out.micrometer;

import java.time.Clock;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import br.com.joshua.stresslabsservice.metrics.application.port.out.InfrastructureMetricsPort;
import br.com.joshua.stresslabsservice.metrics.domain.InfrastructureMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;

/** Lê métricas já coletadas pelo Actuator/Micrometer, retornando null quando indisponíveis. */
@Component
class MicrometerInfrastructureMetricsAdapter implements InfrastructureMetricsPort {

    private final MeterRegistry registry;
    private final Clock clock;

    MicrometerInfrastructureMetricsAdapter(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    @Override
    public InfrastructureMetrics read() {
        Collection<Timer> httpTimers = registry.find("http.server.requests").timers();
        long httpCount = httpTimers.stream().mapToLong(Timer::count).sum();
        double totalTimeMs = httpTimers.stream().mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS)).sum();
        double maxMs = httpTimers.stream().mapToDouble(t -> t.max(TimeUnit.MILLISECONDS)).max().orElse(0);

        return new InfrastructureMetrics(
                clock.instant(),
                gauge("process.cpu.usage"),
                gauge("system.cpu.usage"),
                Runtime.getRuntime().availableProcessors(),
                sumGauges(registry.find("jvm.memory.used").tag("area", "heap")),
                sumGauges(registry.find("jvm.memory.max").tag("area", "heap")),
                sumGauges(registry.find("jvm.memory.used").tag("area", "nonheap")),
                gauge("jvm.threads.live"),
                gauge("jvm.threads.peak"),
                gauge("jvm.threads.daemon"),
                httpCount,
                httpCount == 0 ? 0.0 : totalTimeMs / httpCount,
                maxMs,
                gauge("hikaricp.connections.active"),
                gauge("hikaricp.connections.idle"),
                gauge("hikaricp.connections.pending"),
                gauge("hikaricp.connections.max"),
                gauge("process.uptime"));
    }

    private Double gauge(String name) {
        // TimeGauge (ex.: process.uptime) também é um Gauge, expresso na unidade base (segundos)
        Gauge gauge = registry.find(name).gauge();
        return gauge != null ? finite(gauge.value()) : null;
    }

    private static Double sumGauges(Search search) {
        Collection<Gauge> gauges = search.gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        // jvm.memory.max pode ser -1 para regiões sem limite
        return gauges.stream().mapToDouble(Gauge::value).filter(v -> v >= 0).sum();
    }

    private static Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }
}
