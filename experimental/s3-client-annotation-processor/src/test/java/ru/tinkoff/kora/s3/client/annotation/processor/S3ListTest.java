package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class S3ListTest extends AbstractS3Test {
    @Test
    public void testListBucket() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                List<S3ObjectMeta> list(@S3.Bucket String bucket);
            
                @S3.List
                Iterator<S3ObjectMeta> iterator(@S3.Bucket String bucket);
            }
            """);

        var list = List.<S3ObjectMeta>of();

        when(s3Client.list("bucket", null, null, 1000)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("list", "bucket")).isSameAs(list);
        verify(s3Client).list("bucket", null, null, 1000);
        reset(s3Client);

        var iterator = new ArrayList<S3ObjectMeta>().iterator();
        when(s3Client.listIterator("bucket", null, null, 1000)).thenReturn(iterator);
        assertThat(client.<Iterator<S3ObjectMeta>>invoke("iterator", "bucket")).isSameAs(iterator);
        verify(s3Client).listIterator("bucket", null, null, 1000);
        reset(s3Client);
    }

    @Test
    public void testListBucketPrefix() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                List<S3ObjectMeta> prefix(@S3.Bucket String bucket, String prefix);
            
                @S3.List("/prefix/{prefix}")
                List<S3ObjectMeta> template(@S3.Bucket String bucket, String prefix);
            }
            """);

        var list = List.<S3ObjectMeta>of();

        when(s3Client.list("bucket", "test1", null, 1000)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("prefix", "bucket", "test1")).isSameAs(list);
        verify(s3Client).list("bucket", "test1", null, 1000);
        reset(s3Client);

        when(s3Client.list("bucket", "/prefix/test1", null, 1000)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("template", "bucket", "test1")).isSameAs(list);
        verify(s3Client).list("bucket", "/prefix/test1", null, 1000);
        reset(s3Client);

    }

    @Test
    public void testListLimit() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                List<S3ObjectMeta> listOnParameter(@S3.Bucket String bucket, @S3.List.Limit int limit);
            
                @S3.List
                Iterator<S3ObjectMeta> iteratorOnParameter(@S3.Bucket String bucket, @S3.List.Limit int limit);
            
                @S3.List
                @S3.List.Limit(44)
                List<S3ObjectMeta> listOnMethod(@S3.Bucket String bucket);
            
                @S3.List
                @S3.List.Limit(45)
                Iterator<S3ObjectMeta> iteratorOnMethod(@S3.Bucket String bucket);
            }
            """);

        var list = List.<S3ObjectMeta>of();
        var iterator = new ArrayList<S3ObjectMeta>().iterator();

        when(s3Client.list("bucket", null, null, 42)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("listOnParameter", "bucket", 42)).isSameAs(list);
        verify(s3Client).list("bucket", null, null, 42);
        reset(s3Client);

        when(s3Client.listIterator("bucket", null, null, 43)).thenReturn(iterator);
        assertThat(client.<Iterator<S3ObjectMeta>>invoke("iteratorOnParameter", "bucket", 43)).isSameAs(iterator);
        verify(s3Client).listIterator("bucket", null, null, 43);
        reset(s3Client);

        when(s3Client.list("bucket", null, null, 44)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("listOnMethod", "bucket")).isSameAs(list);
        verify(s3Client).list("bucket", null, null, 44);
        reset(s3Client);

        when(s3Client.listIterator("bucket", null, null, 45)).thenReturn(iterator);
        assertThat(client.<Iterator<S3ObjectMeta>>invoke("iteratorOnMethod", "bucket")).isSameAs(iterator);
        verify(s3Client).listIterator("bucket", null, null, 45);
        reset(s3Client);
    }

    @Test
    public void testListDelimiter() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                List<S3ObjectMeta> listOnParameter(@S3.Bucket String bucket, @S3.List.Delimiter String delimiter);
            
                @S3.List
                Iterator<S3ObjectMeta> iteratorOnParameter(@S3.Bucket String bucket, @S3.List.Delimiter String delimiter);
            
                @S3.List
                @S3.List.Delimiter("/")
                List<S3ObjectMeta> listOnMethod(@S3.Bucket String bucket);
            
                @S3.List
                @S3.List.Delimiter("/")
                Iterator<S3ObjectMeta> iteratorOnMethod(@S3.Bucket String bucket);
            }
            """);

        var list = List.<S3ObjectMeta>of();
        var iterator = new ArrayList<S3ObjectMeta>().iterator();

        when(s3Client.list("bucket", null, "/", 1000)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("listOnParameter", "bucket", "/")).isSameAs(list);
        verify(s3Client).list("bucket", null, "/", 1000);
        reset(s3Client);

        when(s3Client.listIterator("bucket", null, "/", 1000)).thenReturn(iterator);
        assertThat(client.<Iterator<S3ObjectMeta>>invoke("iteratorOnParameter", "bucket", "/")).isSameAs(iterator);
        verify(s3Client).listIterator("bucket", null, "/", 1000);
        reset(s3Client);

        when(s3Client.list("bucket", null, "/", 1000)).thenReturn(list);
        assertThat(client.<List<S3ObjectMeta>>invoke("listOnMethod", "bucket")).isSameAs(list);
        verify(s3Client).list("bucket", null, "/", 1000);
        reset(s3Client);

        when(s3Client.listIterator("bucket", null, "/", 1000)).thenReturn(iterator);
        assertThat(client.<Iterator<S3ObjectMeta>>invoke("iteratorOnMethod", "bucket")).isSameAs(iterator);
        verify(s3Client).listIterator("bucket", null, "/", 1000);
        reset(s3Client);
    }
}
