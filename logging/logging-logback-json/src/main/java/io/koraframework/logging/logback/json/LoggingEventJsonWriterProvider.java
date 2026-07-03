package io.koraframework.logging.logback.json;

import java.util.List;

@FunctionalInterface
public interface LoggingEventJsonWriterProvider {

    List<LoggingEventJsonWriter> get();
}
