package ru.tinkoff.kora.aws.s3.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.aws.s3.model.response.HeadObjectResult;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3HeadTest extends AbstractS3ClientTest {
    @Test
    void testHead() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Head
                HeadObjectResult head(@S3.Bucket String bucket, String key);
            }
            
            """
        );
        var response = new HeadObjectResult("test", "test", 1, HttpHeaders.empty());

        when(s3Client.headObject(any(), eq("bucket"), eq("key"), any(), eq(true))).thenReturn(response);

        var result = client.<HeadObjectResult>invoke("head", "bucket", "key");

        assertThat(result).isSameAs(response);

        verify(s3Client).headObject(any(), eq("bucket"), eq("key"), isNull(), eq(true));
    }
}
