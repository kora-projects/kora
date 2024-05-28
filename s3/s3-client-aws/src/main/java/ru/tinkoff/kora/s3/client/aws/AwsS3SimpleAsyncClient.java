package ru.tinkoff.kora.s3.client.aws;

import reactor.adapter.JdkFlowAdapter;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.aws.AwsS3ClientTelemetryInterceptor.Operation;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.exception.SdkServiceException;
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

import static ru.tinkoff.kora.s3.client.aws.AwsS3ClientTelemetryInterceptor.OPERATION_KEY;

public class AwsS3SimpleAsyncClient implements S3SimpleAsyncClient {

    private final S3AsyncClient asyncClient;
    private final S3AsyncClient multipartAsyncClient;
    private final ExecutorService awsExecutor;
    private final S3ClientTelemetry telemetry;
    private final AwsS3ClientConfig awsS3ClientConfig;

    public AwsS3SimpleAsyncClient(S3AsyncClient asyncClient,
                                  ExecutorService awsExecutor,
                                  S3ClientTelemetry telemetry,
                                  AwsS3ClientConfig awsS3ClientConfig) {
        this.asyncClient = asyncClient;
        this.awsExecutor = awsExecutor;
        this.telemetry = telemetry;

        this.awsS3ClientConfig = awsS3ClientConfig;
        this.multipartAsyncClient = MultipartS3AsyncClient.create(asyncClient,
            MultipartConfiguration.builder()
                .thresholdInBytes(awsS3ClientConfig.upload().bufferSize())
                .apiCallBufferSizeInBytes(awsS3ClientConfig.upload().bufferSize())
                .minimumPartSizeInBytes(awsS3ClientConfig.upload().partSize())
                .build());
    }

    @Override
    public CompletionStage<S3Object> get(String bucket, String key) {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("GET", bucket));

