package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.s3.client.S3ClientModule;
import ru.tinkoff.kora.s3.client.S3Config;
import ru.tinkoff.kora.s3.client.S3KoraAsyncClient;
import ru.tinkoff.kora.s3.client.S3KoraClient;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetryFactory;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientTelemetryFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.AwsClient;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.internal.multipart.MultipartS3AsyncClient;
import software.amazon.awssdk.services.s3.model.MultipartUpload;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApiStatus.Experimental
public interface AwsS3ClientModule extends S3ClientModule {

    @Tag(AwsClient.class)
    @DefaultComponent
    default HttpClient awsS3httpClient(HttpClient client) {
        return client;
    }

    @DefaultComponent
    default KoraAwsSdkHttpClient awsKoraSdkHttpClient(@Tag(AwsClient.class) HttpClient client,
                                                      AwsS3ClientConfig clientConfig) {
        return new KoraAwsSdkHttpClient(client, clientConfig);
    }

    @Tag(AwsClient.class)
    @DefaultComponent
    default ExecutorService awsAsyncExecutorService() {
        return Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 2);
    }

    default AwsS3ClientConfig awsS3ClientConfig(Config config, ConfigValueExtractor<AwsS3ClientConfig> extractor) {
        var value = config.get("s3client.aws");
        return extractor.extract(value);
    }

    @DefaultComponent
    default AwsCredentialsProvider awsCredentialsProvider(S3Config s3Config) {
        return () -> AwsBasicCredentials.create(s3Config.accessKey(), s3Config.secretKey());
    }

    @DefaultComponent
    default S3Configuration awsS3Configuration(AwsS3ClientConfig awsS3ClientConfig) {
        return S3Configuration.builder()
            .checksumValidationEnabled(awsS3ClientConfig.checksumValidationEnabled())
            .chunkedEncodingEnabled(awsS3ClientConfig.chunkedEncodingEnabled())
            .pathStyleAccessEnabled(awsS3ClientConfig.addressStyle() == AwsS3ClientConfig.AddressStyle.PATH)
            .build();
    }

    default S3Client awsS3Client(SdkHttpClient httpClient,
                                 AwsCredentialsProvider credentialsProvider,
                                 S3Configuration s3Configuration,
                                 S3Config s3Config,
                                 AwsS3ClientConfig awsS3ClientConfig,
                                 S3ClientTelemetryFactory telemetryFactory,
                                 All<ExecutionInterceptor> interceptors) {
        return S3Client.builder()
            .credentialsProvider(credentialsProvider)
            .httpClient(httpClient)
            .endpointOverride(URI.create(s3Config.url()))
            .serviceConfiguration(s3Configuration)
            .region(Region.of(s3Config.region()))
            .overrideConfiguration(b -> b.addExecutionInterceptor(new AwsS3ClientTelemetryInterceptor(telemetryFactory.get(s3Config.telemetry(), S3Client.class), awsS3ClientConfig.addressStyle())))
            .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
            .build();
    }

    default S3AsyncClient awsS3AsyncClient(SdkAsyncHttpClient asyncHttpClient,
                                           AwsCredentialsProvider credentialsProvider,
                                           S3Configuration s3Configuration,
                                           S3Config s3Config,
                                           AwsS3ClientConfig awsS3ClientConfig,
                                           S3ClientTelemetryFactory telemetryFactory,
                                           All<ExecutionInterceptor> interceptors) {
        return S3AsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .httpClient(asyncHttpClient)
            .endpointOverride(URI.create(s3Config.url()))
            .serviceConfiguration(s3Configuration)
            .region(Region.of(s3Config.region()))
            .overrideConfiguration(b -> b.addExecutionInterceptor(new AwsS3ClientTelemetryInterceptor(telemetryFactory.get(s3Config.telemetry(), S3AsyncClient.class), awsS3ClientConfig.addressStyle())))
            .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
            .build();
    }

    default S3KoraClient awsS3KoraClient(S3Client s3Client,
                                         S3KoraAsyncClient simpleAsyncClient,
                                         S3KoraClientTelemetryFactory telemetryFactory,
                                         S3Config config,
                                         AwsS3ClientConfig awsS3ClientConfig) {
        var telemetry = telemetryFactory.get(config.telemetry(), S3KoraClient.class);
        return new AwsS3KoraClient(s3Client, simpleAsyncClient, telemetry, awsS3ClientConfig);
    }

    default S3KoraAsyncClient awsS3KoraAsyncClient(S3AsyncClient s3AsyncClient,
                                                   @Tag(AwsClient.class) ExecutorService awsExecutor,
                                                   S3KoraClientTelemetryFactory telemetryFactory,
                                                   S3Config config,
                                                   AwsS3ClientConfig awsS3ClientConfig) {
        var telemetry = telemetryFactory.get(config.telemetry(), S3KoraAsyncClient.class);
        return new AwsS3KoraAsyncClient(s3AsyncClient, awsExecutor, telemetry, awsS3ClientConfig);
    }

    @Tag(MultipartUpload.class)
    default MultipartS3AsyncClient multipartS3AsyncClient(S3AsyncClient asyncClient,
                                                          AwsS3ClientConfig awsS3ClientConfig) {
        MultipartConfiguration config = MultipartConfiguration.builder()
            .thresholdInBytes(awsS3ClientConfig.upload().partSize().toBytes())
            .apiCallBufferSizeInBytes(awsS3ClientConfig.upload().bufferSize().toBytes())
            .minimumPartSizeInBytes(awsS3ClientConfig.upload().partSize().toBytes())
            .build();

        return MultipartS3AsyncClient.create(asyncClient, config);
    }
}
