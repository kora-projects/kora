package ru.tinkoff.kora.s3.client.aws;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3DeleteException;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleClient;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTelemetry;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AwsS3SimpleClient implements S3SimpleClient {

    private final software.amazon.awssdk.services.s3.S3Client client;
    private final S3ClientTelemetry telemetry;

    public AwsS3SimpleClient(software.amazon.awssdk.services.s3.S3Client client, S3ClientTelemetry telemetry) {
        this.client = client;
        this.telemetry = telemetry;
    }

    @Override
    public S3Object get(String bucket, String key) throws S3NotFoundException {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        try {
            var ctx = ru.tinkoff.kora.common.Context.current();
            var telemetryContext = telemetry.get("GET", bucket);
            ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

            var response = client.getObject(request);
            return new AwsS3Object(request.key(), response);
        } catch (NoSuchKeyException e) {
            throw new S3NotFoundException(e);
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public S3ObjectMeta getMeta(String bucket, String key) throws S3NotFoundException {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .objectAttributes(ObjectAttributes.OBJECT_SIZE)
            .build();

        try {
            var ctx = ru.tinkoff.kora.common.Context.current();
            var telemetryContext = telemetry.get("GET_META", bucket);
            ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

            var response = client.getObjectAttributes(request);
            return new AwsS3ObjectMeta(key, response);
        } catch (NoSuchKeyException e) {
            throw new S3NotFoundException(e);
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
    }

    @Override
    public S3ObjectList list(String bucket, @Nullable String prefix, int limit) {
        try {
            var metaList = listMeta(bucket, prefix, limit);

            CompletableFuture<?>[] futures = metaList.metas().stream()
                .map(meta -> CompletableFuture.supplyAsync(() -> get(bucket, meta.key())))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            final List<S3Object> objects = new ArrayList<>(futures.length);
            for (CompletableFuture<?> future : futures) {
                objects.add(((AwsS3Object) future.join()));
            }

            return new AwsS3ObjectList(metaList, objects);
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            throw new S3NotFoundException(e);
        } catch (S3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }

    @Override
    public S3ObjectMetaList listMeta(String bucket, @Nullable String prefix, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .maxKeys(limit)
            .prefix(prefix)
            .build();

        try {
            var ctx = ru.tinkoff.kora.common.Context.current();
            var telemetryContext = telemetry.get("LIST_META", bucket);
            ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

            var response = client.listObjectsV2(request);
            return new AwsS3ObjectMetaList(response);
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            throw new S3NotFoundException(e);
        } catch (S3Exception e) {
            throw e;
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
        var requestBuilder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(body.type())
            .contentEncoding(body.encoding());

        if (body.size() > 0) {
            requestBuilder.contentLength(body.size());
        }

        var request = requestBuilder.build();
        try {
            var ctx = ru.tinkoff.kora.common.Context.current();
            var telemetryContext = telemetry.get("PUT", bucket);
            ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

            if (body instanceof ByteS3Body bb) {
                return client.putObject(request, RequestBody.fromBytes(bb.bytes())).versionId();
            } else if (body.size() > 0) {
                return client.putObject(request, RequestBody.fromInputStream(body.asInputStream(), body.size())).versionId();
            } else {
                return client.putObject(request, RequestBody.fromContentProvider(body::asInputStream, body.type())).versionId();
            }
        } catch (Exception e) {
            throw new S3Exception(e);
        }

//        CreateMultipartUploadResponse multipartUpload = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
//            .key("k")
//            .bucket("b")
//            .acl(access.value())
//            .build());
//
//        client.uploadPart(UploadPartRequest.builder()
//            .partNumber(1)
//            .key("k")
//            .bucket("b")
//            .sdkPartType(SdkPartType.DEFAULT)
//            .uploadId(multipartUpload.uploadId())
//            .build(), RequestBody.fromInputStream(body.asInputStream(), 50_0000));
//
//        client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
//            .multipartUpload(CompletedMultipartUpload.builder()
//                .parts(CompletedPart.builder()
//                    .partNumber(1)
//                    .build())
//                .build())
//            .build());
    }

    @Override
    public void delete(String bucket, String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        try {
            var ctx = ru.tinkoff.kora.common.Context.current();
            var telemetryContext = telemetry.get("DELETE", bucket);
            ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

            client.deleteObject(request);
        } catch (Exception e) {
            throw new S3Exception(e);
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

        try {
            var ctx = ru.tinkoff.kora.common.Context.current();
            var telemetryContext = telemetry.get("DELETE_MANY", bucket);
            ctx.set(AwsS3ClientTelemetryInterceptor.CONTEXT_KEY, telemetryContext);

            var response = client.deleteObjects(request);
            if (response.hasErrors()) {
                var errors = response.errors().stream()
                    .map(e -> new S3DeleteException.Error(e.key(), bucket, e.code(), e.message()))
                    .toList();

                throw new S3DeleteException(errors);
            }
        } catch (S3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new S3Exception(e);
        }
    }
}
