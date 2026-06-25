package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.soap.client.common.SoapResult;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.envelope.SoapFault;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultSoapClientMetricsFactory {

    public static final DefaultSoapClientMetricsFactory INSTANCE = new DefaultSoapClientMetricsFactory();

    public DefaultSoapClientMetrics create(DefaultSoapClientTelemetry.TelemetryContext context) {
        return new DefaultSoapClientMetrics(context);
    }

    public static class DefaultSoapClientMetrics {

        public record DurationKey(int httpCode,
                                  String errorType,
                                  String soapFaultCode,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(httpCode, errorType, soapFaultCode, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> requestDurationCache = new ConcurrentHashMap<>();

        protected final DefaultSoapClientTelemetry.TelemetryContext context;

        public DefaultSoapClientMetrics(DefaultSoapClientTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(SoapEnvelope requestEnvelope,
                           @Nullable HttpClientResponse httpResponse,
                           SoapResult.@Nullable Failure failure,
                           @Nullable Throwable exception,
                           long processingTimeNanos) {
            var key = createMetricClientDurationKey(requestEnvelope, httpResponse, failure, exception);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricClientDuration(key, requestEnvelope, httpResponse, failure, exception).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createMetricClientDurationKey(SoapEnvelope requestEnvelope,
                                                            @Nullable HttpClientResponse httpResponse,
                                                            SoapResult.@Nullable Failure failure,
                                                            @Nullable Throwable exception) {
            if (exception instanceof CompletionException ce && ce.getCause() != null) {
                exception = ce.getCause();
            }
            var responseCode = httpResponse != null
                ? httpResponse.code()
                : -1;
            var fault = failure == null
                ? null
                : failure.fault();
            var faultCode = fault != null && fault.getFaultcode() != null
                ? fault.getFaultcode().toString()
                : "";
            if (exception != null) {
                return new DurationKey(responseCode, exception.getClass().getCanonicalName(), faultCode, null);
            }
            if (fault != null && fault.getDetail() != null && fault.getDetail().getAny() != null && !fault.getDetail().getAny().isEmpty()) {
                var faultDetail = fault.getDetail().getAny().getFirst();
                return new DurationKey(responseCode, faultDetail.getClass().getCanonicalName(), faultCode, null);
            }
            return new DurationKey(responseCode, "", faultCode, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricClientDuration(DurationKey metricKey,
                                                           @Nullable SoapEnvelope requestEnvelope,
                                                           @Nullable HttpClientResponse httpResponse,
                                                           SoapResult.Failure failure,
                                                           @Nullable Throwable throwable) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var staticTags = new ArrayList<Tag>(11 + this.context.config().metrics().tags().size() + extraTags);
            var descriptor = this.context.descriptor();
            var uri = URI.create(this.context.url());
            var port = DefaultSoapClientTelemetry.getPort(uri);

            staticTags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), "soap"));
            staticTags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), descriptor.service()));
            staticTags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), descriptor.method()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), uri.getHost()));
            staticTags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), Integer.toString(port)));
            staticTags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(metricKey.httpCode())));
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), metricKey.errorType()));
            staticTags.add(Tag.of(DefaultSoapClientObservation.FAULT_CODE.getKey(), metricKey.soapFaultCode()));
            staticTags.add(Tag.of(DefaultSoapClientTelemetry.SYSTEM_CONFIG_PATH, this.context.clientConfigPath()));
            staticTags.add(Tag.of(DefaultSoapClientTelemetry.SYSTEM_NAME_SIMPLE, this.context.clientSimpleName()));
            staticTags.add(Tag.of(DefaultSoapClientTelemetry.SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName()));

            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Timer.builder("rpc.client.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(staticTags));
        }
    }
}
