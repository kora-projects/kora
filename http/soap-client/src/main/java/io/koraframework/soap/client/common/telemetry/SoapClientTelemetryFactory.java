package io.koraframework.soap.client.common.telemetry;

import io.koraframework.soap.client.common.SoapMethodDescriptor;
import io.koraframework.soap.client.common.SoapServiceConfig;

public interface SoapClientTelemetryFactory {

    SoapClientTelemetry get(SoapServiceConfig.SoapClientTelemetryConfig config, SoapMethodDescriptor descriptor, String url);
}
