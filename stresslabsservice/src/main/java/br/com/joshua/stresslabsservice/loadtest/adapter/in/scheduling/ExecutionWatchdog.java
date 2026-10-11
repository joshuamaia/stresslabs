package br.com.joshua.stresslabsservice.loadtest.adapter.in.scheduling;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.joshua.stresslabsservice.loadtest.application.ExecutionProgressService;

@Component
class ExecutionWatchdog {

    private final ExecutionProgressService progress;

    ExecutionWatchdog(ExecutionProgressService progress) {
        this.progress = progress;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        progress.recoverAfterRestart();
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    void checkStaleExecutions() {
        progress.failStaleExecutions();
    }
}
