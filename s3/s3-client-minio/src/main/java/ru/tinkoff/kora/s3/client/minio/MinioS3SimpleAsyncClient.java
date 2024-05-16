package ru.tinkoff.kora.s3.client.minio;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3Object;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3ObjectList;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3ObjectMeta;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3ObjectMetaList;
import ru.tinkoff.kora.s3.client.model.*;

import java.io.ByteArrayInputStream;
import java.security.InvalidKeyException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class MinioS3SimpleAsyncClient implements S3SimpleAsyncClient {

    private static final int DEFAULT_PART_SIZE = 1024 * 1024 * 50; // 50Mb

    private final MinioAsyncClient minioClient;

    public MinioS3SimpleAsyncClient(MinioAsyncClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public CompletableFuture<S3Object> get(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .<S3Object>thenApply(MinioS3Object::new)
                .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public CompletableFuture<S3ObjectMeta> getMeta(String bucket, String key) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .<S3ObjectMeta>thenApply(MinioS3ObjectMeta::new)
                .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
        } catch (Exception e) {
            throw new S3Exception(e);
        }
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
            .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
    }


    @Override
    public CompletableFuture<S3ObjectList> list(String bucket, String prefix, int limit) {
        try {
            return listMeta(bucket, prefix, limit).<S3ObjectList>thenCompose(metaList -> {
                var futures = metaList.metas().stream()
                    .map(meta -> get(bucket, meta.key()))
                    .toArray(CompletableFuture[]::new);

                return CompletableFuture.allOf(futures).thenApply(_v -> {
                    final List<S3Object> objects = new ArrayList<>(futures.length);
                    for (CompletableFuture<?> future : futures) {
                        objects.add(((S3Object) future.join()));
                    }

                    return new MinioS3ObjectList(metaList, objects);
                });
            }).exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
        } catch (S3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public CompletableFuture<S3ObjectMetaList> listMeta(String bucket, String prefix, int limit) {
        var response = minioClient.listObjects(ListObjectsArgs.builder()
            .bucket(bucket)
            .prefix(prefix)
            .continuationToken(UUID.randomUUID().toString())
            .maxKeys(limit)
            .build());

        return CompletableFuture.<S3ObjectMetaList>supplyAsync(() -> {
                try {
                    final List<S3ObjectMeta> metas = new ArrayList<>();
                    for (Result<Item> result : response) {
                        metas.add(new MinioS3ObjectMeta(result.get()));
                    }

                    return new MinioS3ObjectMetaList(prefix, metas);
                } catch (InvalidKeyException e) {
                    throw new S3NotFoundException(e);
                } catch (Exception e) {
                    throw new S3Exception(e);
                }
            })
            .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletableFuture<String> put(String bucket, String key, S3Body body) {
        var requestBuilder = PutObjectArgs.builder()
            .bucket(bucket)
            .object(key)
            .contentType(body.type());

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

        try {
            if (body instanceof ByteS3Body bb) {
                return minioClient.putObject(requestBuilder.stream(new ByteArrayInputStream(bb.bytes()), bb.size(), -1).build())
                    .thenApply(ObjectWriteResponse::versionId)
                    .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
            } else if (body.size() > 0) {
                return minioClient.putObject(requestBuilder.stream(body.asInputStream(), body.size(), DEFAULT_PART_SIZE).build())
                    .thenApply(ObjectWriteResponse::versionId)
                    .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
            } else {
                return minioClient.putObject(requestBuilder.stream(body.asInputStream(), -1, DEFAULT_PART_SIZE).build())
                    .thenApply(ObjectWriteResponse::versionId)
                    .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
            }
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public CompletableFuture<Void> delete(String bucket, String key) {
        try {
            return minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())
                .exceptionallyCompose(MinioS3SimpleAsyncClient::handleException);
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public CompletableFuture<List<String>> delete(String bucket, Collection<String> keys) {
        var response = minioClient.removeObjects(RemoveObjectsArgs.builder()
            .bucket(bucket)
            .objects(keys.stream()
                .map(DeleteObject::new)
                .toList())
            .build());

        return CompletableFuture.supplyAsync(() -> {
            try {
                final List<DeleteError> errors = new ArrayList<>(keys.size());
                for (Result<DeleteError> result : response) {
                    DeleteError deleteError = result.get();
                    errors.add(deleteError);
                }

                if (errors.isEmpty()) {
                    return new ArrayList<>(keys);
                } else {
                    throw new S3MinioDeleteException(errors);
                }
            } catch (Exception e) {
                throw new S3Exception(e);
            }
        });
    }

    private static <T> CompletionStage<T> handleException(Throwable e) {
        if (e instanceof CompletionException ce) {
            e = ce.getCause();
        }

        if (e instanceof ErrorResponseException re) {
            if ("NoSuchKey".equals(re.errorResponse().code()) || "NoSuchBucket".equals(re.errorResponse().code())) {
                return CompletableFuture.failedFuture(new S3NotFoundException(e));
            } else {
                return CompletableFuture.failedFuture(new S3Exception(e));
            }
        } else if (e instanceof S3Exception) {
            return CompletableFuture.failedFuture(e);
        } else {
            return CompletableFuture.failedFuture(new S3Exception(e));
        }
    }
}
