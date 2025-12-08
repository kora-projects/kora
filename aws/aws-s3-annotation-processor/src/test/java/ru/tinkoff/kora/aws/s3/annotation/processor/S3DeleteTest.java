package ru.tinkoff.kora.aws.s3.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.aws.s3.AwsCredentials;
import ru.tinkoff.kora.aws.s3.model.request.DeleteObjectArgs;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class S3DeleteTest extends AbstractS3ClientTest {

    @Test
    public void testDeleteWithBucketAndKey() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Delete
                void delete(@S3.Bucket String bucket, String key);
            }
            """);

        client.invoke("delete", "bucket", "key");

        verify(s3Client).deleteObject(any(), eq("bucket"), eq("key"), isNull());
        reset(s3Client);
    }

    @Test
    public void testDeleteWithTemplateKeyAndArgs() throws Exception {
        var bucketConfig = MapConfigFactory.fromMap(Map.of(
            "bucket", "bucket_value"
        ));
        var client = this.compile("""
            @S3.Client
            @S3.Bucket("bucket")
            public interface Client {
                @S3.Delete("prefix-{key}")
                void deleteByTemplate(String key, DeleteObjectArgs args);
            }
            """, newGeneratedObject("$Client_BucketsConfig", bucketConfig));

        var args = new DeleteObjectArgs();
        client.invoke("deleteByTemplate", "key1", args);

        verify(s3Client).deleteObject(any(), eq("bucket_value"), eq("prefix-key1"), same(args));
        reset(s3Client);
    }

    @Test
    public void testDeleteWithAwsCredentials() throws Exception {
        var bucketConfig = MapConfigFactory.fromMap(Map.of(
            "Client", Map.of(
                "bucket", "bucket_value"
            )
        ));
        var client = this.compile("""
            @S3.Client
            @S3.Bucket(".bucket")
            public interface Client {
                @S3.Delete
                void deleteWithCreds(AwsCredentials creds, String key);
            }
            """, newGeneratedObject("$Client_BucketsConfig", bucketConfig));

        var creds = AwsCredentials.of("test", "test");
        client.invoke("deleteWithCreds", creds, "key");

        verify(s3Client).deleteObject(same(creds), eq("bucket_value"), eq("key"), isNull());
        reset(s3Client);
    }

    @Test
    public void testDeleteConstantKey() throws Exception {
        var bucketConfig = MapConfigFactory.fromMap(Map.of(
            "bucket", "bucket_value"
        ));
        var client = this.compile("""
            @S3.Client
            @S3.Bucket("bucket")
            public interface Client {
                @S3.Delete("constant-key")
                void deleteConstant();
            }
            """, newGeneratedObject("$Client_BucketsConfig", bucketConfig));

        client.invoke("deleteConstant");

        verify(s3Client).deleteObject(any(), eq("bucket_value"), eq("constant-key"), isNull());
        reset(s3Client);
    }
}
