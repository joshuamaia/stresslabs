package br.com.joshua.loadgen.execution;

import java.util.UUID;

import br.com.joshua.loadgen.metrics.FinalReport;
import br.com.joshua.loadgen.metrics.Snapshot;

public interface ProgressListener {

    /** @return false quando o consumidor não aceita mais dados para a execução (ela deve ser cancelada). */
    boolean onSnapshot(UUID executionId, Snapshot snapshot);

    void onFinished(UUID executionId, FinalReport report);
}