            return asyncClient.getObject(request, AsyncResponseTransformer.toPublisher())
                .thenApply(r -> ((S3Object) new AwsS3Object(request.key(), r)))
                .exceptionallyCompose(e -> handleExceptionStage(e));
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletionStage<S3ObjectMeta> getMeta(String bucket, String key) {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .objectAttributes(ObjectAttributes.OBJECT_SIZE)
            .build();

        var ctx = Context.current();

        try {
            ctx.set(OPERATION_KEY, new Operation("GET_META", bucket));

            return asyncClient.getObjectAttributes(request)
                .thenApply(r -> ((S3ObjectMeta) new AwsS3ObjectMeta(key, r)))
                .exceptionallyCompose(e -> handleExceptionStage(e));
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletionStage<List<S3Object>> get(String bucket, Collection<String> keys) {
        var telemetryContext = telemetry.get("GET_MANY", bucket);

        var futures = keys.stream()
            .map(k -> get(bucket, k).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> {
                telemetryContext.close(200);
                return Arrays.stream(futures)
                    .map(f -> ((S3Object) f.join()))
                    .toList();
            })
            .exceptionallyCompose(e -> handleExceptionAndTelemetryStage(e, telemetryContext));
    }

    @Override
    public CompletionStage<List<S3ObjectMeta>> getMeta(String bucket, Collection<String> keys) {
        var telemetryContext = telemetry.get("GET_META_MANY", bucket);

        var futures = keys.stream()
            .map(k -> getMeta(bucket, k).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> {
                telemetryContext.close(200);
                return Arrays.stream(futures)
                    .map(f -> ((S3ObjectMeta) f.join()))
                    .toList();
            })
            .exceptionallyCompose(e -> handleExceptionAndTelemetryStage(e, telemetryContext));
    }

    @Override
    public CompletionStage<S3ObjectList> list(String bucket, String prefix, int limit) {
        var telemetryContext = telemetry.get("LIST", bucket);

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

                        telemetryContext.close(200);
                        return ((S3ObjectList) new AwsS3ObjectList(((AwsS3ObjectMetaList) metaList).response(), objects));
                    });
            })
            .exceptionallyCompose(e -> handleExceptionAndTelemetryStage(e, telemetryContext));
    }

    private CompletionStage<S3ObjectList> listInternal(String bucket, String prefix, int limit) {
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

                        return new AwsS3ObjectList(((AwsS3ObjectMetaList) metaList).response(), objects);
                    });
            });
    }

    @Override
    public CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .maxKeys(limit)
            .build();

        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("LIST_META", bucket));

            return asyncClient.listObjectsV2(request)
                .thenApply(response -> ((S3ObjectMetaList) new AwsS3ObjectMetaList(response)))
                .exceptionallyCompose(e -> handleExceptionStage(e));
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletionStage<List<S3ObjectList>> list(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var telemetryContext = telemetry.get("LIST_MANY", bucket);

        var futures = prefixes.stream()
            .map(p -> listInternal(bucket, p, limitPerPrefix).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> {
                telemetryContext.close(200);
                return Arrays.stream(futures)
                    .map(f -> ((S3ObjectList) f.join()))
                    .toList();
            })
            .exceptionallyCompose(e -> handleExceptionAndTelemetryStage(e, telemetryContext));
    }

    @Override
    public CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var telemetryContext = telemetry.get("LIST_META_MANY", bucket);

        var futures = prefixes.stream()
            .map(p -> listMeta(bucket, p, limitPerPrefix).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .thenApply(_v -> {
                telemetryContext.close(200);
                return Arrays.stream(futures)
                    .map(f -> ((S3ObjectMetaList) f.join()))
                    .toList();
            })
            .exceptionallyCompose(e -> handleExceptionAndTelemetryStage(e, telemetryContext));
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
            ctx.set(OPERATION_KEY, new Operation("PUT", bucket));

            if (body instanceof ByteS3Body bb) {
                return asyncClient.putObject(request, AsyncRequestBody.fromBytes(bb.bytes()))
                    .thenApply(r -> ((S3ObjectUpload) new AwsS3ObjectUpload(r)))
                    .exceptionallyCompose(e -> handleExceptionStage(e));
            } else if (body instanceof PublisherS3Body) {
                return asyncClient.putObject(request, AsyncRequestBody.fromPublisher(JdkFlowAdapter.flowPublisherToFlux(body.asPublisher())))
                    .thenApply(r -> ((S3ObjectUpload) new AwsS3ObjectUpload(r)))
                    .exceptionallyCompose(e -> handleExceptionStage(e));
            } else if (body.size() > 0 && body.size() <= awsS3ClientConfig.upload().bufferSize()) {
                return asyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), body.size(), awsExecutor))
                    .thenApply(r -> ((S3ObjectUpload) new AwsS3ObjectUpload(r)))
                    .exceptionallyCompose(e -> handleExceptionStage(e));
            } else {
                final Long bodySize = body.size() > 0 ? body.size() : null;
                return multipartAsyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), bodySize, awsExecutor))
                    .thenApply(r -> ((S3ObjectUpload) new AwsS3ObjectUpload(r)))
                    .exceptionallyCompose(e -> handleExceptionStage(e));
            }
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletionStage<Void> delete(String bucket, String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var ctx = Context.current();
        ctx.set(OPERATION_KEY, new Operation("DELETE", bucket));

        try {
            return asyncClient.deleteObject(request)
                .thenAccept(r -> {})
                .exceptionallyCompose(e -> handleExceptionStage(e));
        } finally {
            ctx.remove(OPERATION_KEY);
        }
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

        var ctx = Context.current();
        ctx.set(OPERATION_KEY, new Operation("DELETE_MANY", bucket));

        try {
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
                .exceptionallyCompose(e -> handleExceptionStage(e));
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    private static <T> CompletionStage<T> handleExceptionAndTelemetryStage(Throwable e, S3ClientTelemetry.S3ClientTelemetryContext telemetryContext) {
        Throwable cause = e;
        if (e instanceof S3Exception se) {
            cause = se.getCause();
        }
        if (cause instanceof SdkServiceException se) {
            telemetryContext.close(se.statusCode(), e);
        } else {
            telemetryContext.close(-1, e);
        }
        return handleExceptionStage(e);
    }

    private static <T> CompletionStage<T> handleExceptionStage(Throwable e) {
        return CompletableFuture.failedFuture(handleException(e));
    }

    private static S3Exception handleException(Throwable e) {
        if (e instanceof CompletionException ce) {
            e = ce.getCause();
        }

        if (e instanceof NoSuchKeyException ke) {
            return S3NotFoundException.ofNoSuchKey(e, ke.awsErrorDetails().errorMessage());
        } else if (e instanceof NoSuchBucketException be) {
            return S3NotFoundException.ofNoSuchBucket(e, be.awsErrorDetails().errorMessage());
        } else if (e instanceof AwsServiceException ae) {
            return new S3Exception(e, ae.awsErrorDetails().errorCode(), ae.awsErrorDetails().errorMessage());
        } else if (e instanceof S3Exception se) {
            return se;
        } else {
            return new S3Exception(e, "unknown", "unknown");
        }
    }
}
