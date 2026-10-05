package io.koraframework.s3.client.kora.annotation.processor;


import io.koraframework.common.util.Size;
import io.koraframework.s3.client.kora.$S3ClientConfig_UploadConfig_ConfigValueMapper;
import io.koraframework.s3.client.kora.S3ClientConfig;
import io.koraframework.s3.client.kora.exception.S3ClientErrorException;
import org.junit.jupiter.api.Test;
import io.koraframework.s3.client.kora.S3Client;
import io.koraframework.s3.client.kora.model.response.UploadedPart;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.AdditionalMatchers.aryEq;
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
            import io.koraframework.s3.client.kora.S3Client;@S3.Client
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
        when(config.upload()).thenReturn(new $S3ClientConfig_UploadConfig_ConfigValueMapper.UploadConfig_Defaults());

        assertThat(client.<String>invoke("put", "bucket", "key", is)).isEqualTo("etag-final");

        verify(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any());
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(5 * 1024 * 1024));
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(3 * 1024 * 1024));
        verify(s3Client).completeMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(List.of(part1, part2)), any());
        reset(s3Client);
    }

    @Test
    public void testPutByteBufferWithPosition() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, ByteBuffer data);
            }
            """);

        var bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        var buffer = ByteBuffer.wrap(bytes, 5, 3);

        when(s3Client.putObject(any(), any(), any(), any(), any(byte[].class), anyInt(), anyInt())).thenReturn("etag");

        assertThat(client.<String>invoke("put", "bucket", "key", buffer)).isEqualTo("etag");

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), same(bytes), eq(5), eq(3));
        assertThat(buffer.position()).isEqualTo(5);
        reset(s3Client);
    }

    @Test
    public void testPutDirectByteBufferKeepsPosition() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, ByteBuffer data);
            }
            """);

        var buffer = ByteBuffer.allocateDirect(10).put(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}).position(5).limit(8);

        when(s3Client.putObject(any(), any(), any(), any(), any(byte[].class), anyInt(), anyInt())).thenReturn("etag");

        assertThat(client.<String>invoke("put", "bucket", "key", buffer)).isEqualTo("etag");

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), aryEq(new byte[]{5, 6, 7}), eq(0), eq(3));
        assertThat(buffer.position()).isEqualTo(5);
        reset(s3Client);
    }

    @Test
    public void testPutInputStreamVoidSinglePart() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                void put(@S3.Bucket String bucket, String key, InputStream body);
            }
            """);

        var bytes = new byte[10];
        var upload = uploadConfig(16);
        when(config.upload()).thenReturn(upload);

        client.invoke("put", "bucket", "key", new ByteArrayInputStream(bytes));

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), any(byte[].class), eq(0), eq(10));
        verifyNoMoreInteractions(s3Client);
        reset(s3Client);
    }

    @Test
    public void testPutInputStreamVoidMultipart() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                void put(@S3.Bucket String bucket, String key, InputStream body);
            }
            """);

        var bytes = new byte[16 + 10];
        var part1 = new UploadedPart(null, null, null, null, null, "etag1", 1, 16);
        var part2 = new UploadedPart(null, null, null, null, null, "etag2", 2, 10);
        var upload = uploadConfig(16);
        when(config.upload()).thenReturn(upload);
        when(s3Client.createMultipartUpload(any(), eq("bucket"), eq("key"), any())).thenReturn("multipartid");
        when(s3Client.uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(16))).thenReturn(part1);
        when(s3Client.uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(10))).thenReturn(part2);

        client.invoke("put", "bucket", "key", new ByteArrayInputStream(bytes));

        verify(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any());
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(16));
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(10));
        verify(s3Client).completeMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(List.of(part1, part2)), any());
        verifyNoMoreInteractions(s3Client);
        reset(s3Client);
    }

    @Test
    public void testPutInputStreamAbortsMultipartUploadOnPartFailure() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, InputStream body);
            }
            """);

        var bytes = new byte[16 * 2 + 10];
        var error = new S3ClientErrorException(500, "InternalError", "boom", "rq");
        var upload = uploadConfig(16);
        when(config.upload()).thenReturn(upload);
        when(s3Client.createMultipartUpload(any(), eq("bucket"), eq("key"), any())).thenReturn("multipartid");
        when(s3Client.uploadPart(any(), any(), any(), any(), eq(1), any(), anyInt(), anyInt()))
            .thenReturn(new UploadedPart(null, null, null, null, null, "etag1", 1, 16));
        when(s3Client.uploadPart(any(), any(), any(), any(), eq(2), any(), anyInt(), anyInt())).thenThrow(error);

        assertThatThrownBy(() -> client.invoke("put", "bucket", "key", new ByteArrayInputStream(bytes))).isSameAs(error);

        verify(s3Client).abortMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), any());
        verify(s3Client, never()).completeMultipartUpload(any(), any(), any(), any(), any(), any());
        reset(s3Client);
    }

    @Test
    public void testPutInputStreamAbortsMultipartUploadOnCompleteFailure() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Put
                String put(@S3.Bucket String bucket, String key, InputStream body);
            }
            """);

        var bytes = new byte[16 + 10];
        var error = new S3ClientErrorException(500, "InternalError", "boom", "rq");
        var abortError = new S3ClientErrorException(500, "InternalError", "abort failed", "rq");
        var upload = uploadConfig(16);
        when(config.upload()).thenReturn(upload);
        when(s3Client.createMultipartUpload(any(), eq("bucket"), eq("key"), any())).thenReturn("multipartid");
        when(s3Client.uploadPart(any(), any(), any(), any(), anyInt(), any(), anyInt(), anyInt()))
            .thenReturn(new UploadedPart(null, null, null, null, null, "etag", 1, 16));
        when(s3Client.completeMultipartUpload(any(), any(), any(), any(), any(), any())).thenThrow(error);
        doThrow(abortError).when(s3Client).abortMultipartUpload(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> client.invoke("put", "bucket", "key", new ByteArrayInputStream(bytes)))
            .isSameAs(error)
            .hasSuppressedException(abortError);

        verify(s3Client).abortMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), any());
        reset(s3Client);
    }

    private static S3ClientConfig.UploadConfig uploadConfig(long partSize) {
        var upload = mock(S3ClientConfig.UploadConfig.class);
        when(upload.partSize()).thenReturn(Size.of(partSize, Size.Type.BYTES));
        return upload;
    }
}
