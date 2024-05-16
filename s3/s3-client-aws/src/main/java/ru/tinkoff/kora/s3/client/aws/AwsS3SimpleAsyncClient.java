package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.S3NotFoundException;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.aws.model.AwsS3Object;
import ru.tinkoff.kora.s3.client.aws.model.AwsS3ObjectList;
import ru.tinkoff.kora.s3.client.aws.model.AwsS3ObjectMeta;
import ru.tinkoff.kora.s3.client.aws.model.AwsS3ObjectMetaList;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.*;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

public class AwsS3SimpleAsyncClient implements S3SimpleAsyncClient {

    private final S3AsyncClient asyncClient;
    private final ExecutorService awsExecutor;

    public AwsS3SimpleAsyncClient(S3AsyncClient asyncClient, ExecutorService awsExecutor) {
        this.asyncClient = asyncClient;
        this.awsExecutor = awsExecutor;
    }

    @Override
    public CompletionStage<S3Object> get(String bucket, String key) {
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        return asyncClient.getObject(request, AsyncResponseTransformer.toBlockingInputStream())
            .thenApply(r -> ((S3Object) new AwsS3Object(request.key(), r)))
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<S3ObjectMeta> getMeta(String bucket, String key) {
        var request = GetObjectAttributesRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        return asyncClient.getObjectAttributes(request)
            .thenApply(r -> ((S3ObjectMeta) new AwsS3ObjectMeta(key, r)))
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<S3ObjectList> list(String bucket, String prefix, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .maxKeys(limit)
            .continuationToken(UUID.randomUUID().toString())
            .build();

        return asyncClient.listObjectsV2(request)
            .thenCompose(response -> {
                var futures = response.contents().stream()
                    .map(c -> get(bucket, c.key()).toCompletableFuture())
                    .toArray(CompletableFuture[]::new);

                return CompletableFuture.allOf(futures)
                    .thenApply(_v -> {
                        final List<S3Object> objects = new ArrayList<>(futures.length);
                        for (var future : futures) {
                            objects.add(((S3Object) future.join()));
                        }

                        return ((S3ObjectList) new AwsS3ObjectList(response, objects));
                    });
            })
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix, int limit) {
        var request = ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .maxKeys(limit)
            .continuationToken(UUID.randomUUID().toString())
            .build();

        return asyncClient.listObjectsV2(request)
            .thenApply(response -> ((S3ObjectMetaList) new AwsS3ObjectMetaList(response)))
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
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
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<String> put(String bucket, String key, S3Body body) {
        var requestBuilder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(body.type())
            .contentEncoding(body.encoding());

        if (body.size() > 0) {
            requestBuilder.contentLength(body.size());
        }

        var request = requestBuilder.build();
        if (body instanceof ByteS3Body bb) {
            return asyncClient.putObject(request, AsyncRequestBody.fromBytes(bb.bytes()))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        } else if (body.size() > 0) {
            return asyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), body.size(), awsExecutor))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        } else {
            return asyncClient.putObject(request, AsyncRequestBody.fromInputStream(body.asInputStream(), null, awsExecutor))
                .thenApply(PutObjectResponse::versionId)
                .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
        }
    }

    @Override
    public CompletionStage<Void> delete(String bucket, String key) {
        var request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        return asyncClient.deleteObject(request)
            .thenAccept(r -> {})
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    @Override
    public CompletionStage<List<String>> delete(String bucket, Collection<String> keys) {
        var request = DeleteObjectsRequest.builder()
            .bucket(bucket)
            .delete(Delete.builder()
                .objects(cb -> {
                    for (String key : keys) {
                        cb.key(key);
                    }
                })
                .build())
            .build();

        return asyncClient.deleteObjects(request)
            .<List<String>>thenApply(response -> {
                if (response.hasDeleted()) {
                    return response.deleted().stream()
                        .map(DeletedObject::key)
                        .toList();
                } else {
                    return Collections.emptyList();
                }
            })
            .exceptionallyCompose(AwsS3SimpleAsyncClient::handleException);
    }

    private static <T> CompletionStage<T> handleException(Throwable e) {
        if (e instanceof CompletionException ce) {
            e = ce.getCause();
        }

        if (e instanceof NoSuchKeyException || e instanceof NoSuchBucketException) {
            return CompletableFuture.failedFuture(new S3NotFoundException(e));
        } else if (e instanceof S3Exception) {
            return CompletableFuture.failedFuture(e);
        } else {
            return CompletableFuture.failedFuture(new S3Exception(e));
        }
    }
}
