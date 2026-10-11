package br.com.joshua.loadgen.metrics;

import java.util.Arrays;

/**
 * Armazena a latência de cada resposta (nanossegundos) em um array append-only.
 * Memória limitada pelo máximo de requisições do teste (8 bytes por requisição).
 * Leituras por intervalo permitem calcular percentis exatos por janela e globais.
 */
public class LatencyRecorder {

    private long[] values;
    private int size;

    public LatencyRecorder(int expectedCapacity) {
        this.values = new long[Math.max(16, expectedCapacity)];
    }

    public synchronized void record(long nanos) {
        if (size == values.length) {
            values = Arrays.copyOf(values, values.length * 2);
        }
        values[size++] = nanos;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized long[] copyRange(int fromInclusive, int toExclusive) {
        int to = Math.min(toExclusive, size);
        int from = Math.clamp(fromInclusive, 0, to);
        return Arrays.copyOfRange(values, from, to);
    }

    public synchronized long[] copyAll() {
        return Arrays.copyOf(values, size);
    }
}
