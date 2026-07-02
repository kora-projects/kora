package io.koraframework.http.client.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import io.koraframework.config.common.mapper.*;
import io.koraframework.config.common.util.ConfigMappingUtils;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.declarative.*;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.telemetry.*;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                invocation.getArgument(0, HttpClientRequest.class).body().close();
                return responseConsumer.apply(TestHttpClientResponse.response(200));
            });
    }

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.http.client.common.annotation.*;
            import io.koraframework.http.client.common.request.*;
            import io.koraframework.http.client.common.response.*;
            import io.koraframework.http.client.common.*;
            import io.koraframework.http.common.annotation.*;
            import io.koraframework.http.common.header.*;
            import io.koraframework.http.client.common.annotation.HttpClient;
            import io.koraframework.http.client.common.interceptor.HttpClientInterceptor;
            import io.koraframework.http.client.common.interceptor.HttpClientInterceptor.InterceptChain;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            """;
    }

    protected TestObject compileClient(List<Object> arguments, @Language("java") String... sources) {
        compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }
        compileResult.warnings().forEach(System.out::println);

        var clientClass = compileResult.loadClass("$TestClient_ClientImpl");
        var durationCVE = new DurationConfigValueMapper();
        var telemetryCVE = new $HttpClientTelemetryConfig_ConfigValueMapper(
            new $HttpClientTelemetryConfig_HttpClientLoggingConfig_ConfigValueMapper(new SetConfigValueMapper<>(new StringConfigValueMapper()), new SizeConfigValueMapper()),
            new $HttpClientTelemetryConfig_HttpClientMetricsConfig_ConfigValueMapper(new DurationArrayConfigValueMapper(new DurationConfigValueMapper()), new MapConfigValueMapper<>(new StringConfigValueMapper())),
            new $HttpClientTelemetryConfig_HttpClientTracingConfig_ConfigValueMapper(new MapConfigValueMapper<>(new StringConfigValueMapper()))
        );
        var operationTelemetryCVE = new $HttpClientOperationConfig_OperationTelemetryConfig_ConfigValueMapper(
            new $HttpClientOperationConfig_OperationTelemetryConfig_LoggingConfig_ConfigValueMapper(new SetConfigValueMapper<>(new StringConfigValueMapper()), new SizeConfigValueMapper()),
            new $HttpClientOperationConfig_OperationTelemetryConfig_TracingConfig_ConfigValueMapper(new MapConfigValueMapper<>(new StringConfigValueMapper())),
            new $HttpClientOperationConfig_OperationTelemetryConfig_MetricsConfig_ConfigValueMapper(new DurationArrayConfigValueMapper(new DurationConfigValueMapper()), new MapConfigValueMapper<>(new StringConfigValueMapper()))
        );
        var operationConfigCVE = new $HttpClientOperationConfig_ConfigValueMapper(durationCVE, operationTelemetryCVE);

        var configValueExtractor = (ConfigValueMapper<?>) newObject("$TestClient_Config_ConfigValueMapper", telemetryCVE, operationConfigCVE, durationCVE);
        var config = configValueExtractor.map(ConfigMappingUtils.fromMap(Map.of(
            "url", "http://test-url:8080"
        )).root());


        var realArgs = new ArrayList<>(arguments);
        realArgs.add(0, httpClient);
        realArgs.add(1, config);
        realArgs.add(2, telemetryFactory);
        return this.client = new TestObject(clientClass, realArgs);
    }
}
