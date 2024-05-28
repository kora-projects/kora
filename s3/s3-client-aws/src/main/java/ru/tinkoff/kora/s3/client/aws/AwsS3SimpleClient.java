package ru.tinkoff.kora.s3.client.aws;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.*;
import ru.tinkoff.kora.s3.client.aws.AwsS3ClientTelemetryInterceptor.Operation;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;

import static ru.tinkoff.kora.s3.client.aws.AwsS3ClientTelemetryInterceptor.OPERATION_KEY;

public class AwsS3SimpleClient implements S3SimpleClient {

    private final S3Client syncClient;
    private final S3SimpleAsyncClient asyncClient;
    private final S3ClientTelemetry telemetry;
    private final AwsS3ClientConfig awsS3ClientConfig;

    public AwsS3SimpleClient(S3Client syncClient,
                             S3SimpleAsyncClient asyncClient, S3ClientTelemetry telemetry,
                             AwsS3ClientConfig awsS3ClientConfig) {
        this.syncClient = syncClient;
        this.telemetry = telemetry;
        this.awsS3ClientConfig = awsS3ClientConfig;
        this.asyncClient = asyncClient;
    }

    @Override
    public S3Object get(String bucket, String key) throws S3NotFoundException {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("GET", bucket));

            return getInternal(bucket, key);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    private S3Object getInternal(String bucket, String key) throws S3NotFoundException {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        try {
            var response = syncClient.getObject(request);
            return new AwsS3Object(request.key(), response);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public S3ObjectMeta getMeta(String bucket, String key) throws S3NotFoundException {
        var ctx = Context.current();

        try {
            ctx.set(OPERATION_KEY, new Operation("GET_META", bucket));

            return getMetaInternal(bucket, key);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    private S3ObjectMeta getMetaInternal(String bucket, String key) throws S3NotFoundException {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .objectAttributes(ObjectAttributes.OBJECT_SIZE)
            .build();

        try {
            var response = syncClient.getObjectAttributes(request);
            return new AwsS3ObjectMeta(key, response);
        } catch (Exception e) {
            throw handleException(e);
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
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }

        telemetryContext.close(200);
        return objects;
    }

    @Override
    public List<S3ObjectMeta> getMeta(String bucket, Collection<String> keys) {
        var telemetryContext = telemetry.get("GET_META_MANY", bucket);

        final List<S3ObjectMeta> metas = new ArrayList<>(keys.size());

        try {
            for (String key : keys) {
                try {
                    S3ObjectMeta meta = getMeta(bucket, key);
                    metas.add(meta);
                } catch (S3NotFoundException e) {
                    // do nothing
                }
            }
        } catch (Exception e) {
            throw handleExceptionAndTelemetry(e, telemetryContext);
        }

        telemetryContext.close(200);
        return metas;
    }

    @Override
    public S3ObjectList list(String bucket, @Nullable String prefix, int limit) {
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

            return new AwsS3ObjectList(((AwsS3ObjectMetaList) metaList).response(), objects);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public S3ObjectMetaList listMeta(String bucket, @Nullable String prefix, int limit) {
        var ctx = Context.current();
        try {
            ctx.set(OPERATION_KEY, new Operation("LIST_META", bucket));
            return listMetaInternal(bucket, prefix, limit);
        } finally {
            ctx.remove(OPERATION_KEY);
        }
    }

    private S3ObjectMetaList listMetaInternal(String bucket, @Nullable String prefix, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .maxKeys(limit)
            .prefix(prefix)
            .build();

        try {
            var response = syncClient.listObjectsV2(request);
            return new AwsS3ObjectMetaList(response);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @Override
    public List<S3ObjectList> list(String bucket, Collection<String> prefixes, int limitPerPrefix) {
        var telemetryContext = telemetry.get("LIST_MANY", bucket);

        final List<S3ObjectList> lists = new ArrayList<>(prefixes.size());
        try {
            for (String prefix : prefixes) {
                S3ObjectList list = list(bucket, prefix, limitPerPrefix);
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

        final List<S3ObjectMetaList> lists = new ArrayList<>(prefixes.size());

        try {
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
            ctx.set(OPERATION_KEY, new Operation("PUT", bucket));
            if (body instanceof ByteS3Body bb) {
                final PutObjectResponse response = syncClient.putObject(request, RequestBody.fromBytes(bb.bytes()));
                return new AwsS3ObjectUpload(response);
            } else {
                final PutObjectResponse response = syncClient.putObject(request, RequestBody.fromContentProvider(body::asInputStream, body.size(), body.type()));
                return new AwsS3ObjectUpload(response);
            }
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
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
            ctx.set(OPERATION_KEY, new Operation("PUT", bucket));

            syncClient.deleteObject(request);
        } catch (Exception e) {
            throw handleException(e);
        } finally {
            ctx.remove(OPERATION_KEY);
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
            ctx.set(OPERATION_KEY, new Operation("DELETE_MANY", bucket));

            var response = syncClient.deleteObjects(request);
            if (response.hasErrors()) {
                var errors = response.errors().stream()
                    .map(e -> new S3DeleteException.Error(e.key(), bucket, e.code(), e.message()))
                    .toList();

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
        if (cause instanceof SdkServiceException se) {
            telemetryContext.close(se.statusCode(), e);
        } else {
            telemetryContext.close(-1, e);
        }
        throw handleException(e);
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
