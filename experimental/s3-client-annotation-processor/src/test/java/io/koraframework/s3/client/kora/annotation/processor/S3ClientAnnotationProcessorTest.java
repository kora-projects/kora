package io.koraframework.s3.client.kora.annotation.processor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class S3ClientAnnotationProcessorTest extends AbstractS3ClientTest {

    @Test
    void testFactoryTag() throws Exception {
        this.compile("""
            @S3.Client(factoryTag = Client.CustomS3FactoryTag.class)
            public interface Client {
                final class CustomS3FactoryTag {}

                @S3.List
                List<String> list(S3Credentials creds, @Bucket String bucket, String prefix);
            }
            """);

        var generatedModule = Paths.get(".", "build", "in-test-generated", "sources")
            .resolve(this.testPackage().replace('.', '/'))
            .resolve("$Client_Module.java");
        assertThat(Files.readString(generatedModule))
            .contains("@Tag(Client.CustomS3FactoryTag.class) S3ClientFactory clientFactory");
    }

    @Test
    void testClientIsFinalComponentWithoutAop() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @S3.List
                List<String> list(S3Credentials creds, @Bucket String bucket, String prefix);
            }
            """);

        assertThat(java.lang.reflect.Modifier.isFinal(client.objectClass.getModifiers())).isTrue();
        assertThat(client.objectClass.getAnnotation(io.koraframework.common.annotation.Component.class)).isNotNull();
    }

    @Test
    void testAopProxyIsGeneratedForClient() {
        var client = this.compile("""
            @S3.Client
            public interface Client {
                @io.koraframework.logging.common.annotation.Log
                @S3.List
                List<String> list(S3Credentials creds, @Bucket String bucket, String prefix);
            }
            """);

        assertThat(java.lang.reflect.Modifier.isFinal(client.objectClass.getModifiers())).isFalse();
        var proxy = loadClass("$Client_Module_Impl__AopProxy");
        assertThat(proxy.getSuperclass()).isEqualTo(client.objectClass);
        assertThat(proxy.getAnnotation(io.koraframework.common.annotation.Component.class)).isNotNull();
    }
}
