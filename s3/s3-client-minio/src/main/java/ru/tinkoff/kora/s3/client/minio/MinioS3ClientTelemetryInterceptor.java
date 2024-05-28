package ru.tinkoff.kora.s3.client.minio;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry.S3ClientTelemetryContext;

import java.io.IOException;

public final class MinioS3ClientTelemetryInterceptor implements Interceptor {

    public static final Context.Key<Operation> OPERATION_KEY = new Context.KeyImmutable<>() {};

    public record Operation(String name, String bucket) {}

    private final S3ClientTelemetry telemetry;

    public MinioS3ClientTelemetryInterceptor(S3ClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        var ctx = Context.current();
        var operation = ctx.get(OPERATION_KEY);

        final S3ClientTelemetryContext telemetryContext;
        if (operation != null) {
            telemetryContext = telemetry.get(operation.name(), operation.bucket());
        } else {
            telemetryContext = telemetry.get(null, null);
        }

        final Request request = chain.request();
        Long contentLength = (request.body() == null)
            ? null
            : request.body().contentLength();
        HttpUrl url = request.url();
        telemetryContext.prepared(request.method(), url.encodedPath(), url.uri(), url.host(), url.port(), contentLength);

        try {
            final Response response = chain.proceed(request);
            telemetryContext.close(response.code());
            return response;
        } catch (Exception e) {
            telemetryContext.close(-1, e);
            throw e;
        }
    }
}
