package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.logging.logback.KoraAsyncAppender;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class LogbackJsonLoggingLifecycle implements Lifecycle {

    private final List<LoggingEventJsonWriter> writers;

    private Appender<ILoggingEvent> appender;
    private List<BootstrapJsonLogbackRecordEncoder> bootstrapEncoders = List.of();

    public LogbackJsonLoggingLifecycle(LoggingEventJsonWriterProvider configuration) {
        this.writers = List.copyOf(configuration.get());
    }

    @Override
    public void init() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);

        var encoder = new JsonLogbackRecordEncoder(this.writers);
        encoder.setContext(context);
        encoder.start();

        var consoleAppender = new ConsoleAppender<ILoggingEvent>();
        consoleAppender.setContext(context);
        consoleAppender.setName("KORA_JSON_CONSOLE");
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        var asyncAppender = new KoraAsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName("KORA_JSON_ASYNC");
        asyncAppender.addAppender(consoleAppender);
        asyncAppender.start();

        var bootstrapAppenders = this.findBootstrapAppenders(root);
        root.addAppender(asyncAppender);
        for (var bootstrapAppender : bootstrapAppenders) {
            bootstrapAppender.encoder().disable();
        }
        for (var bootstrapAppender : bootstrapAppenders) {
            root.detachAppender(bootstrapAppender.appender());
        }

        this.appender = asyncAppender;
        this.bootstrapEncoders = bootstrapAppenders.stream()
            .map(BootstrapAppender::encoder)
            .toList();
    }

    @Override
    public void release() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (this.appender != null) {
            root.detachAppender(this.appender);
            this.appender.stop();
            this.appender = null;
        }
        for (var bootstrapEncoder : this.bootstrapEncoders) {
            bootstrapEncoder.enable();
        }
    }

    private List<BootstrapAppender> findBootstrapAppenders(Logger root) {
        var result = new ArrayList<BootstrapAppender>();
        var visited = new HashSet<Appender<ILoggingEvent>>();
        var appenders = root.iteratorForAppenders();
        while (appenders.hasNext()) {
            var appender = appenders.next();
            this.findBootstrapAppenders(result, visited, appender, appender);
        }
        return result;
    }

    private void findBootstrapAppenders(List<BootstrapAppender> result,
                                        Set<Appender<ILoggingEvent>> visited,
                                        Appender<ILoggingEvent> rootAppender,
                                        Appender<ILoggingEvent> appender) {
        if (!visited.add(appender)) {
            return;
        }
        if (appender instanceof OutputStreamAppender<?> outputStreamAppender
            && outputStreamAppender.getEncoder() instanceof BootstrapJsonLogbackRecordEncoder encoder) {
            result.add(new BootstrapAppender(rootAppender, encoder));
        }
        if (appender instanceof AppenderAttachable<?> appenderAttachable) {
            @SuppressWarnings("unchecked")
            var typedAttachable = (AppenderAttachable<ILoggingEvent>) appenderAttachable;
            var nestedAppenders = typedAttachable.iteratorForAppenders();
            while (nestedAppenders.hasNext()) {
                this.findBootstrapAppenders(result, visited, rootAppender, nestedAppenders.next());
            }
        }
    }

    private record BootstrapAppender(Appender<ILoggingEvent> appender, BootstrapJsonLogbackRecordEncoder encoder) {}
}
