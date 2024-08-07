package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class S3AwsAsyncClientTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.CompletionStage;
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
    public void clientGetAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletionStage<GetObjectResponse> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetAwsFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Get
                CompletableFuture<GetObjectResponse> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                CompletionStage<ListObjectsV2Response> list();
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListAwsWithPrefix() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                CompletionStage<ListObjectsV2Response> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListAwsLimit() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List(limit = 100)
                CompletionStage<ListObjectsV2Response> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListAwsFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.List
                CompletableFuture<ListObjectsV2Response> list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeleteAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletionStage<DeleteObjectResponse> delete(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeleteAwsFuture() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletableFuture<DeleteObjectResponse> delete(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientDeletesAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Delete
                CompletionStage<DeleteObjectsResponse> delete(List<String> key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientPutBody() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
                        
                @S3.Put
                CompletionStage<PutObjectResponse> put(String key, S3Body body);
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
                CompletableFuture<PutObjectResponse> put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }
}
