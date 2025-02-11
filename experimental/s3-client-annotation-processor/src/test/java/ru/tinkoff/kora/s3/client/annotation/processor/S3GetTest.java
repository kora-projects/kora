package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class S3GetTest extends AbstractS3Test {

    @Test
    public void testGetMetadata() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                S3ObjectMeta getRequired(@S3.Bucket String bucket, String key);
            
                @S3.Get
                Optional<S3ObjectMeta> getOptional(@S3.Bucket String bucket, String key);
            
                @S3.Get
                @Nullable
                S3ObjectMeta getNullable(@S3.Bucket String bucket, String key);
            }
            """);

        var meta = new S3ObjectMeta("bucket", "key", Instant.now(), -1);

        when(s3Client.getMeta("bucket", "key")).thenReturn(meta);
        assertThat(client.<S3ObjectMeta>invoke("getRequired", "bucket", "key")).isSameAs(meta);
        verify(s3Client).getMeta("bucket", "key");
        reset(s3Client);

        when(s3Client.getMetaOptional("bucket", "key")).thenReturn(meta);
        assertThat((Optional<S3ObjectMeta>) client.invoke("getOptional", "bucket", "key")).containsSame(meta);
        verify(s3Client).getMetaOptional("bucket", "key");
        reset(s3Client);

        when(s3Client.getMetaOptional("bucket", "key")).thenReturn(null);
        assertThat((Optional<?>) client.invoke("getOptional", "bucket", "key")).isEmpty();
        verify(s3Client).getMetaOptional("bucket", "key");
        reset(s3Client);

        when(s3Client.getMetaOptional("bucket", "key")).thenReturn(meta);
        assertThat(client.<S3ObjectMeta>invoke("getNullable", "bucket", "key")).isSameAs(meta);
        verify(s3Client).getMetaOptional("bucket", "key");
        reset(s3Client);

        when(s3Client.getMetaOptional("bucket", "key")).thenReturn(null);
        assertThat(client.<S3ObjectMeta>invoke("getNullable", "bucket", "key")).isNull();
        verify(s3Client).getMetaOptional("bucket", "key");
        reset(s3Client);
    }

    @Test
    public void testGetBytes() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                byte[] get(@S3.Bucket String bucket, String key);
            
                @S3.Get
                byte[] getRange(@S3.Bucket String bucket, String key, RangeData range);
            
                @S3.Get
                @Nullable
                byte[] getNullable(@S3.Bucket String bucket, String key);
            
                @S3.Get
                Optional<byte[]> getOptional(@S3.Bucket String bucket, String key);
            }
            """);

        var meta = new S3ObjectMeta("bucket", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("bucket", "key", null)).thenReturn(supplier.get());
        assertThat((byte[]) client.invoke("get", "bucket", "key")).isEqualTo(bytes);
        verify(s3Client).get("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier.get());
        assertThat(client.<Optional<byte[]>>invoke("getOptional", "bucket", "key")).contains(bytes);
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(null);
        assertThat(client.<Optional<?>>invoke("getOptional", "bucket", "key")).isEmpty();
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier.get());
        assertThat(client.<byte[]>invoke("getNullable", "bucket", "key")).isEqualTo(bytes);
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(null);
        assertThat(client.<byte[]>invoke("getNullable", "bucket", "key")).isNull();
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);
    }

    @Test
    public void testGetObject() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                S3Object get(@Bucket String bucket, String key);
            
                @S3.Get
                S3Object getRange(@S3.Bucket String bucket, String key, RangeData range);
            
                @S3.Get
                @Nullable
                S3Object getNullable(@Bucket String bucket, String key);
            }
            """);

        var meta = new S3ObjectMeta("bucket", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("bucket", "key", null)).thenReturn(supplier.get());
        try (var object = (S3Object) client.invoke("get", "bucket", "key")) {
            assertThat(object).isNotNull();
            assertThat(object.meta()).isEqualTo(meta);
            try (var body = object.body()) {
                assertThat(body.asBytes()).isEqualTo(bytes);
            }
        }
        verify(s3Client).get("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier.get());
        try (var object = (S3Object) client.invoke("getNullable", "bucket", "key")) {
            assertThat(object).isNotNull();
            assertThat(object.meta()).isEqualTo(meta);
            try (var body = object.body()) {
                assertThat(body.asBytes()).isEqualTo(bytes);
            }
        }
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(null);
        try (var object = (S3Object) client.invoke("getNullable", "bucket", "key")) {
            assertThat(object).isNull();
        }
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);
    }

    @Test
    public void testGetS3Body() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                S3Body get(@Bucket String bucket, String key);
            
                @S3.Get
                S3Body getRange(@S3.Bucket String bucket, String key, RangeData range);
            
                @S3.Get
                @Nullable
                S3Body getNullable(@Bucket String bucket, String key);
            }
            """);

        var meta = new S3ObjectMeta("bucket", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("bucket", "key", null)).thenReturn(supplier.get());
        try (var body = (S3Body) client.invoke("get", "bucket", "key")) {
            assertThat(body.asBytes()).isEqualTo(bytes);
        }
        verify(s3Client).get("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier.get());
        try (var body = (S3Body) client.invoke("getNullable", "bucket", "key")) {
            assertThat(body.asBytes()).isEqualTo(bytes);
        }
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(null);
        try (var body = (S3Body) client.invoke("getNullable", "bucket", "key")) {
            assertThat(body).isNull();
        }
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);
    }

    @Test
    public void testGetInputStream() throws Exception {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get
                InputStream get(@Bucket String bucket, String key);
            
                @S3.Get
                InputStream getRange(@S3.Bucket String bucket, String key, RangeData range);
            
                @S3.Get
                @Nullable
                InputStream getNullable(@Bucket String bucket, String key);
            }
            """);

        var meta = new S3ObjectMeta("bucket", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("bucket", "key", null)).thenReturn(supplier.get());
        try (var body = (InputStream) client.invoke("get", "bucket", "key")) {
            assertThat(body.readAllBytes()).isEqualTo(bytes);
        }
        verify(s3Client).get("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier.get());
        try (var body = (InputStream) client.invoke("getNullable", "bucket", "key")) {
            assertThat(body.readAllBytes()).isEqualTo(bytes);
        }
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);

        when(s3Client.getOptional("bucket", "key", null)).thenReturn(null);
        try (var body = (InputStream) client.invoke("getNullable", "bucket", "key")) {
            assertThat(body).isNull();
        }
        verify(s3Client).getOptional("bucket", "key", null);
        reset(s3Client);
    }

    @Test
    public void testGetWithKeyTemplate() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get("prefix/{key}/suffix")
                byte[] get(@S3.Bucket String bucket, String key);
            }
            """);

        var meta = new S3ObjectMeta("bucket", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("bucket", "prefix/key/suffix", null)).thenReturn(supplier.get());
        assertThat((byte[]) client.invoke("get", "bucket", "key")).isEqualTo(bytes);
        verify(s3Client).get("bucket", "prefix/key/suffix", null);
        reset(s3Client);
    }

    @Test
    public void testGetWithBucketOnClient() {
        var config = MapConfigFactory.fromMap(Map.of(
            "s3", Map.of(
                "client", Map.of(
                    "bucket", "on-class"
                )
            )
        ));
        var client = this.compile("""
            @S3.Client
            @Bucket("s3.client.bucket")
            public interface Client {
                @S3.Get("prefix/{key}/suffix")
                byte[] get(String key);
            }
            """, newGeneratedObject("$Client_ClientConfig", config));

        var meta = new S3ObjectMeta("on-class", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("on-class", "prefix/key/suffix", null)).thenReturn(supplier.get());
        assertThat(client.<byte[]>invoke("get", "key")).isEqualTo(bytes);
        verify(s3Client).get("on-class", "prefix/key/suffix", null);
        reset(s3Client);
    }

    @Test
    public void testGetWithBucketOnMethod() {
        var config = MapConfigFactory.fromMap(Map.of(
            "s3", Map.of(
                "client", Map.of(
                    "get", Map.of(
                        "bucket", "on-method"
                    )
                )
            )
        ));
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.Get("prefix/{key}/suffix")
                @Bucket("s3.client.get.bucket")
                byte[] get(String key);
            }
            """, newGeneratedObject("$Client_ClientConfig", config));

        var meta = new S3ObjectMeta("on-method", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("on-method", "prefix/key/suffix", null)).thenReturn(supplier.get());
        assertThat(client.<byte[]>invoke("get", "key")).isEqualTo(bytes);
        verify(s3Client).get("on-method", "prefix/key/suffix", null);
        reset(s3Client);
    }

    @Test
    public void testGetWithBucketOnMethodAndType() {
        var config = MapConfigFactory.fromMap(Map.of(
            "s3", Map.of(
                "client", Map.of(
                    "bucket", "on-class",
                    "get", Map.of(
                        "bucket", "on-method"
                    )
                )
            )
        ));
        var client = this.compile("""
            @S3.Client
            @Bucket("s3.client.bucket")
            public interface Client {
                @S3.Get("prefix/{key}/suffix")
                @Bucket("s3.client.get.bucket")
                byte[] get(String key);
            }
            """, newGeneratedObject("$Client_ClientConfig", config));

        var meta = new S3ObjectMeta("on-method", "key", Instant.now(), -1);
        var bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        var supplier = (Supplier<S3Object>) () -> new S3Object(meta, S3Body.ofInputStream(new ByteArrayInputStream(bytes), bytes.length));

        when(s3Client.get("on-method", "prefix/key/suffix", null)).thenReturn(supplier.get());
        assertThat((byte[]) client.invoke("get", "key")).isEqualTo(bytes);
        verify(s3Client).get("on-method", "prefix/key/suffix", null);
        reset(s3Client);
    }
}
