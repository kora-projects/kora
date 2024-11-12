package ru.tinkoff.kora.s3.client.minio;

import io.minio.errors.ErrorResponseException;
import jakarta.annotation.Nonnull;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry.S3ClientTelemetryContext;

import java.io.IOException;
import java.util.concurrent.CompletionException;

@ApiStatus.Experimental
public final class MinioS3ClientTelemetryInterceptor implements Interceptor {

    private final S3ClientTelemetry telemetry;
    private final MinioS3ClientConfig.AddressStyle addressStyle;

    public MinioS3ClientTelemetryInterceptor(S3ClientTelemetry telemetry, MinioS3ClientConfig.AddressStyle addressStyle) {
        this.telemetry = telemetry;
        this.addressStyle = addressStyle;
    }

    @Nonnull
    @Override
    public Response intercept(@Nonnull Chain chain) throws IOException {
        final S3ClientTelemetryContext telemetryContext = telemetry.get();

        final Request request = chain.request();
        Long contentLength = (request.body() == null)
            ? null
            : request.body().contentLength();

        final HttpUrl url = request.url();
        final BucketAndKey bk;
        if (addressStyle == MinioS3ClientConfig.AddressStyle.PATH) {
            bk = getPathAddress(url.encodedPath());
        } else {
            bk = getVirtualHost(url.host(), url.encodedPath());
        }

        telemetryContext.prepared(request.method(), bk.bucket(), bk.key(), contentLength);

        try {
            final Response response = chain.proceed(request);
            telemetryContext.close(request.method(), bk.bucket(), bk.key(), response.code());
            return response;
        } catch (Exception e) {
            Throwable cause = e;
            if (cause instanceof CompletionException ce) {
                cause = ce.getCause();
            }

            final S3Exception ex;
            final int code;
            if (cause instanceof ErrorResponseException re) {
                code = re.response().code();
                ex = new S3Exception(re, re.errorResponse().code(), re.errorResponse().message());
            } else {
                code = -1;
                ex = new S3Exception(cause, cause.getClass().getSimpleName(), cause.getMessage());
            }

            telemetryContext.close(request.method(), bk.bucket(), bk.key(), code, ex);
            throw e;
        }
    }

    private record BucketAndKey(String bucket, @Nullable String key) {}

    private static BucketAndKey getPathAddress(String path) {
        final String bucket;
        final String key;
        final int startFrom = (path.charAt(0) == '/')
            ? 1
            : 0;

        if (path.equals("/")) {
            bucket = "/";
            key = null;
        } else {
            int bucketSeparator = path.indexOf('/', startFrom);
            if (bucketSeparator == -1) {
                bucket = path.substring(startFrom);
                key = null;
            } else {
                bucket = path.substring(startFrom, bucketSeparator);
                key = path.substring(bucketSeparator + 1);
            }
        }

        return new BucketAndKey(bucket, key);
    }

    private static BucketAndKey getVirtualHost(String host, String path) {
        int bucketEnd = host.indexOf('.');
        if (bucketEnd == -1) {
            return getPathAddress(path);
        }

        final String bucket;
        final String key;
        final int startFrom = (path.charAt(0) == '/')
            ? 1
            : 0;
        bucket = host.substring(0, bucketEnd);
        if (path.length() == 1) {
            key = null;
        } else {
            key = path.substring(startFrom);
        }

        return new BucketAndKey(bucket, key);
    }
}
