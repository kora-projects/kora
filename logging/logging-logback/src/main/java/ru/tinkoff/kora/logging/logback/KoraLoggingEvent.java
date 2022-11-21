package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.util.List;
import java.util.Map;

public record KoraLoggingEvent(
    String threadName,
    String loggerName,
    LoggerContextVO loggerContextVO,
    Level level,
    String message,
    String formattedMessage,
    Object[] argumentArray,
    IThrowableProxy throwableProxy,
    List<Marker> markerList,
    Map<String, String> mdcPropertyMap,
    long timeStamp,
    int nanoseconds,
    long sequenceNumber,
    List<KeyValuePair> keyValuePairs,
    Map<String, StructuredArgumentWriter> koraMdc
) implements ILoggingEvent {
    @Override
    public String getThreadName() {
        return this.threadName;
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    @Override
    public Object[] getArgumentArray() {
        return this.argumentArray;
    }

    @Override
    public String getFormattedMessage() {
        return this.formattedMessage;
    }

    @Override
    public String getLoggerName() {
        return this.loggerName;
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
        return this.loggerContextVO;
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
        return this.throwableProxy;
    }

    @Override
    public StackTraceElement[] getCallerData() {
        return null;
    }

    @Override
    public boolean hasCallerData() {
        return false;
    }

    @Override
    public List<Marker> getMarkerList() {
        return this.markerList;
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
        return this.mdcPropertyMap;
    }

    @Override
    public Map<String, String> getMdc() {
        return this.mdcPropertyMap;
    }

    @Override
    public long getTimeStamp() {
        return this.timeStamp;
    }

    @Override
    public int getNanoseconds() {
        return this.nanoseconds;
    }

    @Override
    public long getSequenceNumber() {
        return this.sequenceNumber;
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        return this.keyValuePairs;
    }

    @Override
    public void prepareForDeferredProcessing() {
    }
}
