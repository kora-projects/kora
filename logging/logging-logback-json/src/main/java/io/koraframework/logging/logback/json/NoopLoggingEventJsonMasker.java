package io.koraframework.logging.logback.json;

final class NoopLoggingEventJsonMasker implements LoggingEventJsonMasker {

    static final NoopLoggingEventJsonMasker INSTANCE = new NoopLoggingEventJsonMasker();

    private NoopLoggingEventJsonMasker() {}

    @Override
    public boolean shouldMask(String path, String fieldName) {
        return false;
    }
}
