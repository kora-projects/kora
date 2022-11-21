package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import ru.tinkoff.kora.logging.common.MDC;

import java.util.Map;

public final class KoraAsyncAppender extends AsyncAppenderBase<ILoggingEvent> {
    public KoraAsyncAppender() {}

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
            Map.copyOf(eventObject.getMDCPropertyMap()),
            eventObject.getTimeStamp(),
            eventObject.getNanoseconds(),
            eventObject.getSequenceNumber(),
            eventObject.getKeyValuePairs(),
            Map.copyOf(MDC.get().values())
        );
        super.append(koraLoggingEvent);
    }
}
