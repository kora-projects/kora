package io.koraframework.s3.client;

import io.koraframework.s3.client.kora.S3Credentials;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * LocalStack Community does not enforce IAM and does not validate credentials,
 * so authorization tests live only in {@link RustFsS3ClientTest}.
 * 4.14.0 is the last LocalStack image that starts without LOCALSTACK_AUTH_TOKEN.
 */
class LocalStackS3ClientTest extends AbstractS3ClientTest {

    static GenericContainer<?> localstack = new GenericContainer<>(DockerImageName.parse("localstack/localstack:4.14.0"))
        .withEnv("SERVICES", "s3")
        .withExposedPorts(4566)
        .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2));

    static {
        localstack.start();
    }

    static MinioClient minioClient;

    @BeforeAll
    static void beforeAll() {
        minioClient = MinioClient.builder()
            .httpClient(ok)
            .endpoint("http://" + localstack.getHost() + ":" + localstack.getMappedPort(4566))
            .credentials("test", "test")
            .region(REGION)
            .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        minioClient.makeBucket(MakeBucketArgs.builder()
            .bucket(bucketName)
            // MinIO SDK always sends LocationConstraint, which LocalStack rejects for us-east-1
            .region(REGION)
            .build());
    }

    @Override
    String endpoint() {
        return "http://" + localstack.getHost() + ":" + localstack.getMappedPort(4566);
    }

    @Override
    S3Credentials adminCredentials() {
        return S3Credentials.of("test", "test");
    }

    @Override
    MinioClient minioClient() {
        return minioClient;
    }
}
