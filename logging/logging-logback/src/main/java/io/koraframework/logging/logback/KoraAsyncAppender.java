package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import io.opentelemetry.api.trace.Span;
import io.koraframework.logging.common.MDC;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Asynchronous appender enriching every event with the Kora MDC and the current span before queueing it.
 * <p>
 * Its defaults favour the application over the log: a bounded queue of {@value #DEFAULT_QUEUE_SIZE} events that never
 * blocks a logging thread when full, and a shutdown that waits at most a second for the queue to be flushed, so a stalled output can not hold the process from exiting. Each setting is
 * read from a system property or an environment variable, see {@link KoraLogbackProperties}, and a value set in a
 * Logback configuration file overrides both:
 * <ul>
 *     <li>{@value #QUEUE_SIZE_PROPERTY}, {@code KORA_LOGGING_CONFIG_QUEUE_SIZE}: capacity of the queue</li>
 *     <li>{@value #DISCARDING_THRESHOLD_PROPERTY}, {@code KORA_LOGGING_CONFIG_DISCARDING_THRESHOLD}: remaining
 *     capacity below which {@code TRACE}, {@code DEBUG} and {@code INFO} events are dropped, a fifth of the queue by
 *     default and {@code 0} to never drop by level</li>
 *     <li>{@value #MAX_FLUSH_TIME_PROPERTY}, {@code KORA_LOGGING_CONFIG_MAX_FLUSH_TIME}: how long to wait for the
 *     queue to be flushed on shutdown, as a duration such as {@code 1s}, {@code 500ms} or {@code PT1S}, see
 *     {@link KoraLogbackProperties#getDuration}; {@code 0} waits until it is, however long a stalled output takes</li>
 *     <li>{@value #NEVER_BLOCK_PROPERTY}, {@code KORA_LOGGING_CONFIG_NEVER_BLOCK}: whether an event is dropped rather
 *     than blocking the logging thread when the queue is full</li>
 * </ul>
 */
public final class KoraAsyncAppender extends AsyncAppenderBase<ILoggingEvent> {

    public static final String QUEUE_SIZE_PROPERTY = "kora.logging.config.queue-size";
    public static final String DISCARDING_THRESHOLD_PROPERTY = "kora.logging.config.discarding-threshold";
    public static final String MAX_FLUSH_TIME_PROPERTY = "kora.logging.config.max-flush-time";
    public static final String NEVER_BLOCK_PROPERTY = "kora.logging.config.never-block";

    public static final int DEFAULT_QUEUE_SIZE = 512;
    public static final Duration DEFAULT_MAX_FLUSH_TIME = Duration.ofSeconds(1);
    public static final boolean DEFAULT_NEVER_BLOCK = true;

    /** Lets Logback derive the threshold from the queue size, a fifth of it. */
    private static final int DEFAULT_DISCARDING_THRESHOLD = -1;

    // the context is not known yet while constructing, so problems are kept until start can report them
    private final List<String> settingWarnings = new ArrayList<>();

    public KoraAsyncAppender() {
        this.setNeverBlock(KoraLogbackProperties.getBoolean(NEVER_BLOCK_PROPERTY, DEFAULT_NEVER_BLOCK, this.settingWarnings::add));
        this.setQueueSize(KoraLogbackProperties.getInt(QUEUE_SIZE_PROPERTY, DEFAULT_QUEUE_SIZE, 1, this.settingWarnings::add));
        var maxFlushTime = KoraLogbackProperties.getDuration(MAX_FLUSH_TIME_PROPERTY, DEFAULT_MAX_FLUSH_TIME, this.settingWarnings::add);
        // Logback keeps this in milliseconds as an int
        this.setMaxFlushTime((int) Math.min(maxFlushTime.toMillis(), Integer.MAX_VALUE));
        this.setDiscardingThreshold(KoraLogbackProperties.getInt(DISCARDING_THRESHOLD_PROPERTY, DEFAULT_DISCARDING_THRESHOLD, 0, this.settingWarnings::add));
    }

    @Override
    public void start() {
        for (var warning : this.settingWarnings) {
            this.addWarn(warning);
        }
        this.settingWarnings.clear();
        super.start();
    }

    /**
     * Lets {@code TRACE}, {@code DEBUG} and {@code INFO} events be dropped once the queue is below the discarding
     * threshold, as {@link ch.qos.logback.classic.AsyncAppender} does; the base appender never drops any.
     */
    @Override
    protected boolean isDiscardable(ILoggingEvent event) {
        return event.getLevel().toInt() <= Level.INFO_INT;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        var koraLoggingEvent = new KoraLoggingEvent(
            eventObject.getThreadName(),
            eventObject.getLoggerName(),
            eventObject.getLoggerContextVO(),
            eventObject.getLevel(),
            eventObject.getMessage(),
            eventObject.getFormattedMessage(),
            renderArguments(eventObject.getArgumentArray()),
            eventObject.getThrowableProxy(),
            renderMarkers(eventObject.getMarkerList()),
            eventObject.getMDCPropertyMap(),
            eventObject.getTimeStamp(),
            eventObject.getNanoseconds(),
            eventObject.getSequenceNumber(),
            renderKeyValuePairs(eventObject.getKeyValuePairs()),
            // an event logged outside a request or message scope has no MDC bound, and reading an
            // unbound ScopedValue would throw here and make the appender drop the event
            MDC.VALUE.isBound() ? renderMdc(MDC.get().values()) : Map.of(),
            Span.current().getSpanContext()
        );
        super.append(koraLoggingEvent);
    }

    // Structured arguments are lazy writers over the logged objects, which the logging thread is free to change once
    // the event is queued, so they are rendered here and only replayed on the worker thread

    private static @Nullable List<Marker> renderMarkers(@Nullable List<Marker> markers) {
        if (markers == null) {
            return null;
        }
        var result = new ArrayList<Marker>(markers.size());
        for (var marker : markers) {
            result.add(marker instanceof StructuredArgument argument
                ? StructuredArgument.marker(argument.fieldName(), render(argument))
                : marker);
        }
        return result;
    }

    private static @Nullable Object @Nullable [] renderArguments(@Nullable Object @Nullable [] arguments) {
        if (arguments == null) {
            return null;
        }
        var result = arguments.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] instanceof StructuredArgument argument) {
                result[i] = StructuredArgument.arg(argument.fieldName(), render(argument));
            }
        }
        return result;
    }

    private static @Nullable List<KeyValuePair> renderKeyValuePairs(@Nullable List<KeyValuePair> keyValuePairs) {
        if (keyValuePairs == null) {
            return null;
        }
        var result = new ArrayList<KeyValuePair>(keyValuePairs.size());
        for (var pair : keyValuePairs) {
            result.add(pair.value instanceof StructuredArgumentWriter writer
                ? new KeyValuePair(pair.key, render(writer))
                : pair);
        }
        return result;
    }

    // values put by MDC's typed overloads can not change, so the immutable map is kept as it is unless a custom writer
    // in it has to be rendered
    private static Map<String, StructuredArgumentWriter> renderMdc(Map<String, StructuredArgumentWriter> mdc) {
        Map<String, StructuredArgumentWriter> result = null;
        for (var entry : mdc.entrySet()) {
            if (!(entry.getValue() instanceof MDC.ImmutableWriter)) {
                if (result == null) {
                    result = new HashMap<>(mdc);
                }
                result.put(entry.getKey(), render(entry.getValue()));
            }
        }
        return result == null ? mdc : Map.copyOf(result);
    }

    // reads only JSON a writer has just written, so it accepts everything the generator can write
    private static final JsonFactory REPLAY_FACTORY = JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxNumberLength(Integer.MAX_VALUE)
            .maxNameLength(Integer.MAX_VALUE)
            .maxStringLength(Integer.MAX_VALUE)
            .maxNestingDepth(Integer.MAX_VALUE)
            .maxDocumentLength(-1)
            .maxTokenCount(-1)
            .build())
        .build();

    private static StructuredArgumentWriter render(StructuredArgumentWriter writer) {
        String json;
        try {
            json = writer.writeToString();
        } catch (RuntimeException e) {
            // left for the encoder, which reports a failing writer in the record instead of losing the whole event
            return writer;
        }
        // replayed token by token through the generator's own write methods rather than written raw or copied, so an
        // encoder wrapping it still sees every name and value: it can merge several data objects into one and mask fields
        return gen -> {
            try (var parser = REPLAY_FACTORY.createParser(ObjectReadContext.empty(), json)) {
                for (var token = parser.nextToken(); token != null; token = parser.nextToken()) {
                    switch (token) {
                        case START_OBJECT -> gen.writeStartObject();
                        case END_OBJECT -> gen.writeEndObject();
                        case START_ARRAY -> gen.writeStartArray();
                        case END_ARRAY -> gen.writeEndArray();
                        case PROPERTY_NAME -> gen.writeName(parser.currentName());
                        case VALUE_STRING -> gen.writeString(parser.getString());
                        // as the writer wrote them, without a round trip through double
                        case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> gen.writeNumber(parser.getString());
                        case VALUE_TRUE -> gen.writeBoolean(true);
                        case VALUE_FALSE -> gen.writeBoolean(false);
                        case VALUE_NULL -> gen.writeNull();
                        default -> gen.copyCurrentEvent(parser);
                    }
                }
            }
        };
    }
}
