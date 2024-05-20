package ru.tinkoff.kora.s3.client;

import ru.tinkoff.kora.s3.client.model.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface S3SimpleAsyncClient {

    CompletionStage<S3Object> get(String bucket, String key) throws S3NotFoundException;

    CompletionStage<S3ObjectMeta> getMeta(String bucket, String key) throws S3NotFoundException;

    CompletionStage<List<S3Object>> get(String bucket, Collection<String> keys) throws S3NotFoundException;

    CompletionStage<List<S3ObjectMeta>> getMeta(String bucket, Collection<String> keys) throws S3NotFoundException;

    default CompletionStage<S3ObjectList> list(String bucket, String prefix) throws S3NotFoundException {
        return list(bucket, prefix, 1000);
    }

    CompletionStage<S3ObjectList> list(String bucket, String prefix, int limit) throws S3NotFoundException;

    default CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix) throws S3NotFoundException {
        return listMeta(bucket, prefix, 1000);
    }

    CompletionStage<S3ObjectMetaList> listMeta(String bucket, String prefix, int limit) throws S3NotFoundException;

    default CompletionStage<List<S3ObjectList>> list(String bucket, Collection<String> prefixes) throws S3NotFoundException {
        return list(bucket, prefixes, 1000);
    }

    CompletionStage<List<S3ObjectList>> list(String bucket, Collection<String> prefixes, int limitPerPrefix) throws S3NotFoundException;

    default CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket, Collection<String> prefixes) throws S3NotFoundException {
        return listMeta(bucket, prefixes, 1000);
    }

    CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket, Collection<String> prefixes, int limitPerPrefix) throws S3NotFoundException;

    CompletionStage<String> put(String bucket, String key, S3Body body);

    CompletionStage<Void> delete(String bucket, String key);

    CompletionStage<Void> delete(String bucket, Collection<String> keys) throws S3DeleteException;
}
