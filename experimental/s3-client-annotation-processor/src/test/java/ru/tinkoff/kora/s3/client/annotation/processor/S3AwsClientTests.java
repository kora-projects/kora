package ru.tinkoff.kora.s3.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class S3AwsClientTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import java.util.List;
            import java.util.Collection;
            import java.util.Optional;
            import ru.tinkoff.kora.s3.client.annotation.*;
            import ru.tinkoff.kora.s3.client.annotation.S3.*;
            import ru.tinkoff.kora.s3.client.model.*;
            import ru.tinkoff.kora.s3.client.*;
            import ru.tinkoff.kora.s3.client.model.S3Object;
            import software.amazon.awssdk.services.s3.model.*;
            import software.amazon.awssdk.core.ResponseInputStream;
            """;
    }

    @Test
    public void clientGetAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.Get
                GetObjectResponse get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetAwsIS() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.Get
                ResponseInputStream<GetObjectResponse> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetAwsOptional() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.Get
                Optional<GetObjectResponse> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetAwsOptionalIS() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.Get
                Optional<ResponseInputStream<GetObjectResponse>> get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetMetaAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.Get
                HeadObjectResponse get(String key);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientGetMetaOptionalAws() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.Get
                Optional<HeadObjectResponse> get(String key);
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
                ListObjectsV2Response list();
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
                ListObjectsV2Response list(String prefix);
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
                ListObjectsV2Response list(String prefix);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void clientListKeyAndDelimiter() {
        this.compile(List.of(new S3ClientAnnotationProcessor()), """
            @S3.Client("my")
            public interface Client {
            
                @S3.List(value = "{key1}", delimiter = "/")
                ListObjectsV2Response list(String key1);
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
                DeleteObjectResponse delete(String key);
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
                DeleteObjectsResponse delete(List<String> key);
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
                PutObjectResponse put(String key, S3Body body);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Client_Impl");
        assertThat(clazz).isNotNull();
    }
}
