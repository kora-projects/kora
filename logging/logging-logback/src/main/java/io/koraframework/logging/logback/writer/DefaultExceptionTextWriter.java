package io.koraframework.logging.logback.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;

/**
 * Writes the stack trace of a record on the lines following it, and nothing when there is none.
 */
public final class DefaultExceptionTextWriter implements LoggingEventTextWriter {

    @Override
    public void write(StringBuilder out, ILoggingEvent event) {
        var throwable = event.getThrowableProxy();
        if (throwable != null) {
            out.append('\n').append(ThrowableProxyUtil.asString(throwable));
        }
    }
}
