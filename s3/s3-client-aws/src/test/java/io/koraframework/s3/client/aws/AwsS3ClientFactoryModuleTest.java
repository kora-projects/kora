package io.koraframework.s3.client.aws;

import io.koraframework.application.graph.All;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryConfig;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryFactory;
import io.koraframework.s3.client.aws.telemetry.impl.NoopAwsS3ClientTelemetry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AwsS3ClientFactoryModuleTest {

    private final AtomicInteger observed = new AtomicInteger();
    private final AtomicInteger intercepted = new AtomicInteger();

    @Test
    void telemetryInterceptorRegisteredWithoutExecutionInterceptors() {
        deleteObject(All.of());

        assertThat(observed).hasValue(1);
    }

    @Test
    void telemetryInterceptorRegisteredWithExecutionInterceptors() {
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
                intercepted.incrementAndGet();
            }
        };

        deleteObject(All.of(interceptor));

        assertThat(observed).hasValue(1);
        assertThat(intercepted).hasValue(1);
    }

    private void deleteObject(All<ExecutionInterceptor> interceptors) {
        var config = new AwsS3Config() {
            @Override
            public String url() {
                return "http://localhost:9000";
            }

            @Override
            public S3Credentials credentials() {
                return new S3Credentials() {
                    @Override
                    public String accessKey() {
                        return "access";
                    }

                    @Override
                    public String secretKey() {
                        return "secret";
                    }
                };
            }

            @Override
            public AwsS3ClientTelemetryConfig telemetry() {
                return null;
            }
        };
        AwsS3ClientTelemetryFactory telemetryFactory = (path, type, cfg) -> (operation, bucket) -> {
            observed.incrementAndGet();
            return NoopAwsS3ClientTelemetry.INSTANCE.observe(operation, bucket);
        };

        var module = new AwsS3ClientFactoryModule("s3");
        var factory = module.awsS3ClientFactory(new NoContentHttpClient(), module.awsS3credentialsProvider(config),
            module.awsS3Configuration(config), telemetryFactory, interceptors);
        try (var client = factory.create(config)) {
            client.deleteObject(DeleteObjectRequest.builder().bucket("bucket").key("key").build());
        }
    }

    private static final class NoContentHttpClient implements SdkHttpClient {

        @Override
        public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
            return new ExecutableHttpRequest() {
                @Override
                public HttpExecuteResponse call() {
                    return HttpExecuteResponse.builder()
                        .response(SdkHttpResponse.builder().statusCode(204).build())
                        .build();
                }

                @Override
                public void abort() {}
            };
        }

        @Override
        public void close() {}
    }
}
