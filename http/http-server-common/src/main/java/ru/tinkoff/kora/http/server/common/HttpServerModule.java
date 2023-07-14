package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.form.FormUrlEncoded;
import ru.tinkoff.kora.http.server.common.form.FormMultipartServerRequestMapper;
import ru.tinkoff.kora.http.server.common.form.FormUrlEncodedServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseEntityMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.*;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public interface HttpServerModule extends StringParameterReadersModule {

    default HttpServerConfig httpServerConfig(Config config, ConfigValueExtractor<HttpServerConfig> configValueExtractor) {
        return configValueExtractor.extract(config.get("httpServer"));
    }

    @DefaultComponent
    default DefaultHttpServerTelemetry defaultHttpServerTelemetry(@Nullable HttpServerMetrics metricWriter, @Nullable HttpServerLogger logger, @Nullable HttpServerTracer tracer) {
        return new DefaultHttpServerTelemetry(metricWriter, logger, tracer);
    }

    default PrivateApiHandler privateApiHandler(ValueOf<HttpServerConfig> config,
                                                ValueOf<Optional<PrivateApiMetrics>> meterRegistry,
                                                All<PromiseOf<ReadinessProbe>> readinessProbes,
                                                All<PromiseOf<LivenessProbe>> livenessProbes) {
        return new PrivateApiHandler(config, meterRegistry, readinessProbes, livenessProbes);
    }

    default PublicApiHandler publicApiHandler(All<ValueOf<HttpServerRequestHandler>> handlers,
                                              @Tag(HttpServerModule.class) All<ValueOf<HttpServerInterceptor>> interceptors,
                                              ValueOf<HttpServerTelemetry> telemetry,
                                              ValueOf<HttpServerConfig> httpServerConfig) {
        return new PublicApiHandler(handlers, interceptors, telemetry, httpServerConfig);
    }

    default HttpServerLogger httpServerLogger() {
        return new Slf4jHttpServerLogger();
    }

    default HttpServerResponseMapper<HttpServerResponse> noopResponseMapper() {
        return Mono::just;
    }

    default HttpServerRequestMapper<HttpServerRequest> noopRequestMapper() {
        return Mono::just;
    }

    default HttpServerRequestMapper<ByteBuffer> byteBufBodyRequestMapper() {
        return r -> ReactorUtils.toByteBufferMono(r.body());
    }

    default HttpServerRequestMapper<byte[]> byteArrayRequestMapper() {
        return (request) -> ReactorUtils.toByteArrayMono(request.body());
    }

    default HttpServerResponseMapper<ByteBuffer> byteBufBodyResponseMapper() {
        return r -> Mono.just(new SimpleHttpServerResponse(200, "application/octet-stream", HttpHeaders.EMPTY, r));
    }

    default HttpServerResponseMapper<byte[]> byteArrayResponseMapper() {
        return r -> Mono.just(new SimpleHttpServerResponse(200, "application/octet-stream", HttpHeaders.EMPTY, ByteBuffer.wrap(r)));
    }

    default HttpServerResponseMapper<String> stringResponseMapper() {
        return r -> Mono.just(new SimpleHttpServerResponse(200, "text/plain; charset=utf-8", HttpHeaders.EMPTY, ByteBuffer.wrap(r.getBytes(StandardCharsets.UTF_8))));
    }

    default <T> HttpServerResponseMapper<HttpServerResponseEntity<T>> httpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        return new HttpServerResponseEntityMapper<>(delegate);
    }

    default HttpServerRequestMapper<FormUrlEncoded> formUrlEncoderHttpServerRequestMapper() {
        return new FormUrlEncodedServerRequestMapper();
    }

    default FormMultipartServerRequestMapper formMultipartServerRequestMapper() {
        return new FormMultipartServerRequestMapper();
    }
}
