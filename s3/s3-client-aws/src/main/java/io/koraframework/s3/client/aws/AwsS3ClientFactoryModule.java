package io.koraframework.s3.client.aws;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.s3.client.aws.telemetry.AwsS3ClientTelemetryFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

public class AwsS3ClientFactoryModule {

    private final String configPath;

    public AwsS3ClientFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public AwsS3Config awsS3Config(Config config, ConfigValueMapper<AwsS3Config> mapper) {
        return mapper.mapOrThrow(config.get(configPath));
    }

    @Tag(Tag.Factory.class)
    @DefaultComponent
    public AwsS3HttpClientProvider awsS3HttpClientProvider(HttpClient client) {
        return () -> client;
    }

    @Tag(Tag.Factory.class)
    @DefaultComponent
    public KoraAwsSdkHttpClient awsS3SdkHttpClient(@Tag(Tag.Factory.class) AwsS3HttpClientProvider clientProvider,
                                                   @Tag(Tag.Factory.class) AwsS3Config clientConfig) {
        return new KoraAwsSdkHttpClient(clientProvider.get(), clientConfig);
    }

    @Tag(Tag.Factory.class)
    @DefaultComponent
    public S3Configuration awsS3Configuration(@Tag(Tag.Factory.class) AwsS3Config config) {
        return S3Configuration.builder()
            .chunkedEncodingEnabled(config.chunkedEncodingEnabled())
            .pathStyleAccessEnabled(config.addressStyle() == AwsS3Config.AddressStyle.PATH)
            .build();
    }

    @Tag(Tag.Factory.class)
    @DefaultComponent
    public AwsCredentialsProvider awsS3credentialsProvider(@Tag(Tag.Factory.class) AwsS3Config config) {
        return () -> AwsBasicCredentials.create(config.credentials().accessKey(), config.credentials().secretKey());
    }

    @Tag(Tag.Factory.class)
    public AwsS3ClientFactory awsS3ClientFactory(@Tag(Tag.Factory.class) SdkHttpClient httpClient,
                                                 @Tag(Tag.Factory.class) AwsCredentialsProvider credentialsProvider,
                                                 @Tag(Tag.Factory.class) S3Configuration s3Configuration,
                                                 AwsS3ClientTelemetryFactory telemetryFactory,
                                                 @Tag(Tag.Factory.class) All<ExecutionInterceptor> interceptors) {
        return (config) -> {
            var configuration = s3Configuration.toBuilder()
                .chunkedEncodingEnabled(config.chunkedEncodingEnabled())
                .pathStyleAccessEnabled(config.addressStyle() == AwsS3Config.AddressStyle.PATH)
                .build();

            return S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .httpClient(httpClient)
                .endpointOverride(URI.create(config.url()))
                .serviceConfiguration(configuration)
                .region(Region.of(config.region()))
                .requestChecksumCalculation(RequestChecksumCalculation.fromValue(config.checksumCalculationRequest().name()))
                .responseChecksumValidation(ResponseChecksumValidation.fromValue(config.checksumValidationResponse().name()))
                .overrideConfiguration(b -> b.addExecutionInterceptor(new AwsS3ClientTelemetryInterceptor(telemetryFactory.get(configPath, S3Client.class, config.telemetry()))))
                .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
                .build();
        };
    }

    @Tag(Tag.Factory.class)
    public S3Client awsS3Client(@Tag(Tag.Factory.class) AwsS3Config config,
                                @Tag(Tag.Factory.class) AwsS3ClientFactory factory) {
        return factory.create(config);
    }
}
