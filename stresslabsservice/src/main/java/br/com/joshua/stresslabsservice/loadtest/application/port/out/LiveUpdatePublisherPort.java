package br.com.joshua.stresslabsservice.loadtest.application.port.out;

import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

/** Publica atualizações em tempo real para os clientes conectados (SSE). */
public interface LiveUpdatePublisherPort {

    void publishStatus(TestExecution execution);

    void publishSnapshot(MetricSnapshot snapshot);

    void publishResult(TestResult result);
}
