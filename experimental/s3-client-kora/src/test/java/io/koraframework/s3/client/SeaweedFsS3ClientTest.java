package io.koraframework.s3.client;

import io.koraframework.s3.client.kora.S3Credentials;
import io.koraframework.s3.client.kora.exception.S3ClientDeleteException;
import io.koraframework.s3.client.kora.exception.S3ClientErrorException;
import io.koraframework.s3.client.kora.impl.xml.DeleteObjectsResult;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@Execution(ExecutionMode.SAME_THREAD)
class SeaweedFsS3ClientTest extends AbstractS3ClientTest {

    static final String IDENTITIES = """
        {
          "identities": [
            {
              "name": "admin",
              "credentials": [{"accessKey": "seaweedadmin", "secretKey": "seaweedadmin"}],
              "actions": ["Admin", "Read", "List", "Tagging", "Write"]
            },
            {
              "name": "readonly",
              "credentials": [{"accessKey": "seaweedreadonly", "secretKey": "seaweedreadonly"}],
              "actions": ["Read", "List"]
            }
          ]
        }
        """;

    static GenericContainer<?> seaweedfs = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.47"))
        .withCopyToContainer(Transferable.of(IDENTITIES), "/etc/seaweedfs/s3.json")
        .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data")
        .withExposedPorts(8333)
        .waitingFor(Wait.forLogMessage(".*Start Seaweed S3 API Server.*", 1))
        .withStartupTimeout(Duration.ofMinutes(1));
    static MinioClient minioClient;

    S3Credentials invalidCredentials = S3Credentials.of("test", "test");

    @BeforeAll
    static void beforeAll() throws Exception {
        seaweedfs.start();
        minioClient = MinioClient.builder()
            .httpClient(ok)
            .endpoint("http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333))
            .credentials("seaweedadmin", "seaweedadmin")
            .build();
        minioClient.makeBucket(MakeBucketArgs.builder()
            .bucket("test")
            .build());
    }

    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        bucketName = "test";
    }

    @AfterAll
    static void afterAll() throws Exception {
        minioClient.close();
        seaweedfs.stop();
    }

    @Override
    String endpoint() {
        return "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333);
    }

    @Override
    S3Credentials adminCredentials() {
        return S3Credentials.of("seaweedadmin", "seaweedadmin");
    }

    @Override
    MinioClient minioClient() {
        return minioClient;
    }

    @Override
    boolean supportsListPartsMarker() {
        // SeaweedFS 4.47 ignores part-number-marker and always returns the first page
        return false;
    }

    @Nested
    class Auth {

        @Test
        void testInvalidAccessKey() {
            assertThatThrownBy(() -> s3Client().getObject(invalidCredentials, bucketName, UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "InvalidAccessKeyId");
        }

        @Test
        void testInvalidSecretKey() {
            assertThatThrownBy(() -> s3Client().getObject(S3Credentials.of("seaweedadmin", "test"), bucketName, UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SignatureDoesNotMatch");
        }

        @Test
        void testDeleteObjectsWithError() throws Exception {
            var key1 = UUID.randomUUID().toString();
            var key2 = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            for (var key : List.of(key1, key2)) {
                minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .build());
            }

            assertThatThrownBy(() -> s3Client().deleteObjects(S3Credentials.of("seaweedreadonly", "seaweedreadonly"), bucketName, List.of(key1, key2)))
                .isInstanceOf(S3ClientDeleteException.class)
                .asInstanceOf(InstanceOfAssertFactories.throwable(S3ClientDeleteException.class))
                .extracting(S3ClientDeleteException::getErrors, InstanceOfAssertFactories.list(DeleteObjectsResult.Error.class))
                .extracting(DeleteObjectsResult.Error::code, DeleteObjectsResult.Error::key)
                .containsExactly(
                    tuple("AccessDenied", key1),
                    tuple("AccessDenied", key2)
                );
        }

        @Test
        void testDeleteObjectsWithErrorOnEdgeKeys() throws Exception {
            var keys = edgeKeys();
            for (var key : keys) {
                putObject(key);
            }
            try {
                assertThatThrownBy(() -> s3Client().deleteObjects(S3Credentials.of("seaweedreadonly", "seaweedreadonly"), bucketName, keys))
                    .isInstanceOf(S3ClientDeleteException.class)
                    .asInstanceOf(InstanceOfAssertFactories.throwable(S3ClientDeleteException.class))
                    .extracting(S3ClientDeleteException::getErrors, InstanceOfAssertFactories.list(DeleteObjectsResult.Error.class))
                    .extracting(DeleteObjectsResult.Error::key)
                    .containsExactlyInAnyOrderElementsOf(keys);
            } finally {
                s3Client().deleteObjects(credentials, bucketName, keys);
            }
        }
    }
}
