package ru.tinkoff.kora.s3.client;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import ru.tinkoff.kora.s3.client.model.*;

import java.util.Collection;
import java.util.List;

@ApiStatus.Experimental
public interface S3KoraClient {

    S3Object get(String bucket, String key) throws S3NotFoundException;

    S3ObjectMeta getMeta(String bucket, String key) throws S3NotFoundException;

    List<S3Object> get(String bucket, Collection<String> keys) throws S3NotFoundException;

    List<S3ObjectMeta> getMeta(String bucket, Collection<String> keys) throws S3NotFoundException;

    default S3ObjectList list(String bucket) throws S3NotFoundException {
        return list(bucket, ((String) null), null, 1000);
    }

    default S3ObjectList list(String bucket,
                              @Nullable String prefix) throws S3NotFoundException {
        return list(bucket, prefix, null, 1000);
    }

    S3ObjectList list(String bucket,
                      @Nullable String prefix,
                      @Nullable String delimiter,
                      @Range(from = 1, to = 1000) int limit) throws S3NotFoundException;

    default S3ObjectMetaList listMeta(String bucket) throws S3NotFoundException {
        return listMeta(bucket, ((String) null), null, 1000);
    }

    default S3ObjectMetaList listMeta(String bucket,
                                      @Nullable String prefix) throws S3NotFoundException {
        return listMeta(bucket, prefix, null, 1000);
    }

    S3ObjectMetaList listMeta(String bucket,
                              @Nullable String prefix,
                              @Nullable String delimiter,
                              @Range(from = 1, to = 1000) int limit) throws S3NotFoundException;

    default List<S3ObjectList> list(String bucket,
                                    Collection<String> prefixes) throws S3NotFoundException {
        return list(bucket, prefixes, null, 1000);
    }

    List<S3ObjectList> list(String bucket,
                            Collection<String> prefixes,
                            @Nullable String delimiter,
                            @Range(from = 1, to = 1000) int limitPerPrefix) throws S3NotFoundException;

    default List<S3ObjectMetaList> listMeta(String bucket,
                                            Collection<String> prefixes) throws S3NotFoundException {
        return listMeta(bucket, prefixes, null, 1000);
    }

    List<S3ObjectMetaList> listMeta(String bucket,
                                    Collection<String> prefixes,
                                    @Nullable String delimiter,
                                    @Range(from = 1, to = 1000) int limitPerPrefix) throws S3NotFoundException;

    S3ObjectUpload put(String bucket, String key, S3Body body);

    void delete(String bucket, String key);

    void delete(String bucket, Collection<String> keys) throws S3DeleteException;
}
