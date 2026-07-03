package io.koraframework.logging.logback.json.writer;

import java.util.List;

@FunctionalInterface
public interface LoggingEventJsonWriterProvider {

    List<LoggingEventJsonWriter> get();
}
