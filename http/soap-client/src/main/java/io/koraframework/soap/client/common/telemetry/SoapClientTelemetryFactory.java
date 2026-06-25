package io.koraframework.soap.client.common.telemetry;

import io.koraframework.soap.client.common.SoapMethodDescriptor;

public interface SoapClientTelemetryFactory {

    SoapClientTelemetry get(String clientConfigPath,
                            String clientCanonicalName,
                            SoapClientTelemetryConfig config,
                            SoapMethodDescriptor descriptor,
                            String url);
}
