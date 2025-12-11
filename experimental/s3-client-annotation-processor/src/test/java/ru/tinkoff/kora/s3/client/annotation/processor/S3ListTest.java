package ru.tinkoff.kora.s3.client.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.s3.client.AwsCredentials;
import ru.tinkoff.kora.s3.client.model.request.ListObjectsArgs;
import ru.tinkoff.kora.s3.client.model.response.ListBucketResult;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class S3ListTest extends AbstractS3ClientTest {
    @Test
    public void testList() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                List<String> getWithPrefix(AwsCredentials creds, @Bucket String bucket, String prefix);
            }
            """);

        var creds = AwsCredentials.of("test", "test");
        var list = List.of(new ListBucketResult.ListBucketItem("test", "test1", "etag", null, null, null, 1L, null, null));

        var listBucketResult = new ListBucketResult(null, 1, null, list);

        when(s3Client.listObjectsV2(same(creds), eq("bucket"), assertArg(o -> Assertions.assertThat(o).hasFieldOrPropertyWithValue("prefix", "test-prefix"))))
            .thenReturn(listBucketResult);

        var result = client.<List<String>>invoke("getWithPrefix", creds, "bucket", "test-prefix");
        assertThat(result).containsExactly("test1");

        verify(s3Client).listObjectsV2(same(creds), eq("bucket"), assertArg(o -> Assertions.assertThat(o).hasFieldOrPropertyWithValue("prefix", "test-prefix")));
        reset(s3Client);
    }

    @Test
    public void testListReturnsListBucketResultWithConstPrefix() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List("const-")
                ListBucketResult listConst(AwsCredentials creds, @Bucket String bucket);
            }
            """);

        var creds = AwsCredentials.of("a", "b");
        var items = List.of(new ListBucketResult.ListBucketItem("b", "k1", "etag", null, null, null, 10L, null, null));
        var res = new ListBucketResult(null, 1, null, items);

        when(s3Client.listObjectsV2(same(creds), eq("bucket"), assertArg(o -> Assertions.assertThat(o).hasFieldOrPropertyWithValue("prefix", "const-"))))
            .thenReturn(res);

        var result = client.<ListBucketResult>invoke("listConst", creds, "bucket");
        assertThat(result).isSameAs(res);

        verify(s3Client).listObjectsV2(same(creds), eq("bucket"), assertArg(o -> Assertions.assertThat(o).hasFieldOrPropertyWithValue("prefix", "const-")));
        reset(s3Client);
    }

    @Test
    public void testIteratorStringLazyPages() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                Iterator<String> iteratorStrings(AwsCredentials creds, @Bucket String bucket, ListObjectsArgs args);
            }
            """);

        var creds = AwsCredentials.of("x", "y");

        var item1 = new ListBucketResult.ListBucketItem("b", "k1", "etag", null, null, null, 1L, null, null);
        var item2 = new ListBucketResult.ListBucketItem("b", "k2", "etag", null, null, null, 2L, null, null);

        when(s3Client.listObjectsV2Iterator(same(creds), eq("bucket"), any()))
            .thenReturn(List.of(item1, item2).iterator());

        var args = new ListObjectsArgs();
        var it = client.<Iterator<String>>invoke("iteratorStrings", creds, "bucket", args);

        assertThat(it.next()).isEqualTo("k1");
        assertThat(it.next()).isEqualTo("k2");

        verify(s3Client).listObjectsV2Iterator(same(creds), eq("bucket"), any());
        reset(s3Client);
    }

    @Test
    public void testIteratorItemsLazyPages() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                Iterator<ListBucketResult.ListBucketItem> iteratorItems(AwsCredentials creds, @Bucket String bucket, ListObjectsArgs args);
            }
            """);

        var creds = AwsCredentials.of("x", "y");

        var item1 = new ListBucketResult.ListBucketItem("b", "k1", "etag", null, null, null, 1L, null, null);
        var item2 = new ListBucketResult.ListBucketItem("b", "k2", "etag", null, null, null, 2L, null, null);

        when(s3Client.listObjectsV2Iterator(same(creds), eq("bucket"), any()))
            .thenReturn(List.of(item1, item2).iterator());

        var args = new ListObjectsArgs();
        var it = client.<Iterator<ListBucketResult.ListBucketItem>>invoke("iteratorItems", creds, "bucket", args);

        assertThat(it.next()).isSameAs(item1);
        assertThat(it.next()).isSameAs(item2);

        verify(s3Client).listObjectsV2Iterator(same(creds), eq("bucket"), any());
        reset(s3Client);
    }

    @Test
    public void testListItemsMappingWithTemplatePrefix() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List("pre-{prefix}")
                List<ListBucketResult.ListBucketItem> items(AwsCredentials creds, @Bucket String bucket, String prefix);
            }
            """);

        var creds = AwsCredentials.of("u", "v");
        var items = List.of(new ListBucketResult.ListBucketItem("b", "item-key", "etag", null, null, null, 5L, null, null));
        var res = new ListBucketResult(null, 1, null, items);

        when(s3Client.listObjectsV2(same(creds), eq("bucket"), assertArg(o -> Assertions.assertThat(o).hasFieldOrPropertyWithValue("prefix", "pre-x"))))
            .thenReturn(res);

        var result = client.<java.util.List<ListBucketResult.ListBucketItem>>invoke("items", creds, "bucket", "x");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("item-key");

        verify(s3Client).listObjectsV2(same(creds), eq("bucket"), assertArg(o -> Assertions.assertThat(o).hasFieldOrPropertyWithValue("prefix", "pre-x")));
        reset(s3Client);
    }

}
