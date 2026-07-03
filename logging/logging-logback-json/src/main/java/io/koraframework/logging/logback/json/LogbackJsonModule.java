package io.koraframework.logging.logback.json;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.logging.logback.LogbackModule;

import java.util.List;

public interface LogbackJsonModule extends LogbackModule {

    @Root
    default LogbackJsonLoggingLifecycle logbackJsonLoggingInitializer(LoggingEventJsonWriterProvider configuration) {
        return new LogbackJsonLoggingLifecycle(configuration);
    }

    @DefaultComponent
    default LoggingEventJsonWriterProvider defaultJsonLoggingEventConfiguration(All<LoggingEventJsonWriter> writers) {
        return () -> List.of(
            new DefaultEventJsonWriter(),
            new DefaultTraceJsonWriter(),
            new DefaultStructuredJsonWriter(),
            new DefaultExceptionJsonWriter()
        );
    }
}
