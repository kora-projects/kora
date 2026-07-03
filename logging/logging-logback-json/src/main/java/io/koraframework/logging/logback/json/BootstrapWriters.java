package io.koraframework.logging.logback.json;

import java.util.List;

final class BootstrapWriters {

    static final List<LoggingEventJsonWriter> DEFAULT = List.of(
        new DefaultEventJsonWriter(),
        new DefaultExceptionJsonWriter(),
        new DefaultStructuredJsonWriter(),
        new DefaultTraceJsonWriter()
    );

    private BootstrapWriters() {}
}
