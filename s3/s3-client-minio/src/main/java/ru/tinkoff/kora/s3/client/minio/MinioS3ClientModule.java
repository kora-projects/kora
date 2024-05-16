package ru.tinkoff.kora.s3.client.minio;

import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.S3Base;
import io.minio.credentials.Provider;
import io.minio.credentials.StaticProvider;
import io.minio.http.HttpUtils;
import jakarta.annotation.Nullable;
import okhttp3.OkHttpClient;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.s3.client.S3ClientModule;
import ru.tinkoff.kora.s3.client.S3Config;

public interface MinioS3ClientModule extends S3ClientModule {

    @DefaultComponent
    default Provider minioCredentialProvider(S3Config s3Config) {
        return new StaticProvider(s3Config.accessKey(), s3Config.secretKey(), null);
    }

    default MinioClient minioClient(S3Config s3Config,
                                    Provider provider,
                                    @Nullable OkHttpClient okHttpClient) {
        var builder = MinioClient.builder()
            .endpoint(s3Config.url())
            .region(s3Config.region())
            .credentialsProvider(provider);

        if (okHttpClient != null) {
            builder.httpClient(okHttpClient);
        }

        return builder.build();
    }

    default MinioAsyncClient minioAsyncClient(S3Config s3Config,
                                              Provider provider,
                                              @Nullable OkHttpClient okHttpClient) {
        var builder = MinioAsyncClient.builder()
            .endpoint(s3Config.url())
            .region(s3Config.region())
            .credentialsProvider(provider);

        if (okHttpClient != null) {
            builder.httpClient(okHttpClient);
        }

        return builder.build();
    }
}
