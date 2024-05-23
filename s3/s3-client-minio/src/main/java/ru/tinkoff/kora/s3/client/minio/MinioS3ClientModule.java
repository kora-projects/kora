package ru.tinkoff.kora.s3.client.minio;

import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.credentials.Provider;
import io.minio.credentials.StaticProvider;
import io.minio.http.HttpUtils;
import jakarta.annotation.Nullable;
import okhttp3.OkHttpClient;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.s3.client.S3ClientModule;
import ru.tinkoff.kora.s3.client.S3Config;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.S3SimpleClient;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetryFactory;

import java.util.concurrent.TimeUnit;

public interface MinioS3ClientModule extends S3ClientModule {

    @DefaultComponent
    default Provider minioCredentialProvider(S3Config s3Config) {
        return new StaticProvider(s3Config.accessKey(), s3Config.secretKey(), null);
    }

    default MinioS3ClientConfig minioS3ClientConfig(Config config, ConfigValueExtractor<MinioS3ClientConfig> extractor) {
        var value = config.get("s3client.minio");
        return extractor.extract(value);
    }

    @Tag(MinioClient.class)
    default OkHttpClient minioOkHttpClient(@Nullable OkHttpClient okHttpClient,
                                           MinioS3ClientConfig minioS3ClientConfig) {
        long timeout = (minioS3ClientConfig.requestTimeout() != null)
            ? minioS3ClientConfig.requestTimeout().toMillis()
            : TimeUnit.MINUTES.toMillis(5L);

        return (okHttpClient == null)
            ? HttpUtils.newDefaultHttpClient(timeout, timeout, timeout)
            : okHttpClient.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    default MinioClient minioClient(S3Config s3Config,
                                    Provider provider,
                                    S3ClientTelemetryFactory telemetryFactory,
                                    @Tag(MinioClient.class) OkHttpClient okHttpClient) {
        return MinioClient.builder()
            .endpoint(s3Config.url())
            .region(s3Config.region())
            .credentialsProvider(provider)
            .httpClient(okHttpClient.newBuilder()
                .addInterceptor(new MinioS3ClientTelemetryInterceptor(telemetryFactory.get(s3Config.telemetry(), MinioClient.class.getCanonicalName())))
                .build())
            .build();
    }

    default MinioAsyncClient minioAsyncClient(S3Config s3Config,
                                              Provider provider,
                                              S3ClientTelemetryFactory telemetryFactory,
                                              @Tag(MinioClient.class) OkHttpClient okHttpClient) {
        return MinioAsyncClient.builder()
            .endpoint(s3Config.url())
            .region(s3Config.region())
            .credentialsProvider(provider)
            .httpClient(okHttpClient.newBuilder()
                .addInterceptor(new MinioS3ClientTelemetryInterceptor(telemetryFactory.get(s3Config.telemetry(), MinioClient.class.getCanonicalName())))
                .build())
            .build();
    }

    default S3SimpleClient MinioS3SimpleClient(MinioClient minioClient) {
        return new MinioS3SimpleClient(minioClient);
    }

    default S3SimpleAsyncClient MinioS3SimpleAsyncClient(MinioAsyncClient minioAsyncClient) {
        return new MinioS3SimpleAsyncClient(minioAsyncClient);
    }
}
