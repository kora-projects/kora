package ru.tinkoff.kora.s3.client.minio;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3KoraAsyncClient;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientTelemetry;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@ApiStatus.Experimental
public class MinioS3KoraAsyncClient implements S3KoraAsyncClient {

    private final MinioAsyncClient minioClient;
    private final MinioS3ClientConfig minioS3ClientConfig;
    private final S3KoraClientTelemetry telemetry;

    public MinioS3KoraAsyncClient(MinioAsyncClient minioClient,
                                  MinioS3ClientConfig minioS3ClientConfig,
                                  S3KoraClientTelemetry telemetry) {
        this.minioClient = minioClient;
        this.minioS3ClientConfig = minioS3ClientConfig;
        this.telemetry = telemetry;
    }

    private CompletionStage<S3Object> getInternal(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .thenApply(MinioS3Object::new);
        } catch (Exception e) {
            return handleExceptionStage(e);
        }
    }

    @Override
    public CompletionStage<S3Object> get(String bucket, String key) {
        return wrapWithTelemetry(getInternal(bucket, key),
            () -> telemetry.get("GetObject", bucket, key, null));
    }

    private CompletionStage<S3ObjectMeta> getMetaInternal(String bucket, String key) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .<S3ObjectMeta>thenApply(MinioS3ObjectMeta::new);
        } catch (Exception e) {
            return handleExceptionStage(e);
        }
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
            .exceptionallyCompose(MinioS3KoraAsyncClient::handleExceptionStage);

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
            .exceptionallyCompose(MinioS3KoraAsyncClient::handleExceptionStage);

        return wrapWithTelemetry(operation,
            () -> telemetry.get("GetObjectMetas", bucket, null, null));
    }

    @Override
    public CompletionStage<S3ObjectList> list(String bucket, String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(fork -> listInternal(bucket, prefix, delimiter, limit, fork),
            () -> telemetry.get("ListObjects", bucket, prefix, null));
    }

    private CompletionStage<S3ObjectList> listInternal(String bucket, String prefix, @Nullable String delimiter, int limit, Context context) {
        return listMetaInternal(bucket, prefix, delimiter, limit, context)
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

                            return new MinioS3ObjectList(metaList, objects);
                        });
                } finally {
                    Context.clear();
                }
            });
    }

    private CompletionStage<S3ObjectMetaList> listMetaInternal(String bucket, String prefix, @Nullable String delimiter, int limit, Context context) {
        return CompletableFuture.<S3ObjectMetaList>supplyAsync(() -> {
                try {
                    context.inject();

                    var response = minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .maxKeys(limit)
                        .delimiter(delimiter)
                        .build());

                    final List<S3ObjectMeta> metas = new ArrayList<>();
                    for (Result<Item> result : response) {
                        Item item = result.get();
                        metas.add(new MinioS3ObjectMeta(item));
                    }

                    return new MinioS3ObjectMetaList(prefix, metas);
                } catch (Exception e) {
                    throw handleException(e);
                } finally {
                    Context.clear();
                }
            })
            .exceptionallyCompose(MinioS3KoraAsyncClient::handleExceptionStage);
    }

    @Override
    public CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(fork -> listMetaInternal(bucket, prefix, delimiter, limit, fork),
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
                .map(p -> listMetaInternal(bucket, p, delimiter, limitPerPrefix, fork).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(futures)
                .thenApply(_v -> Arrays.stream(futures)
                    .map(f -> ((S3ObjectMetaList) f.join()))
                    .toList());
        }, () -> telemetry.get("ListMultiObjectMetas", bucket, null, null));
    }

    @Override
    public CompletionStage<S3ObjectUpload> put(String bucket, String key, S3Body body) {
        var requestBuilder = PutObjectArgs.builder()
            .bucket(bucket)
            .object(key)
            .contentType(body.type() == null ? "application/octet-stream" : body.type());

        if (body.size() > 0 && body.encoding() != null) {
            requestBuilder.headers(Map.of(
                "content-encoding", String.valueOf(body.encoding()),
                "content-length", String.valueOf(body.size())
            ));
        } else if (body.size() > 0) {
            requestBuilder.headers(Map.of(
                "content-length", String.valueOf(body.size())
            ));
        } else if (body.encoding() != null) {
            requestBuilder.headers(Map.of(
                "content-encoding", String.valueOf(body.encoding())
            ));
        }

        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var size = body.size() > 0 ? body.size() : null;
            var context = telemetry.get("PutObject", bucket, key, size);

            final CompletionStage<S3ObjectUpload> operation;
            try {
                if (body instanceof ByteS3Body bb) {
                    operation = minioClient.putObject(requestBuilder.stream(new ByteArrayInputStream(bb.bytes()), bb.size(), -1).build())
                        .thenApply(r -> new MinioS3ObjectUpload(r.versionId()));
                } else if (body.size() > 0) {
                    operation = minioClient.putObject(requestBuilder.stream(body.asInputStream(), body.size(), minioS3ClientConfig.upload().partSize().toBytes()).build())
                        .thenApply(r -> new MinioS3ObjectUpload(r.versionId()));
                } else {
                    operation = minioClient.putObject(requestBuilder.stream(body.asInputStream(), -1, minioS3ClientConfig.upload().partSize().toBytes()).build())
                        .thenApply(r -> new MinioS3ObjectUpload(r.versionId()));
                }
            } catch (Exception e) {
                S3Exception ex = handleException(e);
                context.close(ex);
                return CompletableFuture.failedFuture(ex);
            }

            return operation
                .exceptionallyCompose(MinioS3KoraAsyncClient::handleExceptionStage)
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
        return wrapWithTelemetry(fork -> minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build()),
            () -> telemetry.get("DeleteObject", bucket, key, null));
    }

    @Override
    public CompletionStage<Void> delete(String bucket, Collection<String> keys) {
        return wrapWithTelemetry(fork -> CompletableFuture.supplyAsync(() -> {
            try {
                fork.inject();
                var response = minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucket)
                    .objects(keys.stream()
                        .map(DeleteObject::new)
                        .toList())
                    .build());

                final List<S3DeleteException.Error> errors = new ArrayList<>(keys.size());
                for (Result<DeleteError> result : response) {
                    DeleteError er = result.get();
                    errors.add(new S3DeleteException.Error(er.objectName(), er.bucketName(), er.code(), er.message()));
                }
                if (!errors.isEmpty()) {
                    throw new S3DeleteException(errors);
                }
                return null;
            } catch (Exception e) {
                throw handleException(e);
            } finally {
                Context.clear();
            }
        }), () -> telemetry.get("DeleteObjects", bucket, null, null));
    }

    @FunctionalInterface
    private interface FunctionThrowable<T, R> {

        R apply(T t) throws Throwable;
    }

    private static <T> CompletionStage<T> wrapWithTelemetry(CompletionStage<T> operationSupplier,
                                                            Supplier<S3KoraClientTelemetry.S3KoraClientTelemetryContext> contextSupplier) {
        return wrapWithTelemetry(context -> operationSupplier, contextSupplier);
    }

    private static <T> CompletionStage<T> wrapWithTelemetry(FunctionThrowable<Context, CompletionStage<T>> operationSupplier,
                                                            Supplier<S3KoraClientTelemetry.S3KoraClientTelemetryContext> contextSupplier) {
        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = contextSupplier.get();

            final CompletionStage<T> operation;
            try {
                operation = operationSupplier.apply(fork);
            } catch (Throwable e) {
                S3Exception ex = handleException(e);
                context.close(ex);
                return CompletableFuture.failedFuture(ex);
            }

            return operation
                .exceptionallyCompose(MinioS3KoraAsyncClient::handleExceptionStage)
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
        Throwable cause = e;
        if (e instanceof CompletionException ce) {
            cause = ce.getCause();
        }

        if (cause instanceof S3Exception se) {
            return se;
        } else if (cause instanceof ErrorResponseException re) {
            if ("NoSuchKey".equals(re.errorResponse().code())) {
                return S3NotFoundException.ofNoSuchKey(cause, re.errorResponse().message());
            } else if ("NoSuchBucket".equals(re.errorResponse().code())) {
                return S3NotFoundException.ofNoSuchBucket(cause, re.errorResponse().message());
            } else {
                return new S3Exception(cause, re.errorResponse().code(), re.errorResponse().message());
            }
        } else {
            return new S3Exception(cause, "unknown", "unknown");
        }
    }
}
