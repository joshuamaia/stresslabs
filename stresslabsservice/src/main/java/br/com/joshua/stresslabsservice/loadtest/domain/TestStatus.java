package br.com.joshua.stresslabsservice.loadtest.domain;

public enum TestStatus {
    PENDING, RUNNING, COMPLETED, STOPPED, FAILED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }

    public boolean isFinal() {
        return !isActive();
    }
}
