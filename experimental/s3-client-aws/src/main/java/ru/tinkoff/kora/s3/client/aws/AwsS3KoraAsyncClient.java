package ru.tinkoff.kora.s3.client.aws;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import reactor.adapter.JdkFlowAdapter;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3KoraAsyncClient;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientTelemetry;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.internal.multipart.MultipartS3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Experimental
public class AwsS3KoraAsyncClient implements S3KoraAsyncClient {

    private final S3AsyncClient asyncClient;
    private final S3AsyncClient multipartAsyncClient;
    private final ExecutorService awsExecutor;
    private final S3KoraClientTelemetry telemetry;
    private final AwsS3ClientConfig awsS3ClientConfig;

    public AwsS3KoraAsyncClient(S3AsyncClient asyncClient,
                                ExecutorService awsExecutor,
                                S3KoraClientTelemetry telemetry,
                                AwsS3ClientConfig awsS3ClientConfig) {
        this.asyncClient = asyncClient;
        this.awsExecutor = awsExecutor;
        this.telemetry = telemetry;

        this.awsS3ClientConfig = awsS3ClientConfig;
        this.multipartAsyncClient = MultipartS3AsyncClient.create(asyncClient,
            MultipartConfiguration.builder()
                .thresholdInBytes(awsS3ClientConfig.upload().partSize().toBytes())
                .apiCallBufferSizeInBytes(awsS3ClientConfig.upload().bufferSize().toBytes())
                .minimumPartSizeInBytes(awsS3ClientConfig.upload().partSize().toBytes())
                .build());
    }

    private CompletionStage<S3Object> getInternal(String bucket, String key) {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        return asyncClient.getObject(request, AsyncResponseTransformer.toPublisher())
            .thenApply(r -> new AwsS3Object(request.key(), r));
    }

    @Override
    public CompletionStage<S3Object> get(String bucket, String key) {
        return wrapWithTelemetry(getInternal(bucket, key),
            () -> telemetry.get("GetObject", bucket, key, null));
    }

    private CompletionStage<S3ObjectMeta> getMetaInternal(String bucket, String key) {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .objectAttributes(ObjectAttributes.OBJECT_SIZE)
            .build();

        return asyncClient.getObjectAttributes(request)
            .thenApply(r -> new AwsS3ObjectMeta(key, r));
    }

    @Override
    public CompletionStage<S3ObjectMeta> getMeta(String bucket, String key) {
        return wrapWithTelemetry(getMetaInternal(bucket, key),
            () -> telemetry.get("GetObjectMeta", bucket, key, null));
    }

