package io.koraframework.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import io.opentelemetry.api.trace.Span;
import io.koraframework.logging.common.MDC;

import java.time.Duration;
import java.util.ArrayList;
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

    @Override
    protected void append(ILoggingEvent eventObject) {
        var koraLoggingEvent = new KoraLoggingEvent(
            eventObject.getThreadName(),
            eventObject.getLoggerName(),
            eventObject.getLoggerContextVO(),
            eventObject.getLevel(),
            eventObject.getMessage(),
            eventObject.getFormattedMessage(),
            eventObject.getArgumentArray(),
            eventObject.getThrowableProxy(),
            eventObject.getMarkerList(),
            eventObject.getMDCPropertyMap(),
            eventObject.getTimeStamp(),
            eventObject.getNanoseconds(),
            eventObject.getSequenceNumber(),
            eventObject.getKeyValuePairs(),
            // an event logged outside a request or message scope has no MDC bound, and reading an
            // unbound ScopedValue would throw here and make the appender drop the event
            MDC.VALUE.isBound() ? Map.copyOf(MDC.get().values()) : Map.of(),
            Span.current().getSpanContext()
        );
        super.append(koraLoggingEvent);
    }
}
