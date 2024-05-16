package ru.tinkoff.kora.s3.client.minio;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleClient;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3Object;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3ObjectList;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3ObjectMeta;
import ru.tinkoff.kora.s3.client.minio.model.MinioS3ObjectMetaList;
import ru.tinkoff.kora.s3.client.model.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MinioS3SimpleClient implements S3SimpleClient {

    private static final int DEFAULT_PART_SIZE = 1024 * 1024 * 50; // 50Mb

    private final MinioClient minioClient;

    public MinioS3SimpleClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public S3Object get(String bucket, String key) {
        try {
            var response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());

            return new MinioS3Object(response);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code())) {
                throw new S3NotFoundException(e);
            } else {
                throw new S3Exception(e);
            }
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public S3ObjectMeta getMeta(String bucket, String key) {
        try {
            var response = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());

            return new MinioS3ObjectMeta(response);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code())) {
                throw new S3NotFoundException(e);
            } else {
                throw new S3Exception(e);
            }
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public List<S3Object> get(String bucket, Collection<String> keys) {
        final List<S3Object> objects = new ArrayList<>(keys.size());

        for (String key : keys) {
            try {
                S3Object object = get(bucket, key);
                objects.add(object);
            } catch (S3NotFoundException e) {
                // do nothing
            }
        }

        return objects;
    }

    @Override
    public List<S3ObjectMeta> getMeta(String bucket, Collection<String> keys) {
        final List<S3ObjectMeta> objects = new ArrayList<>(keys.size());

        for (String key : keys) {
            try {
                S3ObjectMeta object = getMeta(bucket, key);
                objects.add(object);
            } catch (S3NotFoundException e) {
                // do nothing
            }
        }

        return objects;
    }

    @Override
    public S3ObjectList list(String bucket, String prefix, int limit) {
        try {
            var metaList = listMeta(bucket, prefix, limit);

            CompletableFuture<?>[] futures = metaList.metas().stream()
                .map(meta -> CompletableFuture.supplyAsync(() -> get(bucket, meta.key())))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            final List<S3Object> objects = new ArrayList<>(futures.length);
            for (CompletableFuture<?> future : futures) {
                objects.add(((S3Object) future.join()));
            }

            return new MinioS3ObjectList(metaList, objects);
        } catch (S3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public S3ObjectMetaList listMeta(String bucket, String prefix, int limit) {
        try {
            var response = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .continuationToken(UUID.randomUUID().toString())
                .maxKeys(limit)
                .build());

            final List<S3ObjectMeta> metas = new ArrayList<>();
            for (Result<Item> result : response) {
                metas.add(new MinioS3ObjectMeta(result.get()));
            }

            return new MinioS3ObjectMetaList(prefix, metas);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code())) {
                throw new S3NotFoundException(e);
            } else {
                throw new S3Exception(e);
            }
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public List<S3ObjectList> list(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        final List<S3ObjectList> lists = new ArrayList<>(prefixes.size());
        for (String prefix : prefixes) {
            S3ObjectList list = list(bucket, prefix, limitPerPrefix);
            lists.add(list);
        }
        return lists;
    }

    @Override
    public List<S3ObjectMetaList> listMeta(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        final List<S3ObjectMetaList> lists = new ArrayList<>(prefixes.size());
        for (String prefix : prefixes) {
            var list = listMeta(bucket, prefix, limitPerPrefix);
            lists.add(list);
        }
        return lists;
    }

    @Override
    public String put(String bucket, String key, S3Body body) {
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
                return minioClient.putObject(requestBuilder.stream(new BufferedInputStream(new ByteArrayInputStream(bb.bytes())), bb.size(), -1).build()).versionId();
            } else if (body.size() > 0) {
                return minioClient.putObject(requestBuilder.stream(body.asInputStream(), body.size(), DEFAULT_PART_SIZE).build()).versionId();
            } else {
                return minioClient.putObject(requestBuilder.stream(body.asInputStream(), -1, DEFAULT_PART_SIZE).build()).versionId();
            }
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public List<String> delete(String bucket, Collection<String> keys) {
        try {
            var response = minioClient.removeObjects(RemoveObjectsArgs.builder()
                .bucket(bucket)
                .objects(keys.stream()
                    .map(DeleteObject::new)
                    .toList())
                .build());

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
    }
}
