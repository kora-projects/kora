package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3SimpleClientTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
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

    // Get
    @Test
    public void clientGetMeta() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                S3ObjectMeta get(String key);
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
                S3Object get(String key);
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
                List<S3ObjectMeta> get(Collection<String> keys);
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
                List<S3Object> get(List<String> keys);
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
                S3ObjectMeta get(String key1, long key2);
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
                S3ObjectMeta get(String key1);
            }
            """));
    }

    @Test
    public void clientGetKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("const-key")
                S3ObjectMeta get();
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetKeyPrefix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("pre-{key1}")
                S3ObjectMeta get(String key1);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetKeySuffix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("{key1}-suffix")
                S3ObjectMeta get(String key1);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetKeyPrefixSuffix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get("pre-{key1}-suffix")
                S3ObjectMeta get(String key1);
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
                S3ObjectMeta get(String key);
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
                S3ObjectMetaList list();
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
                S3ObjectList list();
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListMetaWithPrefix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                S3ObjectMetaList list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListObjectsWithPrefix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                S3ObjectList list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListLimitWithPrefix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List(limit = 100)
                S3ObjectList list(String prefix);
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
                S3ObjectList list(String key1, long key2);
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
                S3ObjectList list(String key1);
            }
            """));
    }

    @Test
    public void clientListKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List("const-key")
                S3ObjectList list();
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
                S3ObjectList list(String key);
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
                void delete(String key);
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
                void delete(String key1, long key2);
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
                void delete(String key1);
            }
            """));
    }

    @Test
    public void clientDeleteKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete("const-key")
                void delete();
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
                void delete(String key);
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
                void delete(List<String> key);
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
                void put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBodyReturnUpload() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                S3ObjectUpload put(String key, S3Body body);
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
                void put(String key, byte[] body);
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
                void put(String key, ByteBuffer body);
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
                void put(String key, S3Body body);
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
                void put(String key, S3Body body);
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
                void put(String key, S3Body body);
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
                void put(String key1, long key2, S3Body body);
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
                void put(String key1, S3Body body);
            }
            """));
    }

    @Test
    public void clientPutKeyConst() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put("const-key")
                void put(S3Body body);
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
                void put(String key, S3Body body);
            }
            """));
    }
}