    @Override
    public CompletionStage<List<S3Object>> get(String bucket, Collection<String> keys) {
        var futures = keys.stream()
            .map(k -> getInternal(bucket, k).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        var operation = CompletableFuture.allOf(futures)
            .thenApply(_v -> Arrays.stream(futures)
                .map(f -> ((S3Object) f.join()))
                .toList())
            .exceptionallyCompose(AwsS3KoraAsyncClient::handleExceptionStage);

        return wrapWithTelemetry(operation,
            () -> telemetry.get("GetObjects", bucket, null, null));
    }

    @Override
    public CompletionStage<List<S3ObjectMeta>> getMeta(String bucket, Collection<String> keys) {
        var futures = keys.stream()
            .map(k -> getMetaInternal(bucket, k).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        var operation = CompletableFuture.allOf(futures)
            .thenApply(_v -> Arrays.stream(futures)
                .map(f -> ((S3ObjectMeta) f.join()))
                .toList())
            .exceptionallyCompose(AwsS3KoraAsyncClient::handleExceptionStage);

        return wrapWithTelemetry(operation,
            () -> telemetry.get("GetObjectMetas", bucket, null, null));
    }

    @Override
    public CompletionStage<S3ObjectList> list(String bucket, String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(fork -> listInternal(bucket, prefix, delimiter, limit, fork),
            () -> telemetry.get("ListObjects", bucket, prefix, null));
    }

    private CompletionStage<S3ObjectList> listInternal(String bucket, String prefix, @Nullable String delimiter, int limit, Context context) {
        return listMetaInternal(bucket, prefix, delimiter, limit)
            .thenCompose(metaList -> {
                try {
                    context.inject();

                    var futures = metaList.metas().stream()
                        .map(meta -> getInternal(bucket, meta.key()).toCompletableFuture())
                        .toArray(CompletableFuture[]::new);

                    return CompletableFuture.allOf(futures)
                        .thenApply(_v -> {
                            final List<S3Object> objects = new ArrayList<>(futures.length);
                            for (var future : futures) {
                                objects.add(((S3Object) future.join()));
                            }

                            return new AwsS3ObjectList(((AwsS3ObjectMetaList) metaList).response(), objects);
                        });
                } finally {
                    Context.clear();
                }
            });
    }

    private CompletionStage<S3ObjectMetaList> listMetaInternal(String bucket, String prefix, @Nullable String delimiter, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .maxKeys(limit)
            .delimiter(delimiter)
            .build();

        return asyncClient.listObjectsV2(request)
            .thenApply(response -> new AwsS3ObjectMetaList(response));
    }

    @Override
    public CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(listMetaInternal(bucket, prefix, delimiter, limit),
            () -> telemetry.get("ListObjectMetas", bucket, prefix, null));
    }

    @Override
    public CompletionStage<List<S3ObjectList>> list(String bucket, Collection<String> prefixes, @Nullable String delimiter, int limitPerPrefix) {
        return wrapWithTelemetry(fork -> {
            var futures = prefixes.stream()
                .map(p -> listInternal(bucket, p, delimiter, limitPerPrefix, fork).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(futures)
                .thenApply(_v -> Arrays.stream(futures)
                    .map(f -> ((S3ObjectList) f.join()))
                    .toList());
        }, () -> telemetry.get("ListMultiObjects", bucket, null, null));
    }

    @Override
    public CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket, Collection<String> prefixes, @Nullable String delimiter, int limitPerPrefix) {
        return wrapWithTelemetry(fork -> {
            var futures = prefixes.stream()
                .map(p -> listMetaInternal(bucket, p, delimiter, limitPerPrefix).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(futures)
                .thenApply(_v -> Arrays.stream(futures)
                    .map(f -> ((S3ObjectMetaList) f.join()))
                    .toList());
        }, () -> telemetry.get("ListMultiObjectMetas", bucket, null, null));
    }

    @Override
    public CompletionStage<S3ObjectUpload> put(String bucket, String key, S3Body body) {
        var requestBuilder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(body.type())
            .contentEncoding(body.encoding());

        if (body.size() > 0) {
            requestBuilder.contentLength(body.size());
        }

        var request = requestBuilder.build();

        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var size = body.size() > 0 ? body.size() : null;
            var context = telemetry.get("PutObject", bucket, key, size);

            final CompletionStage<S3ObjectUpload> operation;
            if (body instanceof ByteS3Body bb) {
                operation = asyncClient.putObject(request, AsyncRequestBody.fromBytes(bb.bytes()))
                    .thenApply(AwsS3ObjectUpload::new);
            } else if (body instanceof PublisherS3Body) {
                operation = asyncClient.putObject(request, AsyncRequestBody.fromPublisher(JdkFlowAdapter.flowPublisherToFlux(body.asPublisher())))
                    .thenApply(AwsS3ObjectUpload::new);
            } else if (body.size() > 0 && body.size() <= awsS3ClientConfig.upload().partSize().toBytes()) {
                operation = asyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), body.size(), awsExecutor))
                    .thenApply(AwsS3ObjectUpload::new);
            } else {
                operation = multipartAsyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), size, awsExecutor))
                    .thenApply(AwsS3ObjectUpload::new);
            }

            return operation
                .exceptionallyCompose(AwsS3KoraAsyncClient::handleExceptionStage)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        context.close(handleException(e));
                    } else {
                        context.close();
                    }
                });
        } finally {
            ctx.inject();
        }
    }

    @Override
    public CompletionStage<Void> delete(String bucket, String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var operation = asyncClient.deleteObject(request)
            .thenAccept(r -> {});

        return wrapWithTelemetry(operation,
            () -> telemetry.get("DeleteObject", bucket, key, null));
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

        CompletableFuture<Void> operation = asyncClient.deleteObjects(request)
            .thenApply(response -> {
                if (response.hasErrors()) {
                    var errors = response.errors().stream()
                        .map(e -> new S3DeleteException.Error(e.key(), bucket, e.code(), e.message()))
                        .toList();

                    throw new S3DeleteException(errors);
                }

                return null;
            });

        return wrapWithTelemetry(operation,
            () -> telemetry.get("DeleteObjects", bucket, null, null));
    }

    private static <T> CompletionStage<T> wrapWithTelemetry(CompletionStage<T> operationSupplier,
                                                            Supplier<S3KoraClientTelemetry.S3KoraClientTelemetryContext> contextSupplier) {
        return wrapWithTelemetry(context -> operationSupplier, contextSupplier);
    }

    private static <T> CompletionStage<T> wrapWithTelemetry(Function<Context, CompletionStage<T>> operationSupplier,
                                                            Supplier<S3KoraClientTelemetry.S3KoraClientTelemetryContext> contextSupplier) {
        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = contextSupplier.get();
            return operationSupplier.apply(fork)
                .exceptionallyCompose(AwsS3KoraAsyncClient::handleExceptionStage)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        context.close(handleException(e));
                    } else {
                        context.close();
                    }
                });
        } finally {
            ctx.inject();
        }
    }

    private static <T> CompletionStage<T> handleExceptionStage(Throwable e) {
        return CompletableFuture.failedFuture(handleException(e));
    }

    private static S3Exception handleException(Throwable e) {
        if (e instanceof CompletionException ce) {
            e = ce.getCause();
        }

        if (e instanceof S3Exception se) {
            return se;
        } else if (e instanceof NoSuchKeyException ke) {
            return S3NotFoundException.ofNoSuchKey(e, ke.awsErrorDetails().errorMessage());
        } else if (e instanceof NoSuchBucketException be) {
            return S3NotFoundException.ofNoSuchBucket(e, be.awsErrorDetails().errorMessage());
        } else if (e instanceof AwsServiceException ae) {
            return new S3Exception(e, ae.awsErrorDetails().errorCode(), ae.awsErrorDetails().errorMessage());
        } else {
            return new S3Exception(e, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
