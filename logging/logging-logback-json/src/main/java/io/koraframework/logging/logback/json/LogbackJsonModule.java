package io.koraframework.logging.logback.json;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.logging.logback.LogbackModule;
import io.koraframework.logging.logback.json.writer.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface LogbackJsonModule extends LogbackModule {

    @Root
    default LogbackJsonLoggingLifecycle logbackJsonLoggingInitializer(LoggingEventJsonWriterProvider writerProvider,
                                                                      @Nullable LoggingEventJsonMasker eventMasker) {
        var masker = eventMasker == null ? LoggingEventJsonMasker.noop() : eventMasker;
        return new LogbackJsonLoggingLifecycle(writerProvider.get(), masker);
    }

    @DefaultComponent
    default LoggingEventJsonWriterProvider defaultJsonLoggingEventConfiguration(All<LoggingEventJsonWriter> writers) {
        var result = new ArrayList<>(List.of(
            new DefaultLoggingEventJsonWriter(),
            new DefaultTraceJsonWriterLogging(),
            new DefaultStructuredJsonWriterLogging(),
            new DefaultExceptionJsonWriterLogging()
        ));
        writers.forEach(result::add);
        return () -> result;
    }
}
