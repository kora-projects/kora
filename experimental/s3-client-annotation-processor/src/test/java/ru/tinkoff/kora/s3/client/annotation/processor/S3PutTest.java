package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.model.S3ObjectUploadResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class S3PutTest extends AbstractS3Test {

    @Test
    public void testPutByteArray() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                S3ObjectUploadResult put(@S3.Bucket String bucket, String key, byte[] data);
            }
            """);

        var meta = new S3ObjectUploadResult("bucket", "key", "etag", "version");
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

        when(s3Client.put(eq("bucket"), eq("key"), eq(S3Body.ofBytes(bytes)))).thenReturn(meta);
        assertThat(client.<S3ObjectUploadResult>invoke("put", "bucket", "key", bytes)).isSameAs(meta);
        verify(s3Client).put(eq("bucket"), eq("key"), eq(S3Body.ofBytes(bytes)));
        reset(s3Client);
    }

    @Test
    public void testPutByteWithKeyTemplate() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put("prefix/{key}/suffix")
                S3ObjectUploadResult put(@S3.Bucket String bucket, String key, byte[] data);
            }
            """);

        var meta = new S3ObjectUploadResult("bucket", "key", "etag", "version");
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

        when(s3Client.put(eq("bucket"), eq("prefix/key/suffix"), eq(S3Body.ofBytes(bytes)))).thenReturn(meta);
        assertThat(client.<S3ObjectUploadResult>invoke("put", "bucket", "key", bytes)).isSameAs(meta);
        verify(s3Client).put(eq("bucket"), eq("prefix/key/suffix"), eq(S3Body.ofBytes(bytes)));
        reset(s3Client);
    }


}
