package ru.tinkoff.kora.aws.s3;

import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import ru.tinkoff.kora.aws.s3.exception.S3ClientDeleteException;
import ru.tinkoff.kora.aws.s3.exception.S3ClientErrorException;
import ru.tinkoff.kora.aws.s3.exception.S3ClientNoSuchKeyException;
import ru.tinkoff.kora.aws.s3.exception.S3ClientResponseException;
import ru.tinkoff.kora.aws.s3.impl.S3ClientImpl;
import ru.tinkoff.kora.aws.s3.impl.xml.DeleteObjectsResult;
import ru.tinkoff.kora.aws.s3.model.Range;
import ru.tinkoff.kora.aws.s3.model.request.ListObjectsArgs;
import ru.tinkoff.kora.aws.s3.model.response.ListBucketResult;
import ru.tinkoff.kora.http.client.ok.OkHttpClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3ClientTest {
    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio"))
        .withCommand("server", "/home/shared")
        .withEnv("SERVICES", "s3")
        .withStartupTimeout(Duration.ofMinutes(1))
        .withNetworkAliases("s3")
        .withExposedPorts(9000);
    static okhttp3.OkHttpClient ok = new okhttp3.OkHttpClient.Builder()
        .build();
    static MinioClient minioClient;

    AwsCredentials credentials = AwsCredentials.of("minioadmin", "minioadmin");
    AwsCredentials invalidCredentials = AwsCredentials.of("test", "test");
    S3ClientConfig config;

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
        this.config = mock(S3ClientConfig.class);
        when(config.endpoint()).thenReturn("http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        when(config.addressStyle()).thenReturn(S3ClientConfig.AddressStyle.PATH);
        when(config.region()).thenReturn("us-east-1");
        when(config.upload()).thenReturn(Mockito.mock());
        when(config.upload().singlePartUploadLimit()).thenCallRealMethod();
        when(config.upload().chunkSize()).thenCallRealMethod();
        when(config.upload().partSize()).thenCallRealMethod();
    }

    S3Client s3Client() {
        var httpClient = new OkHttpClient(ok);
        return new S3ClientImpl(httpClient, config);
    }

    @Nested
    class HeadObject {

        @Test
        void testHeadObjectThrowsErrorOnUnknownObject() throws Exception {
            assertThatThrownBy(() -> s3Client().headObject(credentials, "test", UUID.randomUUID().toString()))
                .isInstanceOf(S3ClientNoSuchKeyException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .hasFieldOrPropertyWithValue("errorMessage", "Object does not exist");
        }

        @Test
        void testHeadObjectThrowsErrorOnUnknownBucket() throws Exception {
            // HEAD throws 404 without a body (because HEAD has no body), so we cannot read code and message and detect if it's no bucket or no key
            assertThatThrownBy(() -> s3Client().headObject(credentials, UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .hasFieldOrPropertyWithValue("errorMessage", "Object does not exist");
        }

        @Test
        void testHeadObjectForbidden() throws Exception {
            assertThatThrownBy(() -> s3Client().headObject(invalidCredentials, UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(S3ClientResponseException.class)
                .hasFieldOrPropertyWithValue("httpCode", 403);
        }

        @Test
        void testHeadObjectOptionalObjectReturnsNullOnUnknownObjects() {
            var object = s3Client().headObjectOptional(credentials, "test", UUID.randomUUID().toString());
            assertThat(object).isNull();
        }

        @Test
        void testHeadObjectOptionalObjectReturnsNullOnUnknownBucket() {
            var object = s3Client().headObjectOptional(credentials, UUID.randomUUID().toString(), UUID.randomUUID().toString());
            assertThat(object).isNull();
        }

        @Test
        void testHeadObjectdataValidObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            var metadata = s3Client().headObject(credentials, "test", key);
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
            var metadata = s3Client().headObjectOptional(credentials, "test", key);
            assertThat(metadata).isNotNull();
            assertThat(metadata.bucket()).isEqualTo("test");
            assertThat(metadata.key()).isEqualTo(key);
            assertThat(metadata.size()).isEqualTo(content.length);
        }
    }

    @Nested
    class GetObject {
        @Test
        void testInvalidAccessKey() {
            assertThatThrownBy(() -> s3Client().getObject(invalidCredentials, "test", UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "InvalidAccessKeyId")
                .hasFieldOrPropertyWithValue("errorMessage", "The Access Key Id you provided does not exist in our records.");
        }

        @Test
        void testInvalidSecretKey() {
            assertThatThrownBy(() -> s3Client().getObject(AwsCredentials.of("minioadmin", "test"), "test", UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SignatureDoesNotMatch")
                .hasFieldOrPropertyWithValue("errorMessage", "The request signature we calculated does not match the signature you provided. Check your key and signing method.");
        }

        @Test
        void testGetObjectThrowsErrorOnUnknownObject() {
            assertThatThrownBy(() -> s3Client().getObject(credentials, "test", UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientNoSuchKeyException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .hasFieldOrPropertyWithValue("errorMessage", "The specified key does not exist.");
        }

        @Test
        void testGetObjectThrowsErrorOnUnknownBucket() {
            assertThatThrownBy(() -> s3Client().getObject(credentials, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchBucket")
                .hasFieldOrPropertyWithValue("errorMessage", "The specified bucket does not exist");
        }

        @Test
        void testGetOptionalObjectReturnsNullOnUnknownObjects() {
            var object = s3Client().getObject(credentials, "test", UUID.randomUUID().toString(), null, false);
            assertThat(object).isNull();
        }

        @Test
        void testGetOptionalObjectReturnsNullOnUnknownBucket() {
            var object = s3Client().getObject(credentials, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, false);
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
            try (var object = s3Client().getObject(credentials, "test", key, null, true)) {
                assertThat(object).isNotNull();
                try (var body = object.body()) {
                    assertThat(body.contentLength()).isEqualTo(content.length);
                    assertThat(body).isNotNull();
                    assertThat(body.asInputStream().readAllBytes()).isEqualTo(content);
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
            var args = new ru.tinkoff.kora.aws.s3.model.request.GetObjectArgs();
            try (var object = s3Client().getObject(credentials, "test", key, args.setRange(Range.fromTo(1, 5)), true)) {
                assertThat(object).isNotNull();
                assertThat(object.contentRange().completeLength()).isEqualTo(content.length);
                try (var body = object.body()) {
                    assertThat(body.contentLength()).isEqualTo(5);
                    assertThat(body.asInputStream().readAllBytes()).isEqualTo(Arrays.copyOfRange(content, 1, 6));
                }
            }
            try (var object = s3Client().getObject(credentials, "test", key, args.setRange(Range.from(5)), true)) {
                assertThat(object).isNotNull();
                assertThat(object.contentRange().completeLength()).isEqualTo(content.length);
                try (var body = object.body()) {
                    assertThat(body.contentLength()).isEqualTo(content.length - 5);
                    assertThat(body.asInputStream().readAllBytes()).isEqualTo(Arrays.copyOfRange(content, 5, content.length));
                }
            }
            try (var object = s3Client().getObject(credentials, "test", key, args.setRange(Range.last(5)), true)) {
                assertThat(object).isNotNull();
                assertThat(object.contentRange().completeLength()).isEqualTo(content.length);
                try (var body = object.body()) {
                    assertThat(body.contentLength()).isEqualTo(5);
                    assertThat(body.asInputStream().readAllBytes()).isEqualTo(Arrays.copyOfRange(content, content.length - 5, content.length));
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
            try (var object = s3Client().getObject(credentials, "test", key, null, false)) {
                assertThat(object).isNotNull();
                try (var body = object.body()) {
                    assertThat(body).isNotNull();
                    assertThat(body.contentLength()).isEqualTo(content.length);
                    assertThat(body.asInputStream().readAllBytes()).isEqualTo(content);
                    assertThat(body.contentType()).isEqualTo("text/plain");
                }
            }
        }

    }

    @Nested
    class DeleteObject {
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

            s3Client().deleteObject(credentials, "test", key);

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

            s3Client().deleteObject(credentials, "test", key);
        }

        @Test
        void testDeleteObjectSuccessOnBucketThatDoesNotExist() throws Exception {
            var key = UUID.randomUUID().toString();

            s3Client().deleteObject(credentials, key, key);
        }

        @Test
        void testDeleteObjectAccessError() {
            var key = UUID.randomUUID().toString();
            assertThatThrownBy(() -> s3Client().deleteObject(invalidCredentials, key, key))
                .isInstanceOf(S3ClientResponseException.class)
                .hasFieldOrPropertyWithValue("httpCode", 403);
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

            s3Client().deleteObjects(credentials, "test", List.of(key1, key2, key3));

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
            minio.copyFileToContainer(Transferable.of(policy), "/tmp/getonly.json");
            minio.execInContainer("mc", "alias", "set", "minioadmin", "http://localhost:9000", "minioadmin", "minioadmin");
            minio.execInContainer("mc", "admin", "policy", "create", "minioadmin", "getonly", "/tmp/getonly.json");
            minio.execInContainer("mc", "admin", "user", "add", "minioadmin", "testDeleteObjectsWithError", "testDeleteObjectsWithError");
            minio.execInContainer("mc", "admin", "policy", "attach", "minioadmin", "getonly", "--user=testDeleteObjectsWithError");

            var key1 = UUID.randomUUID().toString();
            var key2 = UUID.randomUUID().toString();
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
            try {
                assertThatThrownBy(() -> s3Client().deleteObjects(AwsCredentials.of("testDeleteObjectsWithError", "testDeleteObjectsWithError"), "test", List.of(key1, key2)))
                    .isInstanceOf(S3ClientDeleteException.class)
                    .asInstanceOf(InstanceOfAssertFactories.throwable(S3ClientDeleteException.class))
                    .extracting(S3ClientDeleteException::getErrors, InstanceOfAssertFactories.list(DeleteObjectsResult.Error.class))
                    .containsExactly(
                        new DeleteObjectsResult.Error("AccessDenied", key1, "Access Denied.", null),
                        new DeleteObjectsResult.Error("AccessDenied", key2, "Access Denied.", null)
                    )
                ;
            } finally {
                minio.execInContainer("mc", "admin", "user", "remove", "testDeleteObjectsWithError");
            }
        }
    }

    @Nested
    class PutObject {
        @Test
        void testPutObject() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(1024 * 1024 * 8);
            var writer = new S3Client.ContentWriter() {
                @Override
                public void write(OutputStream os) throws IOException {
                    os.write(content);
                }

                @Override
                public long length() {
                    return content.length;
                }
            };
            try {
                var etag = s3Client().putObject(credentials, "test", key, writer);
                assertThat(etag)
                    .isNotNull()
                    .isNotEmpty();
                try (var object = s3Client().getObject(credentials, "test", key);
                     var body = object.body();
                     var is = body.asInputStream()) {
                    var receivedContent = is.readAllBytes();
                    assertThat(receivedContent).isEqualTo(content);
                }
            } finally {
                s3Client().deleteObject(credentials, "test", key);
            }
        }

        @Test
        void testPutObjectByteArray() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(1024 * 1024 * 8);
            try {
                var etag = s3Client().putObject(credentials, "test", key, content, 0, content.length);
                assertThat(etag)
                    .isNotNull()
                    .isNotEmpty();
                try (var object = s3Client().getObject(credentials, "test", key);
                     var body = object.body();
                     var is = body.asInputStream()) {
                    var receivedContent = is.readAllBytes();
                    assertThat(receivedContent).isEqualTo(content);
                }
            } finally {
                s3Client().deleteObject(credentials, "test", key);
            }
        }
    }


    @Nested
    class Multipart {
        @Test
        void testCreateMultipartUpload() throws Exception {
            var prefix = UUID.randomUUID().toString();
            var key = UUID.randomUUID().toString();

            var uploadId = s3Client().createMultipartUpload(credentials, "test", prefix + "/" + key);
            assertThat(uploadId).isNotNull();
            try {
                var listResult = s3Client().listMultipartUploads(credentials, "test", null);

                assertThat(listResult.uploads()).hasSize(1);
                assertThat(listResult.uploads().getFirst().uploadId()).isEqualTo(uploadId);
                assertThat(listResult.uploads().getFirst().key()).isEqualTo(prefix + "/" + key);
            } finally {
                s3Client().abortMultipartUpload(credentials, "test", prefix + "/" + key, uploadId);
            }
        }

        @Test
        void testAbortMultipartUpload() throws Exception {
            var prefix = UUID.randomUUID().toString();
            var key = UUID.randomUUID().toString();

            var uploadId = s3Client().createMultipartUpload(credentials, "test", prefix + "/" + key);
            s3Client().abortMultipartUpload(credentials, "test", prefix + "/" + key, uploadId);

            var afterListResult = s3Client().listMultipartUploads(credentials, "test", null);

            assertThat(afterListResult.uploads()).isEmpty();
        }

        @Test
        void testUploadPart() {
            var key = UUID.randomUUID().toString();
            var content1 = randomBytes(1024 * 1024 * 8);
            var content2 = randomBytes(1024);

            var uploadId = s3Client().createMultipartUpload(credentials, "test", key);
            try {
                var writer = new S3Client.ContentWriter() {
                    @Override
                    public void write(OutputStream os) throws IOException {
                        os.write(content1);
                    }

                    @Override
                    public long length() {
                        return content1.length;
                    }
                };
                var etag1 = s3Client().uploadPart(credentials, "test", key, uploadId, 1, writer);
                assertThat(etag1).isNotNull();
                var etag2 = s3Client().uploadPart(credentials, "test", key, uploadId, 2, content2, 0, content2.length);
                assertThat(etag2).isNotNull();

                var list1 = s3Client().listParts(credentials, "test", key, uploadId, 1, null);
                assertThat(list1.parts()).hasSize(1);
                assertThat(list1.truncated()).isTrue();
                assertThat(list1.nextPartNumberMarker()).isNotNull();
                var list2 = s3Client().listParts(credentials, "test", key, uploadId, 1, list1.nextPartNumberMarker());
                assertThat(list2.parts()).hasSize(1);
                assertThat(list2.truncated()).isFalse();
            } finally {
                s3Client().abortMultipartUpload(credentials, "test", key, uploadId);
            }
        }

        @Test
        void testCompleteMultipartUpload() throws Exception {
            var key = UUID.randomUUID().toString();
            var content1 = randomBytes(1024 * 1024 * 8);
            var content2 = randomBytes(1024);

            var uploadId = s3Client().createMultipartUpload(credentials, "test", key);
            try {
                var writer = new S3Client.ContentWriter() {
                    @Override
                    public void write(OutputStream os) throws IOException {
                        os.write(content1);
                    }

                    @Override
                    public long length() {
                        return content1.length;
                    }
                };
                var part1 = s3Client().uploadPart(credentials, "test", key, uploadId, 1, writer);
                var part2 = s3Client().uploadPart(credentials, "test", key, uploadId, 2, content2, 0, content2.length);

                var etag = s3Client().completeMultipartUpload(
                    credentials,
                    "test",
                    key,
                    uploadId,
                    List.of(part1, part2),
                    null
                );
                assertThat(etag).isNotNull();

                try (var object = s3Client().getObject(credentials, "test", key);
                     var body = object.body();
                     var is = body.asInputStream()) {
                    var content = is.readAllBytes();
                    assertThat(content.length).isEqualTo(content1.length + content2.length);
                    assertThat(Arrays.copyOfRange(content, 0, content1.length)).isEqualTo(content1);
                    assertThat(Arrays.copyOfRange(content, content1.length, content.length)).isEqualTo(content2);
                }
            } finally {
                s3Client().deleteObject(credentials, "test", key);
            }
        }
    }

    @Nested
    class ListMeta {

        @Test
        void testListMetadata() throws Exception {
            var prefix = "testListMetadata0" + UUID.randomUUID();
            var key = prefix + "/test1/" + UUID.randomUUID();
            var content = randomBytes(1024);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            var args = new ListObjectsArgs()
                .setPrefix(prefix + "/")
                .setMaxKeys(10)
                .setFetchOwner("true");
            assertThat(s3Client().listObjectsV2(credentials, "test", args))
                .isNotNull()
                .extracting(ListBucketResult::items, InstanceOfAssertFactories.list(ListBucketResult.ListBucketItem.class))
                .hasSize(1);

            for (int i = 0; i < 10; i++) {
                var moreKey = prefix + "/test/" + UUID.randomUUID();
                var moreContent = randomBytes(1024);
                minioClient.putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(moreKey)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(moreContent), moreContent.length, -1)
                    .build());
            }

            assertThat(s3Client().listObjectsV2(credentials, "test", args).items())
                .isNotNull()
                .hasSize(10);
            assertThat(s3Client().listObjectsV2(credentials, "test", args.clone().setMaxKeys(20)).items())
                .isNotNull()
                .hasSize(11);
        }

        @Test
        void testListDirs() throws Exception {
            var prefix = "testListMetadata0" + UUID.randomUUID();
            var args = new ListObjectsArgs()
                .setPrefix(prefix + "/")
                .setDelimiter("/")
                .setMaxKeys(20);

            for (int i = 0; i < 10; i++) {
                var moreKey = prefix + "/test" + i + "/" + UUID.randomUUID();
                var moreContent = randomBytes(1024);
                minioClient.putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(moreKey)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(moreContent), moreContent.length, -1)
                    .build());
            }

            assertThat(s3Client().listObjectsV2(credentials, "test", args).commonPrefixes())
                .isNotNull()
                .hasSize(10);
        }

        @Test
        void testListMetadataOnInvalidBucket() {
            assertThatThrownBy(() -> s3Client().listObjectsV2(credentials, UUID.randomUUID().toString(), null))
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

            assertThat(s3Client().listObjectsV2Iterator(credentials, "test", new ListObjectsArgs().setPrefix(prefix).setMaxKeys(42)))
                .toIterable()
                .hasSize(101)
            ;
        }
    }


    byte[] randomBytes(long len) {
        var bytes = new byte[Math.toIntExact(len)];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
