package ru.tinkoff.kora.soap.client.common.telemetry;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import ru.tinkoff.kora.soap.client.common.SoapMethodDescriptor;
import ru.tinkoff.kora.soap.client.common.SoapServiceConfig;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultSoapClientTelemetry implements SoapClientTelemetry {
    private static final Timer NOOP_TIMER = new NoopTimer(new Meter.Id("noop-timer", Tags.empty(), null, null, Meter.Type.TIMER));
    private final SoapServiceConfig.SoapClientTelemetryConfig config;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final String url;
    private final List<Tag> commonTags;
    private final ConcurrentMap<Tags, Timer> durationCache = new ConcurrentHashMap<>();
    private final SoapMethodDescriptor descriptor;
    private final Logger rqLogger;
    private final Logger rsLogger;

    public DefaultSoapClientTelemetry(SoapServiceConfig.SoapClientTelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry, ILoggerFactory loggerFactory, SoapMethodDescriptor descriptor, String url) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.descriptor = descriptor;
        this.url = url;
        var uri = URI.create(url);
        var host = uri.getHost();
        var scheme = uri.getScheme();
        var port = uri.getPort() != -1 ? uri.getPort() : switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
        this.commonTags = new ArrayList<>();
        this.commonTags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), "soap"));
        this.commonTags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), this.descriptor.service));
        this.commonTags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), this.descriptor.method));
        this.commonTags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), host));
        this.commonTags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), Integer.toString(port)));
        for (var e : this.config.metrics().tags().entrySet()) {
            this.commonTags.add(Tag.of(e.getKey(), e.getValue()));
        }
        this.rqLogger = loggerFactory.getLogger(descriptor.serviceClass + ".request");
        this.rsLogger = loggerFactory.getLogger(descriptor.serviceClass + ".response");

    }


    @Override
    public SoapClientObservation observe(SoapEnvelope requestEnvelope) {
        var span = this.createSpan(requestEnvelope);
        var duration = this.buildDuration();
        return new DefaultSoapClientObservation(this.descriptor, span, duration, this.rqLogger, this.rsLogger);
    }

    protected Span createSpan(SoapEnvelope requestEnvelope) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var builder = this.tracer.spanBuilder("SOAP " + this.descriptor.service + " " + this.descriptor.method)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, this.descriptor.service)
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, this.descriptor.method)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, url);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }
        return builder.startSpan();
    }

    protected Meter.MeterProvider<Timer> buildDuration() {
        if (!this.config.metrics().enabled()) {
            return _ -> NOOP_TIMER;
        }
        return tags -> this.durationCache.computeIfAbsent(Tags.of(tags), t -> Timer.builder("rpc.client.duration")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tags(this.commonTags)
            .tags(t)
            .register(this.meterRegistry));

    }
}
