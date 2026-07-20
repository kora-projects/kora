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
            .resolve("$Client_S3Module.java");
        assertThat(Files.readString(generatedModule))
            .contains("@Tag(Client.CustomS3FactoryTag.class) S3ClientFactory clientFactory");
    }
}
