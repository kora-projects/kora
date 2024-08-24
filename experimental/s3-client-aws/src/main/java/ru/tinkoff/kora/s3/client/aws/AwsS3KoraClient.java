package ru.tinkoff.kora.s3.client.aws;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.*;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientTelemetry;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@ApiStatus.Experimental
public class AwsS3KoraClient implements S3KoraClient {

    private final S3Client syncClient;
    private final S3KoraAsyncClient asyncClient;
    private final S3KoraClientTelemetry telemetry;
    private final AwsS3ClientConfig awsS3ClientConfig;

    public AwsS3KoraClient(S3Client syncClient,
                           S3KoraAsyncClient asyncClient,
                           S3KoraClientTelemetry telemetry,
                           AwsS3ClientConfig awsS3ClientConfig) {
        this.syncClient = syncClient;
        this.telemetry = telemetry;
        this.awsS3ClientConfig = awsS3ClientConfig;
        this.asyncClient = asyncClient;
    }

    @Override
    public S3Object get(String bucket, String key) throws S3NotFoundException {
        return wrapWithTelemetry(() -> getInternal(bucket, key),
            () -> telemetry.get("GetObject", bucket, key, null));
    }

    private S3Object getInternal(String bucket, String key) throws S3NotFoundException {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var response = syncClient.getObject(request);
        return new AwsS3Object(request.key(), response);
    }

    @Override
    public S3ObjectMeta getMeta(String bucket, String key) throws S3NotFoundException {
        return wrapWithTelemetry(() -> getMetaInternal(bucket, key),
            () -> telemetry.get("GetObjectMeta", bucket, key, null));
    }

    private S3ObjectMeta getMetaInternal(String bucket, String key) throws S3NotFoundException {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .objectAttributes(ObjectAttributes.OBJECT_SIZE)
            .build();

        var response = syncClient.getObjectAttributes(request);
        return new AwsS3ObjectMeta(key, response);
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

        return new AwsS3ObjectList(((AwsS3ObjectMetaList) metaList).response(), objects);
    }

    @Override
    public S3ObjectMetaList listMeta(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        return wrapWithTelemetry(() -> listMetaInternal(bucket, prefix, delimiter, limit),
            () -> telemetry.get("ListObjectMetas", bucket, prefix, null));
    }

    private S3ObjectMetaList listMetaInternal(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .maxKeys(limit)
            .prefix(prefix)
            .delimiter(delimiter)
            .build();

        var response = syncClient.listObjectsV2(request);
        return new AwsS3ObjectMetaList(response);
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
        if (body instanceof PublisherS3Body || body.size() < 0 || body.size() > awsS3ClientConfig.upload().bufferSize()) {
            try {
                return asyncClient.put(bucket, key, body).toCompletableFuture().join();
            } catch (Exception e) {
                throw handleException(e);
            }
        }

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

            var context = telemetry.get("PutObject", bucket, key, body.size() > 0 ? body.size() : null);
            try {
                if (body instanceof ByteS3Body bb) {
                    final PutObjectResponse response = syncClient.putObject(request, RequestBody.fromBytes(bb.bytes()));
                    context.close();
                    return new AwsS3ObjectUpload(response);
                } else {
                    final PutObjectResponse response = syncClient.putObject(request, RequestBody.fromContentProvider(body::asInputStream, body.size(), body.type()));
                    context.close();
                    return new AwsS3ObjectUpload(response);
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
        var request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        var ctx = Context.current();
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = telemetry.get("DeleteObject", bucket, key, null);
            try {
                syncClient.deleteObject(request);
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
        try {
            var fork = ctx.fork();
            fork.inject();

            var context = telemetry.get("DeleteObjects", bucket, null, null);
            try {
                var response = syncClient.deleteObjects(request);
                if (response.hasErrors()) {
                    var errors = response.errors().stream()
                        .map(e -> new S3DeleteException.Error(e.key(), bucket, e.code(), e.message()))
                        .toList();

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
