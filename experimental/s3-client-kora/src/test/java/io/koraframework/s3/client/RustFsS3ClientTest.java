package io.koraframework.s3.client;

import io.koraframework.s3.client.kora.S3Credentials;
import io.koraframework.s3.client.kora.exception.S3ClientDeleteException;
import io.koraframework.s3.client.kora.exception.S3ClientErrorException;
import io.koraframework.s3.client.kora.exception.S3ClientResponseException;
import io.koraframework.s3.client.kora.impl.xml.DeleteObjectsResult;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.admin.MinioAdminClient;
import io.minio.admin.Status;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class RustFsS3ClientTest extends AbstractS3ClientTest {

    static GenericContainer<?> rustfs = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:latest"))
        .withEnv("RUSTFS_ACCESS_KEY", "rustfsadmin")
        .withEnv("RUSTFS_SECRET_KEY", "rustfsadmin")
        .withStartupTimeout(Duration.ofMinutes(1))
        .withExposedPorts(9000);
    static MinioClient minioClient;
    static MinioAdminClient adminClient;

    S3Credentials invalidCredentials = S3Credentials.of("test", "test");

    @BeforeAll
    static void beforeAll() {
        rustfs.start();
        var endpoint = "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000);
        minioClient = MinioClient.builder()
            .httpClient(ok)
            .endpoint(endpoint)
            .credentials("rustfsadmin", "rustfsadmin")
            .build();
        adminClient = MinioAdminClient.builder()
            .httpClient(ok)
            .endpoint(endpoint)
            .credentials("rustfsadmin", "rustfsadmin")
            .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        minioClient.makeBucket(MakeBucketArgs.builder()
            .bucket(bucketName)
            .build());
    }

    @AfterAll
    static void afterAll() {
        rustfs.stop();
    }

    @Override
    String endpoint() {
        return "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000);
    }

    @Override
    S3Credentials adminCredentials() {
        return S3Credentials.of("rustfsadmin", "rustfsadmin");
    }

    @Override
    MinioClient minioClient() {
        return minioClient;
    }

    @Nested
    class Auth {

        @Test
        void testHeadObjectForbidden() {
            assertThatThrownBy(() -> s3Client().headObject(invalidCredentials, UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(S3ClientResponseException.class)
                .hasFieldOrPropertyWithValue("httpCode", 403);
        }

        @Test
        void testInvalidAccessKey() {
            assertThatThrownBy(() -> s3Client().getObject(invalidCredentials, bucketName, UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "InvalidAccessKeyId")
                .hasFieldOrPropertyWithValue("errorMessage", "The Access Key Id you provided does not exist in our records.");
        }

        @Test
        void testInvalidSecretKey() {
            assertThatThrownBy(() -> s3Client().getObject(S3Credentials.of("rustfsadmin", bucketName), bucketName, UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SignatureDoesNotMatch");
        }

        @Test
        void testDeleteObjectAccessError() {
            var key = UUID.randomUUID().toString();
            assertThatThrownBy(() -> s3Client().deleteObject(invalidCredentials, key, key))
                .isInstanceOf(S3ClientResponseException.class)
                .hasFieldOrPropertyWithValue("httpCode", 403);
        }

        @Test
        void testDeleteObjectsWithError() throws Exception {
            var policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Action": [
                        "s3:GetObject"
                      ],
                      "Effect": "Allow",
                      "Resource": [
                        "arn:aws:s3:::testdeleteobjectswitherror/*"
                      ],
                      "Sid": ""
                    }
                  ]
                }
                """;
            adminClient.addCannedPolicy("getonly", policy);
            adminClient.addUser("testDeleteObjectsWithError", Status.ENABLED, "testDeleteObjectsWithError", null, null);
            adminClient.setPolicy("testDeleteObjectsWithError", false, "getonly");

            var key1 = UUID.randomUUID().toString();
            var key2 = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(key1)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                .build());
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(key2)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                .build());
            try {
                assertThatThrownBy(() -> s3Client().deleteObjects(S3Credentials.of("testDeleteObjectsWithError", "testDeleteObjectsWithError"), bucketName, List.of(key1, key2)))
                    .isInstanceOf(S3ClientDeleteException.class)
                    .asInstanceOf(InstanceOfAssertFactories.throwable(S3ClientDeleteException.class))
                    .extracting(S3ClientDeleteException::getErrors, InstanceOfAssertFactories.list(DeleteObjectsResult.Error.class))
                    .extracting(DeleteObjectsResult.Error::code, DeleteObjectsResult.Error::key)
                    .containsExactly(
                        tuple("AccessDenied", key1),
                        tuple("AccessDenied", key2)
                    );
            } finally {
                adminClient.deleteUser("testDeleteObjectsWithError");
            }
        }

        @Test
        void testDeleteObjectsWithErrorOnEdgeKeys() throws Exception {
            // No policy attached, so the user is denied every delete
            adminClient.addUser("testDeleteObjectsEdgeKeys", Status.ENABLED, "testDeleteObjectsEdgeKeys", null, null);
            var keys = edgeKeys();
            for (var key : keys) {
                putObject(key);
            }
            try {
                assertThatThrownBy(() -> s3Client().deleteObjects(S3Credentials.of("testDeleteObjectsEdgeKeys", "testDeleteObjectsEdgeKeys"), bucketName, keys))
                    .isInstanceOf(S3ClientDeleteException.class)
                    .asInstanceOf(InstanceOfAssertFactories.throwable(S3ClientDeleteException.class))
                    .extracting(S3ClientDeleteException::getErrors, InstanceOfAssertFactories.list(DeleteObjectsResult.Error.class))
                    .extracting(DeleteObjectsResult.Error::key)
                    .containsExactlyInAnyOrderElementsOf(keys);
            } finally {
                adminClient.deleteUser("testDeleteObjectsEdgeKeys");
                s3Client().deleteObjects(credentials, bucketName, keys);
            }
        }
    }
}
