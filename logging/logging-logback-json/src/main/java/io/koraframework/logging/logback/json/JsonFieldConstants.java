package io.koraframework.logging.logback.json;

import tools.jackson.core.io.SerializedString;

public final class JsonFieldConstants {

    public static final SerializedString TIMESTAMP = new SerializedString("timestamp");
    public static final SerializedString LEVEL = new SerializedString("level");
    public static final SerializedString THREAD = new SerializedString("thread");
    public static final SerializedString LOGGER = new SerializedString("logger");
    public static final SerializedString MESSAGE = new SerializedString("message");
    public static final SerializedString TRACE_ID = new SerializedString("traceId");
    public static final SerializedString SPAN_ID = new SerializedString("spanId");
    public static final SerializedString MDC = new SerializedString("mdc");
    public static final SerializedString ARGS = new SerializedString("args");
    public static final SerializedString DATA = new SerializedString("data");
    public static final SerializedString EXCEPTION = new SerializedString("exception");
    public static final SerializedString CLASS = new SerializedString("class");
    public static final SerializedString STACK_TRACE = new SerializedString("stackTrace");

    private JsonFieldConstants() { }
}
