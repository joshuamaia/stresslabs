package br.com.joshua.loadgen.execution;

/**
 * Taxa de envio alvo (requisições/segundo) em função do tempo decorrido, por cenário.
 * A taxa é independente da concorrência: o limite de requisições simultâneas pode impedir que ela seja atingida.
 */
public record RateProfile(Scenario scenario, double targetRps, double durationSeconds) {

    static final double RAMP_START_FRACTION = 0.10;
    static final double SPIKE_BASE_FRACTION = 0.20;
    static final double SPIKE_START = 0.40;
    static final double SPIKE_END = 0.60;

    public RateProfile {
        if (targetRps <= 0 || durationSeconds <= 0) {
            throw new IllegalArgumentException("targetRps e durationSeconds devem ser positivos");
        }
    }

    public double rateAt(double elapsedSeconds) {
        double fraction = Math.clamp(elapsedSeconds / durationSeconds, 0.0, 1.0);
        return switch (scenario) {
            case CONSTANT, RATE_LIMIT -> targetRps;
            case RAMP_UP -> {
                double start = Math.max(1.0, targetRps * RAMP_START_FRACTION);
                yield start + (targetRps - start) * fraction;
            }
            case SPIKE -> fraction >= SPIKE_START && fraction < SPIKE_END
                    ? targetRps
                    : Math.max(1.0, targetRps * SPIKE_BASE_FRACTION);
        };
    }
}
