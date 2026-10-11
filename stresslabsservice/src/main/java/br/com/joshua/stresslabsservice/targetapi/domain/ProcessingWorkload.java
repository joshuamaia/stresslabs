package br.com.joshua.stresslabsservice.targetapi.domain;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

/**
 * Processamento CPU-bound controlado: encadeia hashes SHA-256.
 * Os limites rígidos (unidades máximas e prazo) impedem abuso de recursos.
 */
public final class ProcessingWorkload {

    public static final int MIN_WORK_UNITS = 1;
    public static final int MAX_WORK_UNITS = 50;
    public static final int HASHES_PER_UNIT = 5_000;
    public static final Duration MAX_DURATION = Duration.ofSeconds(2);

    private ProcessingWorkload() {
    }

    public static ProcessingResult execute(int workUnits) {
        if (workUnits < MIN_WORK_UNITS || workUnits > MAX_WORK_UNITS) {
            throw new DomainValidationException(
                    "workUnits deve estar entre " + MIN_WORK_UNITS + " e " + MAX_WORK_UNITS);
        }
        MessageDigest digest = sha256();
        long start = System.nanoTime();
        long deadline = start + MAX_DURATION.toNanos();
        byte[] state = ByteBuffer.allocate(Long.BYTES).putLong(workUnits).array();
        int completedUnits = 0;
        boolean truncated = false;
        for (int unit = 0; unit < workUnits; unit++) {
            if (System.nanoTime() > deadline) {
                truncated = true;
                break;
            }
            for (int i = 0; i < HASHES_PER_UNIT; i++) {
                state = digest.digest(state);
            }
            completedUnits++;
        }
        long elapsedNanos = System.nanoTime() - start;
        return new ProcessingResult(workUnits, completedUnits, (long) completedUnits * HASHES_PER_UNIT,
                HexFormat.of().formatHex(state), elapsedNanos / 1_000_000.0, truncated);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public record ProcessingResult(int requestedUnits, int completedUnits, long hashes, String digest,
                                   double elapsedMs, boolean truncated) {
    }
}
