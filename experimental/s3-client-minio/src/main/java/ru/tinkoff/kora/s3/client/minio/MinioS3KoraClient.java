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
import ru.tinkoff.kora.s3.client.S3KoraClient;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientTelemetry;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@ApiStatus.Experimental
public class MinioS3KoraClient implements S3KoraClient {

    private final MinioClient minioClient;
    private final MinioS3ClientConfig minioS3ClientConfig;
    private final S3KoraClientTelemetry telemetry;

    public MinioS3KoraClient(MinioClient minioClient,
                             MinioS3ClientConfig minioS3ClientConfig,
                             S3KoraClientTelemetry telemetry) {
        this.minioClient = minioClient;
        this.minioS3ClientConfig = minioS3ClientConfig;
        this.telemetry = telemetry;
    }

    @Override
    public S3Object get(String bucket, String key) throws S3NotFoundException {
        return wrapWithTelemetry(() -> getInternal(bucket, key),
            () -> telemetry.get("GetObject", bucket, key, null));
    }

    private S3Object getInternal(String bucket, String key) throws S3NotFoundException {
        try {
            var response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());

            return new MinioS3Object(response);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public S3ObjectMeta getMeta(String bucket, String key) throws S3NotFoundException {
        return wrapWithTelemetry(() -> getMetaInternal(bucket, key),
            () -> telemetry.get("GetObjectMeta", bucket, key, null));
    }

    private S3ObjectMeta getMetaInternal(String bucket, String key) throws S3NotFoundException {
        try {
            var response = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());

            return new MinioS3ObjectMeta(response);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public List<S3Object> get(String bucket, Collection<String> keys) {
        return wrapWithTelemetry(() -> {
            final List<S3Object> objects = new ArrayList<>(keys.size());
            for (String key : keys) {
                try {
                    S3Object object = getInternal(bucket, key);
                    objects.add(object);
                } catch (S3NotFoundException e) {
                    // do nothing
                }
            }
            return objects;
        }, () -> telemetry.get("GetObjects", bucket, null, null));
    }

    @Override
    public List<S3ObjectMeta> getMeta(String bucket, Collection<String> keys) {
        return wrapWithTelemetry(() -> {
            final List<S3ObjectMeta> metas = new ArrayList<>(keys.size());
            for (String key : keys) {
                try {
                    S3ObjectMeta meta = getMeta(bucket, key);
                    metas.add(meta);
                } catch (S3NotFoundException e) {
                    // do nothing
                }
            }
            return metas;
        }, () -> telemetry.get("GetObjectMetas", bucket, null, null));
    }

    @Override
    public S3ObjectList list(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(() -> listInternal(bucket, prefix, delimiter, limit),
            () -> telemetry.get("ListObjects", bucket, prefix, null));
    }

    private S3ObjectList listInternal(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        var metaList = listMetaInternal(bucket, prefix, delimiter, limit);

        final List<S3Object> objects = new ArrayList<>(metaList.metas().size());
        for (S3ObjectMeta meta : metaList.metas()) {
            S3Object object = getInternal(bucket, meta.key());
            objects.add(object);
        }

        return new MinioS3ObjectList(metaList, objects);
    }

    @Override
    public S3ObjectMetaList listMeta(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(() -> listMetaInternal(bucket, prefix, delimiter, limit),
            () -> telemetry.get("ListObjectMetas", bucket, prefix, null));
    }

    private S3ObjectMetaList listMetaInternal(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        try {
            var response = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(limit)
                .delimiter(delimiter)
                .build());

            final List<S3ObjectMeta> metas = new ArrayList<>();
            for (Result<Item> result : response) {
                metas.add(new MinioS3ObjectMeta(result.get()));
            }

            return new MinioS3ObjectMetaList(prefix, metas);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public List<S3ObjectList> list(String bucket, Collection<String> prefixes, @Nullable String delimiter, int limitPerPrefix) {
        return wrapWithTelemetry(() -> {
            final List<S3ObjectList> lists = new ArrayList<>(prefixes.size());
            for (String prefix : prefixes) {
                S3ObjectList list = listInternal(bucket, prefix, delimiter, limitPerPrefix);
                lists.add(list);
            }
            return lists;
        }, () -> telemetry.get("ListMultiObjects", bucket, null, null));
    }

    @Override
    public List<S3ObjectMetaList> listMeta(String bucket, Collection<String> prefixes, @Nullable String delimiter, int limitPerPrefix) {
        return wrapWithTelemetry(() -> {
            final List<S3ObjectMetaList> lists = new ArrayList<>(prefixes.size());
            for (String prefix : prefixes) {
                var list = listMeta(bucket, prefix, delimiter, limitPerPrefix);
                lists.add(list);
            }
            return lists;
        }, () -> telemetry.get("ListMultiObjectMetas", bucket, null, null));
    }

    @Override
    public S3ObjectUpload put(String bucket, String key, S3Body body) {
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

            var context = telemetry.get("PutObject", bucket, key, body.size() > 0 ? body.size() : null);
            try {
                if (body instanceof ByteS3Body bb) {
                    final ObjectWriteResponse response = minioClient.putObject(requestBuilder.stream(new BufferedInputStream(new ByteArrayInputStream(bb.bytes())), bb.size(), -1).build());
                    return new MinioS3ObjectUpload(response.versionId());
                } else if (body.size() > 0) {
                    final ObjectWriteResponse response = minioClient.putObject(requestBuilder.stream(body.asInputStream(), body.size(), minioS3ClientConfig.upload().partSize().toBytes()).build());
                    return new MinioS3ObjectUpload(response.versionId());
                } else {
                    final ObjectWriteResponse response = minioClient.putObject(requestBuilder.stream(body.asInputStream(), -1, minioS3ClientConfig.upload().partSize().toBytes()).build());
                    return new MinioS3ObjectUpload(response.versionId());
                }
            } catch (Exception e) {
                S3Exception ex = handleException(e);
                context.close(ex);
                throw ex;
            }
        } finally {
            ctx.inject();
        }
    }

    @Override
    public void delete(String bucket, String key) {
        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = telemetry.get("DeleteObject", bucket, key, null);
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
                context.close();
            } catch (Exception e) {
                S3Exception ex = handleException(e);
                context.close(ex);
                throw ex;
            }
        } finally {
            ctx.inject();
        }
    }

    @Override
    public void delete(String bucket, Collection<String> keys) {
        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = telemetry.get("DeleteObjects", bucket, null, null);
            try {
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

                context.close();
            } catch (Exception e) {
                S3Exception ex = handleException(e);
                context.close(ex);
                throw ex;
            }
        } finally {
            ctx.inject();
        }
    }

    private static <T> T wrapWithTelemetry(Supplier<T> operationSupplier,
                                           Supplier<S3KoraClientTelemetry.S3KoraClientTelemetryContext> contextSupplier) {
        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = contextSupplier.get();
            try {
                T value = operationSupplier.get();
                context.close();
                return value;
            } catch (Exception e) {
                S3Exception ex = handleException(e);
                context.close(ex);
                throw ex;
            }
        } finally {
            ctx.inject();
        }
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
            return new S3Exception(cause, cause.getClass().getSimpleName(), cause.getMessage());
        }
    }
}
