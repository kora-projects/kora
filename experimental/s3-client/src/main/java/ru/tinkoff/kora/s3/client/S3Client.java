package ru.tinkoff.kora.s3.client;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.exception.S3ClientException;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectUploadResult;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@ApiStatus.Experimental
public interface S3Client {
    sealed interface RangeData {
        /**
         * @param from An integer indicating the start position of the request range, inclusive
         * @param to   An integer indicating the end position of the requested range, exclusive
         */
        record Range(long from, long to) implements RangeData {}

        /**
         * @param from An integer indicating the start position of the request range, inclusive
         */
        record StartFrom(long from) implements RangeData {}

        /**
         * @param bytes An integer indicating the number of bytes at the end of the resource, can be larger than resource size
         */
        record LastN(long bytes) implements RangeData {}
    }

    S3Object get(String bucket, String key, @Nullable RangeData range) throws S3ClientException;

    @Nullable
    S3Object getOptional(String bucket, String key, @Nullable RangeData range) throws S3ClientException;

    S3ObjectMeta getMeta(String bucket, String key) throws S3ClientException;

    @Nullable
    S3ObjectMeta getMetaOptional(String bucket, String key) throws S3ClientException;

    List<S3ObjectMeta> list(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) throws S3ClientException;

    /**
     * paginated listing by maxPageSize
     */
    Iterator<S3ObjectMeta> listIterator(String bucket, @Nullable String prefix, @Nullable String delimiter, int maxPageSize) throws S3ClientException;

    S3ObjectUploadResult put(String bucket, String key, S3Body body) throws S3ClientException;

    // todo version id ?
    void delete(String bucket, String key) throws S3ClientException;

    void delete(String bucket, Collection<String> keys) throws S3ClientException;

}
