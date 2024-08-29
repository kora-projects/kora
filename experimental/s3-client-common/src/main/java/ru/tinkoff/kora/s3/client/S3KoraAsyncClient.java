package ru.tinkoff.kora.s3.client;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import ru.tinkoff.kora.s3.client.model.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

@ApiStatus.Experimental
public interface S3KoraAsyncClient {

    CompletionStage<S3Object> get(String bucket, String key) throws S3NotFoundException;

    CompletionStage<S3ObjectMeta> getMeta(String bucket, String key) throws S3NotFoundException;

    CompletionStage<List<S3Object>> get(String bucket, Collection<String> keys) throws S3NotFoundException;

    CompletionStage<List<S3ObjectMeta>> getMeta(String bucket, Collection<String> keys) throws S3NotFoundException;

    default CompletionStage<S3ObjectList> list(String bucket) throws S3NotFoundException {
        return list(bucket, (String) null, null, 1000);
    }

    default CompletionStage<S3ObjectList> list(String bucket,
                                               @Nullable String prefix) throws S3NotFoundException {
        return list(bucket, prefix, null, 1000);
    }

    CompletionStage<S3ObjectList> list(String bucket,
                                       @Nullable String prefix,
                                       @Nullable String delimiter,
                                       @Range(from = 1, to = 1000) int limit) throws S3NotFoundException;

    default CompletionStage<S3ObjectMetaList> listMeta(String bucket) {
        return listMeta(bucket, (String) null, null, 1000);
    }

    default CompletionStage<S3ObjectMetaList> listMeta(String bucket,
                                                       @Nullable String prefix) {
        return listMeta(bucket, prefix, null, 1000);
    }

    CompletionStage<S3ObjectMetaList> listMeta(String bucket,
                                               @Nullable String prefix,
                                               @Nullable String delimiter,
                                               @Range(from = 1, to = 1000) int limit);

    default CompletionStage<List<S3ObjectList>> list(String bucket,
                                                     Collection<String> prefixes) throws S3NotFoundException {
        return list(bucket, prefixes, null, 1000);
    }

    CompletionStage<List<S3ObjectList>> list(String bucket,
                                             Collection<String> prefixes,
                                             @Nullable String delimiter,
                                             @Range(from = 1, to = 1000) int limitPerPrefix) throws S3NotFoundException;

    default CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket,
                                                             Collection<String> prefixes) {
        return listMeta(bucket, prefixes, null, 1000);
    }

    CompletionStage<List<S3ObjectMetaList>> listMeta(String bucket,
                                                     Collection<String> prefixes,
                                                     @Nullable String delimiter,
                                                     @Range(from = 1, to = 1000) int limitPerPrefix);

    CompletionStage<S3ObjectUpload> put(String bucket, String key, S3Body body);

    CompletionStage<Void> delete(String bucket, String key);

    CompletionStage<Void> delete(String bucket, Collection<String> keys) throws S3DeleteException;
}
