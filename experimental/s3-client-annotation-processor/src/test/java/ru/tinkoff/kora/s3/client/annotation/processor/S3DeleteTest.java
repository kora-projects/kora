package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class S3DeleteTest extends AbstractS3Test {
    @Test
    public void testDeleteKey() {
        var client = this.compile("""
            @S3.Client(clientFactoryTag = String.class)
            public interface Client {
                @S3.Delete
                void deleteKey(@S3.Bucket String bucket, String key);
            }
            """);

        var key1 = UUID.randomUUID().toString();
        client.invoke("deleteKey", "bucket", key1);
        verify(s3Client).delete("bucket", key1);
        reset(s3Client);
    }

    @Test
    public void testDeleteKeyWithTemplate() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Delete("some/prefix/{key1}/{key2}")
                void deleteKey(@S3.Bucket String bucket, String key1, String key2);
            }
            """);

        var key1 = UUID.randomUUID().toString();
        var key2 = UUID.randomUUID().toString();
        client.invoke("deleteKey", "bucket", key1, key2);
        verify(s3Client).delete("bucket", "some/prefix/" + key1 + "/" + key2);
        reset(s3Client);
    }

    @Test
    public void testDeleteKeys() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Delete
                void deleteKeys(@S3.Bucket String bucket, List<String> key);
            
                @S3.Delete
                void deleteKeysNonString(@S3.Bucket String bucket, List<Long> key);
            }
            """);

        var key1 = UUID.randomUUID().toString();
        var key2 = UUID.randomUUID().toString();

        client.invoke("deleteKeys", "bucket", List.of(key1, key2));
        verify(s3Client).delete("bucket", List.of(key1, key2));
        reset(s3Client);
    }

}
