package ru.tinkoff.kora.s3.client.annotation.processor;


import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.s3.client.$S3ClientConfig_UploadConfig_ConfigValueExtractor;
import ru.tinkoff.kora.s3.client.S3Client;
import ru.tinkoff.kora.s3.client.model.response.UploadedPart;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class S3PutTest extends AbstractS3ClientTest {
    @Test
    public void testPutByteArray() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, byte[] data);
            }
            """);

        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

        when(s3Client.putObject(any(), eq("bucket"), eq("key"), any(), same(bytes), eq(0), eq(bytes.length))).thenReturn("etag");

        assertThat(client.<String>invoke("put", "bucket", "key", bytes)).isEqualTo("etag");

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), same(bytes), eq(0), eq(bytes.length));
        reset(s3Client);
    }

    @Test
    public void testPutByteBuffer() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, ByteBuffer data);
            }
            """);

        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

        when(s3Client.putObject(any(), eq("bucket"), eq("key"), any(), same(bytes), eq(0), eq(bytes.length - 1))).thenReturn("etag");

        assertThat(client.<String>invoke("put", "bucket", "key", ByteBuffer.wrap(bytes).slice(0, bytes.length - 1))).isEqualTo("etag");

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), same(bytes), eq(0), eq(bytes.length - 1));
        reset(s3Client);
    }

    @Test
    public void testPutWithContentWriter() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, S3Client.ContentWriter writer);
            }
            """);

        var writer = mock(S3Client.ContentWriter.class);
        when(writer.length()).thenReturn(5L);

        when(s3Client.putObject(any(), eq("bucket"), eq("key"), any(), same(writer))).thenReturn("etag-content");

        assertThat(client.<String>invoke("put", "bucket", "key", writer)).isEqualTo("etag-content");

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), same(writer));
        reset(s3Client);
    }

    @Test
    public void testPutInputStream() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, InputStream writer);
            }
            """);

        var bytes = new byte[8 * 1024 * 1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        var is = new ByteArrayInputStream(bytes);
        var part1 = new UploadedPart(null, null, null, null, null, "etag1", 1, 5 * 1024 * 1024);
        var part2 = new UploadedPart(null, null, null, null, null, "etag2", 2, 3 * 1024 * 1024);

        when(s3Client.createMultipartUpload(any(), eq("bucket"), eq("key"), any())).thenReturn("multipartid");
        when(s3Client.uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(5 * 1024 * 1024))).thenReturn(part1);
        when(s3Client.uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(3 * 1024 * 1024))).thenReturn(part2);
        when(s3Client.completeMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(List.of(part1, part2)), any())).thenReturn("etag-final");
        when(config.upload()).thenReturn(new $S3ClientConfig_UploadConfig_ConfigValueExtractor.UploadConfig_Defaults());

        assertThat(client.<String>invoke("put", "bucket", "key", is)).isEqualTo("etag-final");

        verify(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any());
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(5 * 1024 * 1024));
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(3 * 1024 * 1024));
        verify(s3Client).completeMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(List.of(part1, part2)), any());
        reset(s3Client);
    }
}
