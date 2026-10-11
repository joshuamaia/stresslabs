package br.com.joshua.stresslabsservice.testhistory.domain;

/**
 * Diferença observada entre duas execuções (B - A). preference indica apenas a semântica da métrica
 * (ex.: menor latência é preferível); o resultado nunca é presumido.
 */
public record ComparisonMetric(String key, String label, String unit, double valueA, double valueB,
                               double difference, Double percentChange, Preference preference) {

    public enum Preference { HIGHER_IS_BETTER, LOWER_IS_BETTER, NEUTRAL }

    public static ComparisonMetric of(String key, String label, String unit, double a, double b,
                                      Preference preference) {
        Double percent = a == 0 ? null : (b - a) / Math.abs(a) * 100.0;
        return new ComparisonMetric(key, label, unit, a, b, b - a, percent, preference);
    }
}
