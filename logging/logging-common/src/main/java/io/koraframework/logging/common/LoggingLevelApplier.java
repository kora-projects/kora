package io.koraframework.logging.common;

public interface LoggingLevelApplier {
    void apply(String logName, String logLevel);

    void reset();
}
