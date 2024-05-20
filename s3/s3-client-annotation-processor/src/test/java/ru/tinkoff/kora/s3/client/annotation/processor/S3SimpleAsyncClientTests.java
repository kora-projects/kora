package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3SimpleAsyncClientTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            import java.nio.ByteBuffer;
            import java.io.InputStream;
            import java.util.List;
            import java.util.Collection;
            import ru.tinkoff.kora.s3.client.annotation.*;
            import ru.tinkoff.kora.s3.client.annotation.S3.*;
            import ru.tinkoff.kora.s3.client.model.*;
            import ru.tinkoff.kora.s3.client.*;
            import ru.tinkoff.kora.s3.client.model.S3Object;
            import software.amazon.awssdk.services.s3.model.*;
            """;
    }

    @Test
    public void clientConfig() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletionStage<S3ObjectMeta> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();

        var config = this.compileResult.loadClass("$Client_ClientConfigModule");
        assertThat(config).isNotNull();
    }

    // Get
    @Test
    public void clientGetMeta() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletionStage<S3ObjectMeta> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetMetaFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletableFuture<S3ObjectMeta> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetObject() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletionStage<S3Object> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetManyMetas() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletionStage<List<S3ObjectMeta>> get(Collection<String> keys);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetManyMetasFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletableFuture<List<S3ObjectMeta>> get(Collection<String> keys);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetManyObjects() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletionStage<List<S3Object>> get(List<String> keys);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetManyObjectsFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletableFuture<List<S3Object>> get(List<String> keys);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetKeyConcat() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("{key1}-{key2}")
                CompletionStage<S3ObjectMeta> get(String key1, long key2);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetKeyMissing() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("{key1}-{key12345}")
                CompletionStage<S3ObjectMeta> get(String key1);
            }
            """));
    }

    @Test
    public void clientGetKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("const-key")
                CompletionStage<S3ObjectMeta> get();
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetKeyUnused() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("const-key")
                CompletionStage<S3ObjectMeta> get(String key);
            }
            """));
    }

    // List
    @Test
    public void clientListMeta() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                CompletionStage<S3ObjectMetaList> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListMetaFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                CompletableFuture<S3ObjectMetaList> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListObjects() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                CompletionStage<S3ObjectList> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListLimit() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List(limit = 100)
                CompletionStage<S3ObjectList> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListKeyConcat() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List("{key1}-{key2}")
                CompletionStage<S3ObjectList> list(String key1, long key2);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListKeyMissing() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List("{key1}-{key12345}")
                CompletionStage<S3ObjectList> list(String key1);
            }
            """));
    }

    @Test
    public void clientListKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List("const-key")
                CompletionStage<S3ObjectList> list();
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListKeyUnused() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List("const-key")
                CompletionStage<S3ObjectList> list(String key);
            }
            """));
    }

    // Delete
    @Test
    public void clientDelete() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletionStage<Void> delete(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeleteFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletableFuture<Void> delete(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeleteKeyConcat() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete("{key1}-{key2}")
                CompletionStage<Void> delete(String key1, long key2);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeleteKeyMissing() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete("{key1}-{key12345}")
                CompletionStage<Void> delete(String key1);
            }
            """));
    }

    @Test
    public void clientDeleteKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete("const-key")
                CompletionStage<Void> delete();
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeleteKeyUnused() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete("const-key")
                CompletionStage<Void> delete(String key);
            }
            """));
    }

    // Deletes
    @Test
    public void clientDeletes() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletionStage<Void> delete(List<String> key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeletesReturnKeys() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletionStage<List<String>> delete(List<String> key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    // Put
    @Test
    public void clientPutBody() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletionStage<Void> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBodyFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletableFuture<Void> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBodyReturnVersionId() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletionStage<String> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBytes() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletionStage<Void> put(String key, byte[] body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBuffer() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletionStage<Void> put(String key, ByteBuffer body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutInputStream() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletionStage<Void> put(String key, InputStream body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBodyAndType() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put(type = "type")
                CompletionStage<Void> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBodyAndEncoding() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put(encoding = "encoding")
                CompletionStage<Void> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBodyAndTypeAndEncoding() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put(type = "type", encoding = "encoding")
                CompletionStage<Void> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutKeyConcat() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put("{key1}-{key2}")
                CompletionStage<Void> put(String key1, long key2, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutKeyMissing() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put("{key1}-{key12345}")
                CompletionStage<Void> put(String key1, S3Body body);
            }
            """));
    }

    @Test
    public void clientPutKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put("const-key")
                CompletionStage<Void> put(S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutKeyUnused() {
        assertThatThrownBy(() -> this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put("const-key")
                CompletionStage<Void> put(String key, S3Body body);
            }
            """));
    }
}
