package io.koraframework.s3.client;

import io.koraframework.s3.client.kora.S3Client;
import io.koraframework.s3.client.kora.S3ClientConfig;
import io.koraframework.s3.client.kora.S3Credentials;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import io.koraframework.http.client.ok.OkHttpClient;
import io.koraframework.s3.client.kora.exception.S3ClientErrorException;
import io.koraframework.s3.client.kora.exception.S3ClientNoSuchKeyException;
import io.koraframework.s3.client.kora.impl.KoraS3Client;
import io.koraframework.s3.client.kora.model.Range;
import io.koraframework.s3.client.kora.model.request.ListObjectsArgs;
import io.koraframework.s3.client.kora.model.response.ListBucketResult;
import io.koraframework.s3.client.kora.telemetry.impl.NoopS3ClientTelemetry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class AbstractS3ClientTest {

    static okhttp3.OkHttpClient ok = new okhttp3.OkHttpClient.Builder()
        .build();

    S3Credentials credentials;
    S3ClientConfig config;

    abstract String endpoint();

    abstract S3Credentials adminCredentials();

    abstract MinioClient minioClient();

    /**
     * Whether the server honours {@code part-number-marker} in ListParts.
     */
    boolean supportsListPartsMarker() {
        return true;
    }

    /**
     * Object keys that are valid for S3 but stress XML and whitespace handling in DeleteObjects.
     * Every call returns keys under a fresh prefix, so tests do not interfere with each other.
     */
    static List<String> edgeKeys() {
        var id = UUID.randomUUID().toString();
        return List.of(
            id + "/plain",
            " " + id + "/leading-space-at-root",
            id + "/trailing-space ",
            id + "/  both  ",
            id + "/ ",
            id + "/inner  double  space",
            id + "/tab\there",
            id + "/a & b",
            id + "/&",
            id + "/<tag>",
            id + "/quote\"apos'",
            id + "/a &amp; literal",
            id + "/]]>",
            id + "/юникод ключ",
            id + "/emoji 😀 pair",
            id + "/ nbsp ",
            // servers limit a single path segment, so long keys are split into segments
            id + ("/" + "x".repeat(200)).repeat(4),
            id + ("/" + " y &".repeat(50) + " ").repeat(4)
        );
    }

    static Stream<String> edgeKeyStream() {
        return edgeKeys().stream();
    }

    @BeforeEach
    void setUp() {
        this.credentials = adminCredentials();
        this.config = mock(S3ClientConfig.class);
        when(config.endpoint()).thenReturn(endpoint());
        when(config.addressStyle()).thenReturn(S3ClientConfig.AddressStyle.PATH);
        when(config.region()).thenReturn("us-east-1");
        when(config.upload()).thenReturn(Mockito.mock());
        when(config.upload().singlePartUploadLimit()).thenCallRealMethod();
        when(config.upload().chunkSize()).thenCallRealMethod();
        when(config.upload().partSize()).thenCallRealMethod();
    }

    S3Client s3Client() {
        var httpClient = new OkHttpClient(ok);
        return new KoraS3Client(httpClient, config, NoopS3ClientTelemetry.INSTANCE);
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) (long) content.length, -1L)
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
        void testGetObjectThrowsErrorOnUnknownObject() {
            assertThatThrownBy(() -> s3Client().getObject(credentials, "test", UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientNoSuchKeyException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchKey")
                .extracting("errorMessage").asString().isNotBlank();
        }

        @Test
        void testGetObjectThrowsErrorOnUnknownBucket() {
            assertThatThrownBy(() -> s3Client().getObject(credentials, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, true))
                .isInstanceOf(S3ClientErrorException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NoSuchBucket")
                .extracting("errorMessage").asString().isNotBlank();
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                .build());
            var args = new io.koraframework.s3.client.kora.model.request.GetObjectArgs();
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                .build());

            s3Client().deleteObject(credentials, "test", key);

            assertThatThrownBy(() -> minioClient().getObject(GetObjectArgs.builder()
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
        void testDeleteObjects() throws Exception {
            var key1 = UUID.randomUUID().toString();
            var key2 = UUID.randomUUID().toString();
            var key3 = UUID.randomUUID().toString();
            var content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key1)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                .build());
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key2)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                .build());

            s3Client().deleteObjects(credentials, "test", List.of(key1, key2, key3));

            assertThatThrownBy(() -> minioClient().getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key1)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
            assertThatThrownBy(() -> minioClient().getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key2)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
            assertThatThrownBy(() -> minioClient().getObject(GetObjectArgs.builder()
                .bucket("test")
                .object(key3)
                .build()))
                .isInstanceOf(ErrorResponseException.class)
                .extracting("errorResponse")
                .hasFieldOrPropertyWithValue("code", "NoSuchKey");
        }

        @ParameterizedTest
        @MethodSource("io.koraframework.s3.client.AbstractS3ClientTest#edgeKeyStream")
        void testDeleteObjectsEdgeKey(String key) throws Exception {
            putObject(key);

            s3Client().deleteObjects(credentials, "test", List.of(key));

            assertThat(s3Client().headObjectOptional(credentials, "test", key)).isNull();
        }

        @Test
        void testDeleteObjectsEdgeKeysInOneBatchWithMissingKeys() throws Exception {
            var keys = edgeKeys();
            for (var key : keys) {
                putObject(key);
            }
            var all = new ArrayList<>(keys);
            for (var key : keys) {
                all.add(key + "-missing");
            }

            s3Client().deleteObjects(credentials, "test", all);

            for (var key : keys) {
                assertThat(s3Client().headObjectOptional(credentials, "test", key)).as(key).isNull();
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
                if (supportsListPartsMarker()) {
                    var list2 = s3Client().listParts(credentials, "test", key, uploadId, 1, list1.nextPartNumberMarker());
                    assertThat(list2.parts()).hasSize(1);
                    assertThat(list2.truncated()).isFalse();
                }
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
            minioClient().putObject(PutObjectArgs.builder()
                .bucket("test")
                .object(key)
                .contentType("text/plain")
                .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
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
                minioClient().putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(moreKey)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(moreContent), (long) content.length, -1L)
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
                var content = randomBytes(1024);
                minioClient().putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(moreKey)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
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
                .extracting("errorMessage").asString().isNotBlank();
        }

        @Test
        void testListMetadataIterator() throws Exception {
            var prefix = "testListMetadataIterator" + UUID.randomUUID();
            for (int i = 0; i < 101; i++) {
                var key = prefix + "/" + UUID.randomUUID();
                var content = randomBytes(1024);
                minioClient().putObject(PutObjectArgs.builder()
                    .bucket("test")
                    .object(key)
                    .contentType("text/plain")
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .build());
            }

            assertThat(s3Client().listObjectsV2Iterator(credentials, "test", new ListObjectsArgs().setPrefix(prefix).setMaxKeys(42)))
                .toIterable()
                .hasSize(101)
            ;
        }
    }

    @Nested
    class Compatibility {

        @ParameterizedTest
        @ValueSource(strings = {"with space", "with+plus", "with%percent", "with~tilde", "with*star", "юникод", "with&amp=eq"})
        void testPutGetHeadDeleteSpecialKey(String name) throws Exception {
            var key = UUID.randomUUID() + "/" + name;
            var content = randomBytes(1024);

            s3Client().putObject(credentials, "test", key, content, 0, content.length);

            assertThat(s3Client().headObject(credentials, "test", key).size()).isEqualTo(content.length);
            try (var object = s3Client().getObject(credentials, "test", key);
                 var body = object.body()) {
                assertThat(body.asInputStream().readAllBytes()).isEqualTo(content);
            }
            try (var is = minioClient().getObject(GetObjectArgs.builder().bucket("test").object(key).build())) {
                assertThat(is.readAllBytes()).isEqualTo(content);
            }
            s3Client().deleteObjects(credentials, "test", List.of(key));
            assertThat(s3Client().headObjectOptional(credentials, "test", key)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"with space", "with+plus", "юникод"})
        void testListWithSpecialPrefix(String name) throws Exception {
            var prefix = UUID.randomUUID() + "/" + name + "/";
            putObject(prefix + "object");

            assertThat(s3Client().listObjectsV2(credentials, "test", new ListObjectsArgs().setPrefix(prefix)).items())
                .extracting(ListBucketResult.ListBucketItem::key)
                .containsExactly(prefix + "object");
        }

        @Test
        void testPutObjectByteArrayWithOffset() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(1024);

            s3Client().putObject(credentials, "test", key, content, 100, 500);

            try (var object = s3Client().getObject(credentials, "test", key);
                 var body = object.body()) {
                assertThat(body.asInputStream().readAllBytes()).isEqualTo(Arrays.copyOfRange(content, 100, 600));
            }
        }

        @Test
        void testPutObjectContentWriterWithArgs() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(1024);
            var args = new io.koraframework.s3.client.kora.model.request.PutObjectArgs();
            args.contentType = "text/plain";
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

            s3Client().putObject(credentials, "test", key, args, writer);

            try (var object = s3Client().getObject(credentials, "test", key);
                 var body = object.body()) {
                assertThat(body.contentType()).isEqualTo("text/plain");
                assertThat(body.asInputStream().readAllBytes()).isEqualTo(content);
            }
        }

        @Test
        void testHeadObjectReturnsResponseHeaders() throws Exception {
            var key = UUID.randomUUID().toString();
            var content = randomBytes(16);
            var etag = s3Client().putObject(credentials, "test", key, content, 0, content.length);

            assertThat(s3Client().headObject(credentials, "test", key).etag()).isEqualTo(etag);
        }
    }

    void putObject(String key) throws Exception {
        var content = randomBytes(16);
        minioClient().putObject(PutObjectArgs.builder()
            .bucket("test")
            .object(key)
            .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
            .build());
    }

    byte[] randomBytes(long len) {
        var bytes = new byte[Math.toIntExact(len)];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
