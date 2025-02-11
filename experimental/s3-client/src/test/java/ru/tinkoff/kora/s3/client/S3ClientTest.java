package ru.tinkoff.kora.s3.client;

import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;
import ru.tinkoff.kora.http.client.ok.OkHttpClient;
import ru.tinkoff.kora.s3.client.exception.S3ClientErrorException;
import ru.tinkoff.kora.s3.client.exception.S3ClientUnknownException;
import ru.tinkoff.kora.s3.client.impl.S3ClientImpl;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.telemetry.S3Telemetry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3ClientTest {
    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio"))
        .withCommand("server", "/home/shared")
        .withEnv("SERVICES", "s3")
        .withStartupTimeout(Duration.ofMinutes(1))
//        .withImagePullPolicy(i -> !k8s)
        .withNetworkAliases("s3")
        .withExposedPorts(9000);
    static okhttp3.OkHttpClient ok = new okhttp3.OkHttpClient.Builder()
//        .addInterceptor(new HttpLoggingInterceptor(System.out::println).setLevel(HttpLoggingInterceptor.Level.HEADERS))
        .build();
    static MinioClient minioClient;

    private S3Config config;

    @BeforeAll
    static void beforeAll() throws Exception {
        minio.start();
        minioClient = MinioClient.builder()
            .httpClient(ok)
            .endpoint("http://" + minio.getHost() + ":" + minio.getMappedPort(9000))
            .credentials("minioadmin", "minioadmin")
            .build();
        minioClient.makeBucket(MakeBucketArgs.builder()
            .bucket("test")
            .build());
    }

    @AfterAll
    static void afterAll() {
        minio.stop();
    }


    @BeforeEach
    void setUp() {
        this.config = mock(S3Config.class);
        when(config.endpoint()).thenReturn("http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        when(config.addressStyle()).thenReturn(S3Config.AddressStyle.PATH);
        when(config.region()).thenReturn("us-east-1");
        when(config.upload()).thenReturn(Mockito.mock());
        when(config.upload().singlePartUploadLimit()).thenCallRealMethod();
        when(config.upload().chunkSize()).thenCallRealMethod();
        when(config.upload().partSize()).thenCallRealMethod();
    }

    S3Client s3Client(String accessKey, String secretKey) {
        when(config.accessKey()).thenReturn(accessKey);
        when(config.secretKey()).thenReturn(secretKey);
        var telemetry = mock(S3Telemetry.class);
        when(telemetry.getObject(anyString(), anyString())).thenReturn(mock());
        when(telemetry.getMetadata(anyString(), anyString())).thenReturn(mock());
        when(telemetry.listMetadata(anyString(), any(), any())).thenReturn(mock());
        when(telemetry.deleteObject(anyString(), any())).thenReturn(mock());
        when(telemetry.deleteObjects(anyString(), any())).thenReturn(mock());
        when(telemetry.putObject(anyString(), any(), anyLong())).thenReturn(mock());
        when(telemetry.putObjectPart(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(mock());
        when(telemetry.startMultipartUpload(anyString(), anyString())).thenReturn(mock());
        when(telemetry.abortMultipartUpload(anyString(), anyString(), anyString())).thenReturn(mock());
        when(telemetry.completeMultipartUpload(anyString(), anyString(), anyString())).thenReturn(mock());

        var httpTelemetry = mock(HttpClientTelemetry.class);

        var httpClient = new OkHttpClient(ok);
        return new S3ClientImpl(httpClient, config, (config, clazz) -> telemetry, (con, cl) -> httpTelemetry, Object.class);
    }

    S3Client s3Client() {
        return s3Client("minioadmin", "minioadmin");
    }

    @Nested
    class Get {
        @Test
        void testInvalidAccessKey() {
            assertThatThrownBy(() -> s3Client("test", "test").get("test", UUID.randomUUID().toString(), null))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "InvalidAccessKeyId")
                .hasFieldOrPropertyWithValue("errorMessage", "The Access Key Id you provided does not exist in our records.");
        }

        @Test
        void testInvalidSecretKey() {
            assertThatThrownBy(() -> s3Client("minioadmin", "test").get("test", UUID.randomUUID().toString(), null))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SignatureDoesNotMatch")
                .hasFieldOrPropertyWithValue("errorMessage", "The request signature we calculated does not match the signature you provided. Check your key and signing method.");
        }

        @Test
        void testGetObjectThrowsErrorOnUnknownObject() {
            assertThatThrownBy(() -> s3Client().get("test", UUID.randomUUID().toString(), null))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .hasFieldOrPropertyWithValue("errorMessage", "The specified key does not exist.");
        }

        @Test
        void testGetObjectThrowsErrorOnUnknownBucket() {
            assertThatThrownBy(() -> s3Client().get(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchBucket")
                .hasFieldOrPropertyWithValue("errorMessage", "The specified bucket does not exist");
        }

        @Test
        void testGetOptionalObjectReturnsNullOnUnknownObjects() {
            var object = s3Client().getOptional("test", UUID.randomUUID().toString(), null);
            assertThat(object).isNull();
        }

        @Test
        void testGetOptionalObjectReturnsNullOnUnknownBucket() {
            var object = s3Client().getOptional(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null);
            assertThat(object).isNull();
        }

        @Test
        void testGetValidObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().repeat(10240).getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            try (var object = s3Client().get("test", key, null)) {
                assertThat(object).isNotNull();
                assertThat(object.meta().size()).isEqualTo(content.length);
                assertThat(object.meta().bucket()).isEqualTo("test");
                assertThat(object.meta().key()).isEqualTo(key);
                try (var body = object.body()) {
                    assertThat(body).isNotNull();
                    assertThat(body.asBytes()).isEqualTo(content);
                    assertThat(body.contentType()).isEqualTo("text/plain");
                }
            }
        }

        @Test
        void testGetRange() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            try (var object = s3Client().get("test", key, new S3Client.RangeData.Range(1, 5))) {
                assertThat(object.meta().size()).isEqualTo(content.length);
                try (var body = object.body()) {
                    assertThat(body.size()).isEqualTo(5);
                    assertThat(body.asBytes()).isEqualTo(Arrays.copyOfRange(content, 1, 6));
                }
            }
            try (var object = s3Client().get("test", key, new S3Client.RangeData.StartFrom(5))) {
                assertThat(object.meta().size()).isEqualTo(content.length);
                try (var body = object.body()) {
                    assertThat(body.size()).isEqualTo(content.length - 5);
                    assertThat(body.asBytes()).isEqualTo(Arrays.copyOfRange(content, 5, content.length));
                }
            }
            try (var object = s3Client().get("test", key, new S3Client.RangeData.LastN(5))) {
                assertThat(object.meta().size()).isEqualTo(content.length);
                try (var body = object.body()) {
                    assertThat(body.size()).isEqualTo(5);
                    assertThat(body.asBytes()).isEqualTo(Arrays.copyOfRange(content, content.length - 5, content.length));
                }
            }
        }

        @Test
        void testGetOptionalValidObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            try (var object = s3Client().getOptional("test", key, null)) {
                assertThat(object).isNotNull();
                assertThat(object.meta().size()).isEqualTo(content.length);
                assertThat(object.meta().bucket()).isEqualTo("test");
                assertThat(object.meta().key()).isEqualTo(key);
                try (var body = object.body()) {
                    assertThat(body).isNotNull();
                    assertThat(body.asBytes()).isEqualTo(content);
                    assertThat(body.contentType()).isEqualTo("text/plain");
                }
            }
        }

    }

    @Nested
    class GetMeta {

        @Test
        void testGetMetaThrowsErrorOnUnknownObject() throws Exception {
            assertThatThrownBy(() -> s3Client().getMeta("test", UUID.randomUUID().toString()))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .hasFieldOrPropertyWithValue("errorMessage", "Object does not exist");
        }

        @Test
        void testGetMetaThrowsErrorOnUnknownBucket() throws Exception {
            // HEAD throws 404 without a body (because HEAD has no body), so we cannot read code and message and detect if it's no bucket or no key
            assertThatThrownBy(() -> s3Client().getMeta(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .hasFieldOrPropertyWithValue("errorMessage", "Object does not exist");
        }

        @Test
        void testGetMetaOptionalObjectReturnsNullOnUnknownObjects() {
            var object = s3Client().getMetaOptional("test", UUID.randomUUID().toString());
            assertThat(object).isNull();
        }

        @Test
        void testGetMetaOptionalObjectReturnsNullOnUnknownBucket() {
            var object = s3Client().getMetaOptional(UUID.randomUUID().toString(), UUID.randomUUID().toString());
            assertThat(object).isNull();
        }

        @Test
        void testGetMetadataValidObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            var metadata = s3Client().getMeta("test", key);
            assertThat(metadata).isNotNull();
            assertThat(metadata.bucket()).isEqualTo("test");
            assertThat(metadata.key()).isEqualTo(key);
            assertThat(metadata.size()).isEqualTo(content.length);
        }

        @Test
        void testGetOptionalMetadataValidObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            var metadata = s3Client().getMetaOptional("test", key);
            assertThat(metadata).isNotNull();
            assertThat(metadata.bucket()).isEqualTo("test");
            assertThat(metadata.key()).isEqualTo(key);
            assertThat(metadata.size()).isEqualTo(content.length);
        }
    }

    @Nested
    class ListMeta {

        @Test
        void testListMetadata() throws Exception {
            var prefix = "testListMetadata0" + UUID.randomUUID();
            var key = prefix + "/" + UUID.randomUUID();
            var content = randomBytes(1024);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());

            assertThat(s3Client().list("test", prefix + "/", null, 10))
                .isNotNull()
                .hasSize(1);

            for (int i = 0; i < 10; i++) {
                var moreKey = prefix + "/" + UUID.randomUUID();
                var moreContent = randomBytes(1024);
                minioClient.putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(moreKey)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(moreContent), moreContent.length, -1)
                    .build());
            }

            assertThat(s3Client().list("test", prefix, null, 10))
                .isNotNull()
                .hasSize(10);
            assertThat(s3Client().list("test", prefix, null, 20))
                .isNotNull()
                .hasSize(11);
        }

        @Test
        void testListMetadataOnInvalidBucket() {
            assertThatThrownBy(() -> s3Client().list(UUID.randomUUID().toString(), "testListMetadata0", null, 10))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchBucket")
                .hasFieldOrPropertyWithValue("errorMessage", "The specified bucket does not exist");
        }

        @Test
        void testListMetadataIterator() throws Exception {
            var prefix = "testListMetadataIterator" + UUID.randomUUID();
            for (int i = 0; i < 101; i++) {
                var key = prefix + "/" + UUID.randomUUID();
                var content = randomBytes(1024);
                minioClient.putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(key)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
            }

            assertThat(s3Client().listIterator("test", prefix, null, 42))
                .toIterable()
                .hasSize(101)
            ;
        }
    }

    @Nested
    class Delete {

        @Test
        void testDeleteObjectSuccessOnValidObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(1024);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());

            s3Client().delete("test", key);

            assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
        }

        @Test
        void testDeleteObjectSuccessOnObjectThatDoesNotExist() throws Exception {
            var key = UUID.randomUUID().toString();

            s3Client().delete("test", key);
        }

        @Test
        void testDeleteObjectSuccessOnBucketThatDoesNotExist() throws Exception {
            var key = UUID.randomUUID().toString();

            s3Client().delete(key, key);
        }

        @Test
        void testDeleteObjects() throws Exception {
            var key1 = UUID.randomUUID().toString();
            var key2 = UUID.randomUUID().toString();
            var key3 = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key1)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key2)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());

            s3Client().delete("test", List.of(key1, key2, key3));

            assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key1)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
            assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key2)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
            assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key3)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
        }
    }

    @Nested
    class Put {
        @Test
        void testPutByteArray() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(32 * 1024);
            s3Client().put("test", key, S3Body.ofBytes(content));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }

        @Test
        void testByteArrayBiggerThanTwoRecommendedPartSizes() throws Exception {
            var key = UUID.randomUUID().toString();
            when(config.upload().singlePartUploadLimit()).thenReturn(Size.of(10, Size.Type.MiB));
            var content = randomBytes(Size.of(21, Size.Type.MiB).toBytes());
            s3Client().put("test", key, S3Body.ofBytes(content));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }

        @Test
        void putKnownSizeStreamLessThanOnePart() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(1024 * 1024);
            s3Client().put("test", key, S3Body.ofInputStream(new ByteArrayInputStream(content), content.length));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }

        @Test
        void putKnownSizeStreamThatFitsInOnePartUpload() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(32 * 1023 * 1024);
            s3Client().put("test", key, S3Body.ofInputStream(new ByteArrayInputStream(content), content.length));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }

        @Test
        void putKnownSizeStreamThatBiggerThanTwoRecommendedPartSizes() throws Exception {
            var key = UUID.randomUUID().toString();
            when(config.upload().singlePartUploadLimit()).thenReturn(Size.of(10, Size.Type.MiB));
            var content = randomBytes(Size.of(21, Size.Type.MiB).toBytes());
            s3Client().put("test", key, S3Body.ofInputStream(new ByteArrayInputStream(content), content.length));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }

        @Test
        void putKnownSizeStreamThatBiggerThanTwoRecommendedPartSizesWithError() throws Exception {
            var key = UUID.randomUUID().toString();
            when(config.upload().singlePartUploadLimit()).thenReturn(Size.of(10, Size.Type.MiB));
            var content = randomBytes(Size.of(21, Size.Type.MiB).toBytes());
            assertThatThrownBy(() -> s3Client().put("test", key, S3Body.ofInputStream(new ByteArrayInputStream(content), content.length + 10)))
                .isInstanceOf(S3ClientUnknownException.class);

            // todo validate that upload removed
        }

        @Test
        void testPutUnknownSizeInputStreamThatFitsInOnePart() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(6 * 1024 * 1024);
            s3Client().put("test", key, S3Body.ofInputStream(new ByteArrayInputStream(content), -1));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }

        @Test
        void testPutUnknownSizeLargeInputStream() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(32 * 1024 * 1024);
            s3Client().put("test", key, S3Body.ofInputStream(new ByteArrayInputStream(content), -1));

            try (var rs = minioClient.getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key)
                .build())) {
                assertThat(rs).hasBinaryContent(content);
            }
        }
    }

    byte[] randomBytes(long len) {
        var bytes = new byte[Math.toIntExact(len)];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
