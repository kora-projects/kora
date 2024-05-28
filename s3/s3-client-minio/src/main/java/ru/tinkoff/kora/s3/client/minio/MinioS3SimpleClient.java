package ru.tinkoff.kora.s3.client.minio;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleClient;
import ru.tinkoff.kora.s3.client.minio.MinioS3ClientTelemetryInterceptor.Operation;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static ru.tinkoff.kora.s3.client.minio.MinioS3ClientTelemetryInterceptor.OPERATION_KEY;

public class MinioS3SimpleClient implements S3SimpleClient {

    private static final int DEFAULT_PART_SIZE = 1024 * 1024 * 50; // 50Mb

    private final MinioClient minioClient;
    private final S3ClientTelemetry telemetry;

    public MinioS3SimpleClient(MinioClient minioClient, S3ClientTelemetry telemetry) {
        this.minioClient = minioClient;
        this.telemetry = telemetry;
    }

    @Override
    public S3Object get(String bucket, String key) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("GET", bucket));

            var response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());

            return new MinioS3Object(response);
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public S3ObjectMeta getMeta(String bucket, String key) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("GET_META", bucket));

            var response = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());

            return new MinioS3ObjectMeta(response);
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public List<S3Object> get(String bucket, Collection<String> keys) {
        var telemetryContext = telemetry.get("GET_MANY", bucket);

        final List<S3Object> objects = new ArrayList<>(keys.size());

        try {
            for (String key : keys) {
                try {
                    S3Object object = get(bucket, key);
                    objects.add(object);
                } catch (S3NotFoundException e) {
                    // do nothing
                }
            }

            telemetryContext.close(200);
            return objects;
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }
    }

    @Override
    public List<S3ObjectMeta> getMeta(String bucket, Collection<String> keys) {
        var telemetryContext = telemetry.get("GET_META_MANY", bucket);

        try {
            final List<S3ObjectMeta> objects = new ArrayList<>(keys.size());
            for (String key : keys) {
                try {
                    S3ObjectMeta object = getMeta(bucket, key);
                    objects.add(object);
                } catch (S3NotFoundException e) {
                    // do nothing
                }
            }

            telemetryContext.close(200);
            return objects;
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }
    }

    @Override
    public S3ObjectList list(String bucket, String prefix, int limit) {
        var telemetryContext = telemetry.get("LIST", bucket);

        try {
            var objectList = listInternal(bucket, prefix, limit);
            telemetryContext.close(200);
            return objectList;
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }
    }

    private S3ObjectList listInternal(String bucket, @Nullable String prefix, int limit) {
        try {
            var metaList = listMeta(bucket, prefix, limit);

            final List<S3Object> objects = new ArrayList<>(metaList.metas().size());
            for (S3ObjectMeta meta : metaList.metas()) {
                S3Object object = get(bucket, meta.key());
                objects.add(object);
            }

            return new MinioS3ObjectList(metaList, objects);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public S3ObjectMetaList listMeta(String bucket, @Nullable String prefix, int limit) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("LIST_META", bucket));

            var response = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(limit)
                .build());

            final List<S3ObjectMeta> metas = new ArrayList<>();
            for (Result<Item> result : response) {
                metas.add(new MinioS3ObjectMeta(result.get()));
            }

            return new MinioS3ObjectMetaList(prefix, metas);
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public List<S3ObjectList> list(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var telemetryContext = telemetry.get("LIST_MANY", bucket);

        try {
            final List<S3ObjectList> lists = new ArrayList<>(prefixes.size());
            for (String prefix : prefixes) {
                S3ObjectList list = listInternal(bucket, prefix, limitPerPrefix);
                lists.add(list);
            }

            telemetryContext.close(200);
            return lists;
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }
    }

    @Override
    public List<S3ObjectMetaList> listMeta(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var telemetryContext = telemetry.get("LIST_META_MANY", bucket);

        try {
            final List<S3ObjectMetaList> lists = new ArrayList<>(prefixes.size());
            for (String prefix : prefixes) {
                var list = listMeta(bucket, prefix, limitPerPrefix);
                lists.add(list);
            }

            telemetryContext.close(200);
            return lists;
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }
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
            ctx.set(OPERATION_KEY, new Operation("PUT", bucket));

            if (body instanceof ByteS3Body bb) {
                final ObjectWriteResponse response = minioClient.putObject(requestBuilder.stream(new BufferedInputStream(new ByteArrayInputStream(bb.bytes())), bb.size(), -1).build());
                return new MinioS3ObjectUpload(response.versionId());
            } else if (body.size() > 0) {
                final ObjectWriteResponse response = minioClient.putObject(requestBuilder.stream(body.asInputStream(), body.size(), DEFAULT_PART_SIZE).build());
                return new MinioS3ObjectUpload(response.versionId());
            } else {
                final ObjectWriteResponse response = minioClient.putObject(requestBuilder.stream(body.asInputStream(), -1, DEFAULT_PART_SIZE).build());
                return new MinioS3ObjectUpload(response.versionId());
            }
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("DELETE", bucket));

            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    @Override
    public void delete(String bucket, Collection<String> keys) {
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
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    private static S3Exception handleExceptionAndTelemetry(Throwable e, S3ClientTelemetry.S3ClientTelemetryContext telemetryContext) {
        Throwable cause = e;
        if (e instanceof S3Exception se) {
            cause = se.getCause();
        }

        if (cause instanceof ErrorResponseException re) {
            telemetryContext.close(re.response().code(), e);
        } else {
            telemetryContext.close(-1, e);
        }
        throw handleException(e);
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
