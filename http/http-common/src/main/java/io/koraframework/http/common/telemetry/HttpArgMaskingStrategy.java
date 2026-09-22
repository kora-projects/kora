package io.koraframework.http.common.telemetry;

@FunctionalInterface
public interface HttpArgMaskingStrategy {

    String mask(String key, Object value);
}
