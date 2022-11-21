package ru.tinkoff.kora.logging.common;

public interface LoggingLevelApplier {
    void apply(String logName, String logLevel);

    void reset();
}
