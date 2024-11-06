package ru.tinkoff.kora.http.client.annotation.processor;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import ru.tinkoff.kora.config.common.extractor.BooleanConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.DoubleArrayConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.DurationConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.SetConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.StringConfigValueExtractor;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.declarative.$HttpClientOperationConfig_ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.telemetry.*;
import ru.tinkoff.kora.telemetry.common.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractHttpClientTest extends AbstractAnnotationProcessorTest {
    protected HttpClient httpClient = mock(HttpClient.class);
    protected HttpClientTelemetry telemetry = mock(HttpClientTelemetry.class);
    protected HttpClientTelemetryFactory telemetryFactory = mock(HttpClientTelemetryFactory.class);
    protected TestObject client;

    @BeforeEach
    public void init() {
        Mockito.reset(httpClient, telemetry, telemetryFactory);
    }

    protected void onRequest(String method, String path, Function<TestHttpClientResponse, TestHttpClientResponse> responseConsumer) {
        when(httpClient.execute(Mockito.argThat(argument -> argument.method().equalsIgnoreCase(method) && argument.uri().toString().equalsIgnoreCase(path))))
            .thenAnswer(invocation -> {
                var f = new CompletableFuture<Void>();
                invocation.getArgument(0, HttpClientRequest.class).body().subscribe(new Flow.Subscriber<ByteBuffer>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        f.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        f.complete(null);
                    }
                });
                f.get();
                return CompletableFuture.completedFuture(responseConsumer.apply(TestHttpClientResponse.response(200)));
            });
    }

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.http.client.common.annotation.*;
            import ru.tinkoff.kora.http.client.common.request.*;
            import ru.tinkoff.kora.http.client.common.response.*;
            import ru.tinkoff.kora.http.client.common.*;
            import ru.tinkoff.kora.http.common.annotation.*;
            import ru.tinkoff.kora.http.common.header.*;
            import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
            import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor;
            import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor.InterceptChain;
            import reactor.core.publisher.Mono;
            import reactor.core.publisher.Flux;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            """;
    }

    protected TestObject compileClient(List<Object> arguments, @Language("java") String... sources) {
        compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        var clientClass = compileResult.loadClass("$TestClient_ClientImpl");
        var durationCVE = new DurationConfigValueExtractor();
//        var telemetryCVE = new $TelemetryConfig_ConfigValueExtractor(
//            new $TelemetryConfig_LogConfig_ConfigValueExtractor(new BooleanConfigValueExtractor()),
//            new $TelemetryConfig_TracingConfig_ConfigValueExtractor(new BooleanConfigValueExtractor()),
//            new $TelemetryConfig_MetricsConfig_ConfigValueExtractor(new BooleanConfigValueExtractor(), new DoubleArrayConfigValueExtractor(c -> c.asNumber().doubleValue()))
//        );
        var telemetryCVE = (ConfigValueExtractor) new $HttpClientTelemetryConfig_ConfigValueExtractor(
            new $HttpClientLoggerConfig_ConfigValueExtractor(new SetConfigValueExtractor<>(new StringConfigValueExtractor()), new BooleanConfigValueExtractor()),
            new $TelemetryConfig_TracingConfig_ConfigValueExtractor(new BooleanConfigValueExtractor()),
            new $TelemetryConfig_MetricsConfig_ConfigValueExtractor(new BooleanConfigValueExtractor(), new DoubleArrayConfigValueExtractor(c -> c.asNumber().doubleValue()))
        );
        var configCVE = new $HttpClientOperationConfig_ConfigValueExtractor(durationCVE, telemetryCVE);

        var configValueExtractor = (ConfigValueExtractor<?>) newObject("$$TestClient_Config_ConfigValueExtractor", telemetryCVE, durationCVE, configCVE);
        var config = configValueExtractor.extract(MapConfigFactory.fromMap(Map.of(
            "url", "http://test-url:8080"
        )).root());


        var realArgs = new ArrayList<>(arguments);
        realArgs.add(0, httpClient);
        realArgs.add(1, config);
        realArgs.add(2, telemetryFactory);
        return this.client = new TestObject(clientClass, realArgs);
    }
}
