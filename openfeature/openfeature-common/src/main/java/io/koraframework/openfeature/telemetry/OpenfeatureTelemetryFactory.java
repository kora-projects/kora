package io.koraframework.openfeature.telemetry;

public interface OpenfeatureTelemetryFactory {

    OpenfeatureTelemetry get(String clientConfigPath, String clientCanonicalName, OpenfeatureTelemetryConfig config);
}
