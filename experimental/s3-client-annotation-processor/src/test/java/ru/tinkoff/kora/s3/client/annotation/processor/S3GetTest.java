package ru.tinkoff.kora.s3.client.annotation.processor;


import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.s3.client.AwsCredentials;
import ru.tinkoff.kora.s3.client.model.request.GetObjectArgs;
import ru.tinkoff.kora.s3.client.model.response.GetObjectResult;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class S3GetTest extends AbstractS3ClientTest {
    @Test
    public void testGetByteArray() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                byte[] get(@S3.Bucket String bucket, String key);
            }
            """);

        var getObjectResult = mock(GetObjectResult.class);
        var getObjectBody = mock(HttpBodyInput.class);
        when(getObjectResult.body()).thenReturn(getObjectBody);
        when(getObjectBody.asInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(s3Client.getObject(any(), eq("bucket"), eq("key"), any(), eq(true))).thenReturn(getObjectResult);

        var result = client.<byte[]>invoke("get", "bucket", "key");
        assertThat(result).isEqualTo(new byte[0]);

        verify(s3Client).getObject(any(), eq("bucket"), eq("key"), isNull(), eq(true));
        verify(getObjectResult).body();
        verify(getObjectResult).close();
        verify(getObjectBody).asInputStream();
        verify(getObjectBody).close();
        reset(s3Client);
    }

    @Test
    public void testGetWithTemplateKeyAndArgs() throws Exception {
        var bucketConfig = MapConfigFactory.fromMap(Map.of(
            "bucket", "bucket_value"
        ));
        var client = this.compile("""
            @S3.Client
            @S3.Bucket("bucket")
            public interface Client {
                @S3.Get("prefix-{key}")
                GetObjectResult getByTemplate(String key, GetObjectArgs args);
            }
            """, newGeneratedObject("$Client_BucketsConfig", bucketConfig));

        var getObjectResult = mock(GetObjectResult.class);
        var args = new GetObjectArgs();
        when(s3Client.getObject(any(), eq("bucket_value"), eq("prefix-key1"), same(args), eq(true))).thenReturn(getObjectResult);

        var result = client.<GetObjectResult>invoke("getByTemplate", "key1", args);
        assertThat(result).isSameAs(getObjectResult);

        verify(s3Client).getObject(any(), eq("bucket_value"), eq("prefix-key1"), same(args), eq(true));
        verify(getObjectResult, never()).body();
        verify(getObjectResult, never()).close();
        reset(s3Client);
    }

    @Test
    public void testGetWithAwsCredentials() throws Exception {
        var bucketConfig = MapConfigFactory.fromMap(Map.of(
            "Client", Map.of(
                "bucket", "bucket_value"
            )
        ));
        var client = this.compile("""
            @S3.Client
            @S3.Bucket(".bucket")
            public interface Client {
                @S3.Get
                @Nullable
                GetObjectResult getWithCreds(AwsCredentials creds, String key);
            }
            """, newGeneratedObject("$Client_BucketsConfig", bucketConfig));

        var getObjectResult = mock(GetObjectResult.class);
        var creds = AwsCredentials.of("test", "test");
        when(s3Client.getObject(same(creds), eq("bucket_value"), eq("key"), isNull(), eq(false))).thenReturn(getObjectResult);

        var result = client.<GetObjectResult>invoke("getWithCreds", creds, "key");
        assertThat(result).isSameAs(getObjectResult);

        verify(s3Client).getObject(same(creds), eq("bucket_value"), eq("key"), isNull(), eq(false));
        reset(s3Client);
    }

    @Test
    public void testGetWithGetObjectArgsParam() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                GetObjectResult getWithArgs(@S3.Bucket String bucket, String key, GetObjectArgs args);
            }
            """);

        var getObjectResult = mock(GetObjectResult.class);
        var args = new GetObjectArgs();
        when(s3Client.getObject(any(), eq("bucket"), eq("key"), same(args), eq(true))).thenReturn(getObjectResult);

        assertThat(client.<GetObjectResult>invoke("getWithArgs", "bucket", "key", args)).isSameAs(getObjectResult);

        verify(s3Client).getObject(any(), eq("bucket"), eq("key"), same(args), eq(true));
        reset(s3Client);
    }

    @Test
    public void testGetConstantKey() throws Exception {
        var bucketConfig = MapConfigFactory.fromMap(Map.of(
            "bucket", "bucket_value"
        ));
        var client = this.compile("""
            @S3.Client
            @S3.Bucket("bucket")
            public interface Client {
                @S3.Get("constant-key")
                GetObjectResult getConstant();
            }
            """, newGeneratedObject("$Client_BucketsConfig", bucketConfig));

        var getObjectResult = mock(GetObjectResult.class);
        when(s3Client.getObject(any(), eq("bucket_value"), eq("constant-key"), isNull(), eq(true))).thenReturn(getObjectResult);

        assertThat(client.<GetObjectResult>invoke("getConstant")).isSameAs(getObjectResult);

        verify(s3Client).getObject(any(), eq("bucket_value"), eq("constant-key"), isNull(), eq(true));
        reset(s3Client);
    }
}

