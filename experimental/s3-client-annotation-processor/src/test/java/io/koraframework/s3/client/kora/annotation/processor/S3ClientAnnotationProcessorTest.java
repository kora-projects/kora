package io.koraframework.s3.client.kora.annotation.processor;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ClientAnnotationProcessorTest extends AbstractS3ClientTest {

    public static final List<String> USED_HTTP_CLIENT_PROVIDERS = new CopyOnWriteArrayList<>();

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

    @Test
    void testTaggedFactoryModuleUsesTaggedHttpClientProvider() throws Exception {
        var result = this.compile(List.of(new KoraAppProcessor()), """
            import io.koraframework.http.client.common.HttpClient;
            import io.koraframework.s3.client.kora.annotation.processor.S3ClientAnnotationProcessorTest;

            @KoraApp
            public interface ExampleApplication extends KoraS3ClientModule {
                final class Second {}

                @Tag(Second.class)
                @FactoryModule
                default S3FactoryModule secondS3() { return new S3FactoryModule("s3second"); }

                default HttpClient defaultHttpClient() {
                    return request -> { throw new IllegalStateException("default"); };
                }

                @Tag(Second.class)
                default S3HttpClientProvider secondHttpClientProvider() {
                    return () -> {
                        S3ClientAnnotationProcessorTest.USED_HTTP_CLIENT_PROVIDERS.add("second");
                        return request -> { throw new IllegalStateException("second"); };
                    };
                }

                @Root
                default Object root(@Tag(Second.class) S3ClientFactory second) { return second; }
            }
            """);
        result.assertSuccess();

        @SuppressWarnings("unchecked")
        var draw = ((Supplier<ApplicationGraphDraw>) result.loadClass("ExampleApplicationGraph").getConstructor().newInstance()).get();
        USED_HTTP_CLIENT_PROVIDERS.clear();
        draw.init();
        assertThat(USED_HTTP_CLIENT_PROVIDERS).containsExactly("second");
    }
}
