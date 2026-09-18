package io.koraframework.logging.logback.text.writer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.format.DateTimeFormatter;
import io.koraframework.logging.logback.CachingTimestampFormatter;
import io.koraframework.logging.logback.KoraLogbackProperties;

/**
 * Writes the head of a record: its UTC timestamp, its level padded to five characters, its thread in square brackets
 * and its abbreviated logger name, followed by the separator in front of the message.
 * <p>
 * When colored, the timestamp is highlighted in cyan and the level by severity, the same way
 * {@code %cyan(%d) %highlight(%-5level)} does.
 */
public final class DefaultLoggingEventTextWriter implements LoggingEventTextWriter {

    public static final int DEFAULT_LOGGER_TARGET_LENGTH = 100;

    private static final CachingTimestampFormatter DATE_FORMATTER =
        new CachingTimestampFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

    private static final boolean EPOCH_MILLIS = KoraLogbackProperties.isTimestampEpochMillis(warning -> { });

    private final boolean colored;
    private final Abbreviator abbreviator;

    public DefaultLoggingEventTextWriter() {
        this(false);
    }

    public DefaultLoggingEventTextWriter(boolean colored) {
        this(colored, DEFAULT_LOGGER_TARGET_LENGTH);
    }

    public DefaultLoggingEventTextWriter(boolean colored, int loggerTargetLength) {
        this.colored = colored;
        this.abbreviator = new TargetLengthBasedClassNameAbbreviator(loggerTargetLength);
    }

    @Override
    public void write(StringBuilder out, ILoggingEvent event) {
        var timestamp = EPOCH_MILLIS
            ? Long.toString(event.getTimeStamp())
            : DATE_FORMATTER.format(event.getTimeStamp());
        AnsiColor.CYAN.append(out, timestamp, this.colored);
        out.append(' ');

        var level = event.getLevel();
        var paddedLevel = level.levelStr.length() == 4 ? level.levelStr + " " : level.levelStr;
        color(level).append(out, paddedLevel, this.colored);
        out.append(' ');

        out.append('[').append(event.getThreadName()).append("] ");
        out.append(this.abbreviator.abbreviate(event.getLoggerName())).append(" - ");
    }

    private static AnsiColor color(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> AnsiColor.BOLD_RED;
            case Level.WARN_INT -> AnsiColor.RED;
            case Level.INFO_INT -> AnsiColor.BLUE;
            default -> AnsiColor.DEFAULT;
        };
    }
}
