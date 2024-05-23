package ru.tinkoff.kora.s3.client.aws;

import reactor.adapter.JdkFlowAdapter;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

public class AwsS3SimpleAsyncClient implements S3SimpleAsyncClient {

    private final S3AsyncClient asyncClient;
    private final ExecutorService awsExecutor;
    private final S3ClientTelemetry telemetry;

    public AwsS3SimpleAsyncClient(S3AsyncClient asyncClient, ExecutorService awsExecutor, S3ClientTelemetry telemetry) {
        this.asyncClient = asyncClient;
        this.awsExecutor = awsExecutor;
        this.telemetry = telemetry;
    }

    @Override
    public CompletionStage<S3Object> get(String bucket, String key) {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = telemetry.get("GET", bucket);
        ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

        return asyncClient.getObject(request, AsyncResponseTransformer.toPublisher())
            .thenApply(r -> ((S3Object) new AwsS3Object(request.key(), r)))
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<S3ObjectMeta> getMeta(String bucket, String key) {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .objectAttributes(ObjectAttributes.OBJECT_SIZE)
            .build();

        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = telemetry.get("GET_META", bucket);
        ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

        return asyncClient.getObjectAttributes(request)
            .thenApply(r -> ((S3ObjectMeta) new AwsS3ObjectMeta(key, r)))
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<List<S3Object>> get(String bucket, Collection<String> keys) {
        var futures = keys.stream()
            .map(k -> get(bucket, k).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> Arrays.stream(futures)
                .map(f -> ((S3Object) f.join()))
                .toList())
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<List<S3ObjectMeta>> getMeta(String bucket, Collection<String> keys) {
        var futures = keys.stream()
            .map(k -> getMeta(bucket, k).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> Arrays.stream(futures)
                .map(f -> ((S3ObjectMeta) f.join()))
                .toList())
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<S3ObjectList> list(String bucket, String prefix, int limit) {
        return listMeta(bucket, prefix, limit)
            .thenCompose(metaList -> {
                var futures = metaList.metas().stream()
                    .map(meta -> get(bucket, meta.key()).toCompletableFuture())
                    .toArray(CompletableFuture[]::new);

                return CompletableFuture.allOf(futures)
                    .thenApply(_v -> {
                        final List<S3Object> objects = new ArrayList<>(futures.length);
                        for (var future : futures) {
                            objects.add(((S3Object) future.join()));
                        }

                        return ((S3ObjectList) new AwsS3ObjectList(((AwsS3ObjectMetaList) metaList).response(), objects));
                    });
            })
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .maxKeys(limit)
            .build();

        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = telemetry.get("LIST_META", bucket);
        ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

        return asyncClient.listObjectsV2(request)
            .thenApply(response -> ((S3ObjectMetaList) new AwsS3ObjectMetaList(response)))
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<List<S3ObjectList>> list(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var futures = prefixes.stream()
            .map(p -> list(bucket, p, limitPerPrefix).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> Arrays.stream(futures)
                .map(f -> ((S3ObjectList) f.join()))
                .toList())
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var futures = prefixes.stream()
            .map(p -> listMeta(bucket, p, limitPerPrefix).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> Arrays.stream(futures)
                .map(f -> ((S3ObjectMetaList) f.join()))
                .toList())
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<String> put(String bucket, String key, S3Body body) {
        var requestBuilder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(body.type())
            .contentEncoding(body.encoding());

        if (body.size() > 0) {
            requestBuilder.contentLength(body.size());
        }

        var request = requestBuilder.build();

        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = telemetry.get("PUT", bucket);
        ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

        if (body instanceof ByteS3Body bb) {
            return asyncClient.putObject(request, AsyncRequestBody.fromBytes(bb.bytes()))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        } else if (body instanceof PublisherS3Body) {
            return asyncClient.putObject(request, AsyncRequestBody.fromPublisher(JdkFlowAdapter.flowPublisherToFlux(body.asPublisher())))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        } else if (body.size() > 0) {
            return asyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), body.size(), awsExecutor))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        } else {
            return asyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), null, awsExecutor))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        }
    }

    @Override
    public CompletionStage<Void> delete(String bucket, String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = telemetry.get("DELETE", bucket);
        ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

        return asyncClient.deleteObject(request)
            .thenAccept(r -> {})
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<Void> delete(String bucket, Collection<String> keys) {
        var request = DeleteObjectsRequest.builder()
            .bucket(bucket)
            .delete(Delete.builder()
                .objects(keys.stream()
                    .map(k -> ObjectIdentifier.builder()
                        .key(k)
                        .build())
                    .toList())
                .build())
            .build();

        var ctx = ru.tinkoff.kora.common.Context.current();
        var telemetryContext = telemetry.get("DELETE_MANY", bucket);
        ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

        return asyncClient.deleteObjects(request)
            .<Void>thenApply(response -> {
                if (response.hasErrors()) {
                    var errors = response.errors().stream()
                        .map(e -> new S3DeleteException.Error(e.key(), bucket, e.code(), e.message()))
                        .toList();

                    throw new S3DeleteException(errors);
                }

                return null;
            })
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    private static <T> CompletionStage<T> handleException(Throwable e) {
        if (e instanceof CompletionException ce) {
            e = ce.getCause();
        }

        if (e instanceof NoSuchKeyException || e instanceof NoSuchBucketException) {
            return CompletableFuture.failedFuture(new S3NotFoundException(e));
        } else if (e instanceof S3Exception) {
            return CompletableFuture.failedFuture(e);
        } else {
            return CompletableFuture.failedFuture(new S3Exception(e));
        }
    }
}
