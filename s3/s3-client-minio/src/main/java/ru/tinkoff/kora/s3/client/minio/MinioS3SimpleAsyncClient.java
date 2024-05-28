package ru.tinkoff.kora.s3.client.minio;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.minio.MinioS3ClientTelemetryInterceptor.Operation;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static ru.tinkoff.kora.s3.client.minio.MinioS3ClientTelemetryInterceptor.OPERATION_KEY;

public class MinioS3SimpleAsyncClient implements S3SimpleAsyncClient {

    private static final int DEFAULT_PART_SIZE = 1024 * 1024 * 50; // 50Mb

    private final MinioAsyncClient minioClient;
    private final S3ClientTelemetry telemetry;

    public MinioS3SimpleAsyncClient(MinioAsyncClient minioClient, S3ClientTelemetry telemetry) {
        this.minioClient = minioClient;
        this.telemetry = telemetry;
    }

    @Override
    public CompletableFuture<S3Object> get(String bucket, String key) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("GET", bucket));

            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .<S3Object>thenApply(MinioS3Object::new)
                .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletableFuture<S3ObjectMeta> getMeta(String bucket, String key) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("GET", bucket));

            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .<S3ObjectMeta>thenApply(MinioS3ObjectMeta::new)
                .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
        } catch (Exception e) {
            throw handleException(e);
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
    public CompletableFuture<S3ObjectList> list(String bucket, String prefix, int limit) {
        var telemetryContext = telemetry.get("LIST", bucket);

        return listMeta(bucket, prefix, limit)
            .<S3ObjectList>thenCompose(metaList -> {
                var futures = metaList.metas().stream()
                    .map(meta -> get(bucket, meta.key()))
                    .toArray(CompletableFuture[]::new);

                return CompletableFuture.allOf(futures).thenApply(_v -> {
                    final List<S3Object> objects = new ArrayList<>(futures.length);
                    for (CompletableFuture<?> future : futures) {
                        objects.add(((S3Object) future.join()));
                    }

                    telemetryContext.close(200);
                    return new MinioS3ObjectList(metaList, objects);
                });
            })
            .exceptionallyCompose(e -> handleExceptionAndTelemetryStage(e, telemetryContext));
    }

    @Override
    public CompletableFuture<S3ObjectMetaList> listMeta(String bucket, String prefix, int limit) {
        return CompletableFuture.<S3ObjectMetaList>supplyAsync(() -> {
                var ctx = Context.current();
                ctx.set(OPERATION_KEY, new Operation("LIST_META", bucket));

                var response = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .maxKeys(limit)
                    .build());

                try {
                    final List<S3ObjectMeta> metas = new ArrayList<>();
                    for (Result<Item> result : response) {
                        Item item = result.get();
                        metas.add(new MinioS3ObjectMeta(item));
                    }

                    return new MinioS3ObjectMetaList(prefix, metas);
                } catch (Exception e) {
                    throw handleException(e);
                } finally {
                    ctx.remove(OPERATION_KEY);
                }
            })
            .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
    }

    @Override
    public CompletionStage<List<S3ObjectList>> list(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var telemetryContext = telemetry.get("LIST_MANY", bucket);

        var futures = prefixes.stream()
            .map(p -> list(bucket, p, limitPerPrefix).toCompletableFuture())
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
    public CompletableFuture<S3ObjectUpload> put(String bucket, String key, S3Body body) {
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
            ctx.set(OPERATION_KEY, new Operation("PUT", bucket));

            if (body instanceof ByteS3Body bb) {
                return minioClient.putObject(requestBuilder.stream(new ByteArrayInputStream(bb.bytes()), bb.size(), -1).build())
                    .thenApply(r -> ((S3ObjectUpload) new MinioS3ObjectUpload(r.versionId())))
                    .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
            } else if (body.size() > 0) {
                return minioClient.putObject(requestBuilder.stream(body.asInputStream(), body.size(), DEFAULT_PART_SIZE).build())
                    .thenApply(r -> ((S3ObjectUpload) new MinioS3ObjectUpload(r.versionId())))
                    .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
            } else {
                return minioClient.putObject(requestBuilder.stream(body.asInputStream(), -1, DEFAULT_PART_SIZE).build())
                    .thenApply(r -> ((S3ObjectUpload) new MinioS3ObjectUpload(r.versionId())))
                    .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
            }
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletableFuture<Void> delete(String bucket, String key) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("DELETE", bucket));

            return minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .exceptionallyCompose(MinioS3SimpleAsyncClient::handleExceptionStage);
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public CompletableFuture<Void> delete(String bucket, Collection<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            var ctx = Context.current();
            try {
                ctx.set(OPERATION_KEY, new Operation("DELETE_MANY", bucket));

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
                ctx.remove(OPERATION_KEY);
            }
        });
    }

    private static <T> CompletionStage<T> handleExceptionAndTelemetryStage(Throwable e, S3ClientTelemetry.S3ClientTelemetryContext telemetryContext) {
        Throwable cause = e;
        if (e instanceof S3Exception se) {
            cause = se.getCause();
        }

        if (cause instanceof ErrorResponseException re) {
            telemetryContext.close(re.response().code(), e);
        } else {
            telemetryContext.close(-1, e);
        }
        return handleExceptionStage(e);
    }

    private static <T> CompletionStage<T> handleExceptionStage(Throwable e) {
        return CompletableFuture.failedFuture(handleException(e));
    }

    private static S3Exception handleException(Throwable e) {
        Throwable cause = e;
        if (e instanceof CompletionException ce) {
            cause = ce.getCause();
        }

        if (cause instanceof ErrorResponseException re) {
            if ("NoSuchKey".equals(re.errorResponse().code())) {
                return S3NotFoundException.ofNoSuchKey(cause, re.errorResponse().message());
            } else if ("NoSuchBucket".equals(re.errorResponse().code())) {
                return S3NotFoundException.ofNoSuchBucket(cause, re.errorResponse().message());
            } else {
                return new S3Exception(cause, re.errorResponse().code(), re.errorResponse().message());
            }
        } else if (cause instanceof S3Exception se) {
            return se;
        } else {
            return new S3Exception(cause, "unknown", "unknown");
        }
    }
}
