package io.koraframework.s3.client.aws;

import io.koraframework.application.graph.All;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryFactory;
import io.koraframework.s3.client.aws.telemetry.DefaultAwsS3ClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.AwsClient;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

public interface AwsS3ClientModule {

    default AwsS3Config awsS3Config(Config config, ConfigValueExtractor<AwsS3Config> extractor) {
        var value = config.get("s3client.aws");
        return extractor.extract(value);
    }

    @Tag(AwsClient.class)
    @DefaultComponent
    default HttpClient awsS3httpClient(HttpClient client) {
        return client;
    }

    @DefaultComponent
    default KoraAwsSdkHttpClient awsS3koraSdkHttpClient(@Tag(AwsClient.class) HttpClient client,
                                                        AwsS3Config clientConfig) {
        return new KoraAwsSdkHttpClient(client, clientConfig);
    }

    @DefaultComponent
    default S3Configuration awsS3Configuration(AwsS3Config config) {
        return S3Configuration.builder()
            .checksumValidationEnabled(config.checksumValidationEnabled())
            .chunkedEncodingEnabled(config.chunkedEncodingEnabled())
            .pathStyleAccessEnabled(config.addressStyle() == AwsS3Config.AddressStyle.PATH)
            .build();
    }

    @DefaultComponent
    default AwsCredentialsProvider awsS3credentialsProvider(AwsS3Config config) {
        return () -> AwsBasicCredentials.create(config.credentials().accessKey(), config.credentials().secretKey());
    }

    default AwsS3ClientTelemetryFactory awsS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                                                    @Nullable MeterRegistry meterRegistry) {
        return new DefaultAwsS3ClientTelemetryFactory(tracer, meterRegistry);
    }

    default AwsS3ClientFactory awsS3ClientFactory(SdkHttpClient httpClient,
                                                  AwsCredentialsProvider credentialsProvider,
                                                  S3Configuration s3Configuration,
                                                  AwsS3ClientTelemetryFactory telemetryFactory,
                                                  All<ExecutionInterceptor> interceptors) {
        return (config) -> {
            var configuration = s3Configuration.toBuilder()
                .checksumValidationEnabled(config.checksumValidationEnabled())
                .chunkedEncodingEnabled(config.chunkedEncodingEnabled())
                .pathStyleAccessEnabled(config.addressStyle() == AwsS3Config.AddressStyle.PATH)
                .build();

            return S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .httpClient(httpClient)
                .endpointOverride(URI.create(config.url()))
                .serviceConfiguration(configuration)
                .region(Region.of(config.region()))
                .overrideConfiguration(b -> b.addExecutionInterceptor(new AwsS3ClientTelemetryInterceptor(telemetryFactory.get(config.telemetry()))))
                .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
                .build();
        };
    }

    default S3Client awsS3Client(AwsS3Config config, AwsS3ClientFactory factory) {
        return factory.create(config);
    }
}
