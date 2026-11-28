package io.koraframework.logging.logback.text.writer;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.pattern.*;
import ch.qos.logback.classic.spi.*;
import io.koraframework.logging.logback.*;

import java.time.*;
import java.time.format.*;
import java.util.function.*;

/**
 * Writes the head of a record: its UTC timestamp, its level padded to five characters, its thread in square brackets
 * and its abbreviated logger name, followed by the separator in front of the message.
 * <p>
 * When colored, the timestamp is highlighted in cyan and the level by severity, the same way
 * {@code %cyan(%d) %highlight(%-5level)} does.
 */
public final class DefaultLoggingEventTextWriter implements LoggingEventTextWriter {

    public static final int DEFAULT_LOGGER_TARGET_LENGTH = 100;
    private static final CachingTimestampFormatter DATE_FORMATTER = new CachingTimestampFormatter(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC)
    );
    private static final boolean EPOCH_MILLIS = KoraLogbackProperties.isTimestampEpochMillis(NoOpConsumer.INSTANCE);

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
        if (EPOCH_MILLIS) {
            var start = out.length();
            out.append(event.getTimeStamp());
            var timestamp = out.substring(start);
            out.setLength(start);
            AnsiColor.CYAN.append(out, timestamp, this.colored);
        } else {
            var timestamp = DATE_FORMATTER.format(event.getTimeStamp());
            AnsiColor.CYAN.append(out, timestamp, this.colored);
        }
        out.append(' ');

        var level = event.getLevel();
        var color = color(level);

        if (this.colored) {
            var start = out.length();
            out.append(level.levelStr);
            if (level.levelStr.length() == 4) {
                out.append(' ');
            }
            var paddedLevel = out.substring(start);
            out.setLength(start);
            color.append(out, paddedLevel, true);
        } else {
            out.append(level.levelStr);
            if (level.levelStr.length() == 4) {
                out.append(' ');
            }
        }
        out.append(' ');

        out.append('[').append(event.getThreadName()).append("] ");
        out.append(this.abbreviator.abbreviate(event.getLoggerName())).append(" - ");
    }

    private static AnsiColor color(Level level) {
        switch (level.toInt()) {
            case Level.ERROR_INT:
                return AnsiColor.BOLD_RED;
            case Level.WARN_INT:
                return AnsiColor.RED;
            case Level.INFO_INT:
                return AnsiColor.BLUE;
            default:
                return AnsiColor.DEFAULT;
        }
    }

    private static final class NoOpConsumer implements Consumer<String> {
        static final NoOpConsumer INSTANCE = new NoOpConsumer();

        @Override
        public void accept(String warning) {
        }
    }
}
