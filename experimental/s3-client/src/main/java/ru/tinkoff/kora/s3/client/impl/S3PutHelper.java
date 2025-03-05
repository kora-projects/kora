package ru.tinkoff.kora.s3.client.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.s3.client.S3Config;
import ru.tinkoff.kora.s3.client.exception.S3ClientException;
import ru.tinkoff.kora.s3.client.exception.S3ClientUnknownException;
import ru.tinkoff.kora.s3.client.impl.xml.CompleteMultipartUploadRequest;
import ru.tinkoff.kora.s3.client.impl.xml.CompleteMultipartUploadResult;
import ru.tinkoff.kora.s3.client.impl.xml.InitiateMultipartUploadResult;
import ru.tinkoff.kora.s3.client.model.S3ObjectUploadResult;
import ru.tinkoff.kora.s3.client.telemetry.S3Telemetry;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;

class S3PutHelper {
    private final HttpClient httpClient;
    private final S3RequestSigner signer;
    final int uploadChunkSize;
    final int uploadPartSize;
    final String uploadChunkSizeHex;
    final int singlePartLimit;
    private final UriHelper uriHelper;
    private final Duration requestTimeout;
    private final S3Telemetry telemetry;

    S3PutHelper(HttpClient httpClient, S3Config config, S3Telemetry telemetry, UriHelper uriHelper, S3RequestSigner signer) {
        this.httpClient = httpClient;
        this.signer = signer;
        this.uriHelper = uriHelper;
        this.requestTimeout = config.requestTimeout();
        this.telemetry = telemetry;
        var uploadChunkSize = Math.toIntExact(config.upload().chunkSize().toBytes());
        if (uploadChunkSize < 0) {
            throw new IllegalArgumentException("uploadChunkSize must be >= 0");
        }
        var uploadPartSize = Math.toIntExact(config.upload().partSize().toBytes());
        if (uploadPartSize < 0) {
            throw new IllegalArgumentException("uploadPartSize must be >= 0");
        }
        if (uploadPartSize < Size.of(5, Size.Type.MiB).toBytes()) {
            throw new IllegalArgumentException("uploadPartSize must be >= 5mb");
        }
        this.singlePartLimit = Math.toIntExact(config.upload().singlePartUploadLimit().toBytes());
        this.uploadChunkSize = uploadChunkSize;
        this.uploadChunkSizeHex = Integer.toHexString(uploadChunkSize);
        this.uploadPartSize = uploadPartSize;
    }

    S3ObjectUploadResult putByteArrayMultipart(String bucket, String key, String contentEncoding, String contentType, byte[] buf, int off, int len) {
        var startMultipart = this.startMultipartUpload(bucket, key, contentEncoding, contentType);
        var completeRq = new CompleteMultipartUploadRequest(new ArrayList<>());
        try {
            var queryParams = new TreeMap<String, String>();
            queryParams.put("uploadId", startMultipart.uploadId());
            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                var from = i * singlePartLimit;
                if (from >= len) {
                    break;
                }
                var partNumber = String.valueOf(i + 1);
                var to = Math.min(len, from + singlePartLimit);
                var partLen = to - from;
                queryParams.put("partNumber", partNumber);
                var uri = this.uriHelper.uri(bucket, key + "?partNumber=" + partNumber + "&uploadId=" + startMultipart.uploadId());
                var sha256 = DigestUtils.sha256(buf, off + from, partLen).base64();
                try (var telemetry = this.telemetry.putObjectPart(bucket, key, startMultipart.uploadId(), i + 1, partLen)) {
                    telemetry.setUploadId(startMultipart.uploadId());
                    try {
                        var part = this.putAwsChunked(telemetry, uri, queryParams, partLen, null, null, new ByteArrayInputStream(buf, off + from, partLen), sha256);
                        completeRq.parts().add(new CompleteMultipartUploadRequest.Part(part.etag(), i + 1, part.sha256()));
                    } catch (Throwable t) {
                        telemetry.setError(t);
                        throw t;
                    }
                }
            }
        } catch (Throwable t) {
            try {
                this.abortUpload(bucket, key, startMultipart.uploadId());
            } catch (Throwable abortError) {
                t.addSuppressed(abortError);
            }
            throw t;
        }
        return this.completeUpload(bucket, key, startMultipart.uploadId(), completeRq, len);
    }

    S3ObjectUploadResult putUnknownSizeBody(String bucket, String key, InputStreamS3Body body) throws IOException {
        var buf = new byte[this.uploadPartSize];
        // download first part
        try (var is = body.asInputStream()) {
            var read = is.readNBytes(buf, 0, buf.length);
            if (read < buf.length) {
                // body is less than one part
                return this.putFullBody(bucket, key, body.encoding(), body.contentType(), buf, 0, read);
            }
            var startMultipartResult = this.startMultipartUpload(bucket, key, body.encoding(), body.contentType());
            var completeRequest = new CompleteMultipartUploadRequest(new ArrayList<>());
            var len = 0L;
            try {
                for (var i = 1; read > 0; i++) {
                    len += len;
                    try (var telemetry = this.telemetry.putObjectPart(bucket, key, startMultipartResult.uploadId(), i, read)) {
                        try {
                            var part = this.uploadPart(telemetry, bucket, key, startMultipartResult.uploadId(), i, buf, read);
                            completeRequest.parts().add(part);
                        } catch (Throwable t) {
                            telemetry.setError(t);
                            throw t;
                        }
                    }
                    read = is.readNBytes(buf, 0, buf.length);
                }
            } catch (Throwable t) {
                try {
                    this.abortUpload(bucket, key, startMultipartResult.uploadId());
                } catch (Throwable e) {
                    t.addSuppressed(e);
                }
                throw t;
            }
            return this.completeUpload(bucket, key, startMultipartResult.uploadId(), completeRequest, len);
        }
    }

    private S3ObjectUploadResult completeUpload(String bucket, String key, String uploadId, CompleteMultipartUploadRequest completeRequest, long objectSize) {
        try (var telemetry = this.telemetry.completeMultipartUpload(bucket, key, uploadId)) {
            var xml = completeRequest.toXml();
            var sha256 = DigestUtils.sha256(xml, 0, xml.length).hex();
            var uri = this.uriHelper.uri(bucket, key + "?uploadId=" + uploadId);
            var request = HttpClientRequest.of("POST", uri, "/{bucket}?uploadId={uploadId}", HttpHeaders.of(), HttpBody.of(xml), this.requestTimeout);
            var queryParams = new TreeMap<String, String>();
            queryParams.put("uploadId", uploadId);
            var headers = Map.of(
                "content-type", "text/xml",
                "content-length", Integer.toString(xml.length),
                "x-amz-mp-object-size", Long.toString(objectSize)
            );
            this.signer.processRequest(request, queryParams, headers, sha256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    var result = CompleteMultipartUploadResult.fromXml(body.asInputStream());
                    var version = rs.headers().getFirst("x-amz-version-id");
                    return new S3ObjectUploadResult(bucket, key, result.etag(), version);
                }
                throw S3ClientImpl.parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    private void abortUpload(String bucket, String key, String uploadId) {
        try (var telemetry = this.telemetry.abortMultipartUpload(bucket, key, uploadId)) {
            var uri = this.uriHelper.uri(bucket, key + "?uploadId=" + uploadId);
            var request = HttpClientRequest.of("DELETE", uri, "/{bucket}?uploadId={uploadId}", HttpHeaders.of(), HttpBody.empty(), this.requestTimeout);
            var queryParams = new TreeMap<String, String>();
            queryParams.put("uploadId", uploadId);
            this.signer.processRequest(request, queryParams, Map.of(), S3RequestSigner.EMPTY_PAYLOAD_SHA256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                if (rs.code() == 204) {
                    return;
                }
                throw S3ClientImpl.parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    private CompleteMultipartUploadRequest.Part uploadPart(S3Telemetry.S3TelemetryContext telemetry, String bucket, String key, String uploadId, int partNumber, byte[] buf, int read) {
        var sha256 = DigestUtils.sha256(buf, 0, read);
        var sha256Hex = sha256.hex();
        var sha256Base64 = sha256.base64();
        var md5 = DigestUtils.md5(buf, 0, read).base64();
        var headers = Map.of(
            "content-length", Integer.toString(read),
            "content-md5", md5,
            "x-amz-checksum-sha256", sha256Base64
        );
        var queryParams = new TreeMap<String, String>();
        queryParams.put("partNumber", Integer.toString(partNumber));
        queryParams.put("uploadId", uploadId);
        var uri = this.uriHelper.uri(bucket, key + "?partNumber=" + partNumber + "&uploadId=" + uploadId);
        var httpBody = HttpBody.of(ByteBuffer.wrap(buf, 0, read));
        var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}?partNumber={partNumber}&uploadId={uploadId}", HttpHeaders.of(), httpBody, this.requestTimeout);
        this.signer.processRequest(request, queryParams, headers, sha256Hex);
        try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
             var body = rs.body()) {
            telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
            telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
            if (rs.code() == 200) {
                var etag = rs.headers().getFirst("ETag");
                return new CompleteMultipartUploadRequest.Part(etag, partNumber, sha256Base64);
            }
            throw S3ClientImpl.parseS3Exception(rs, body);
        } catch (S3ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new S3ClientUnknownException(e);
        }
    }

    private InitiateMultipartUploadResult startMultipartUpload(String bucket, String key, @Nullable String encoding, @Nullable String contentType) {
        try (var telemetry = this.telemetry.startMultipartUpload(bucket, key)) {
            var headers = HttpHeaders.of();
            if (encoding != null) {
                headers.add("content-encoding", encoding);
            }
            if (contentType != null) {
                headers.add("content-type", contentType);
            }
            headers.add("x-amz-checksum-algorithm", "SHA256");
            headers.add("x-amz-checksum-type", "COMPOSITE");
            var uri = this.uriHelper.uri(bucket, key + "?uploads=true");
            var request = HttpClientRequest.of("POST", uri, "/{bucket}?uploads=true", headers, HttpBody.empty(), this.requestTimeout);
            this.signer.processRequest(request, new TreeMap<>(Map.of("uploads", "true")), Map.of(), S3RequestSigner.EMPTY_PAYLOAD_SHA256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    var result = InitiateMultipartUploadResult.fromXml(body.asInputStream());
                    telemetry.setUploadId(result.uploadId());
                    return result;
                }
                throw S3ClientImpl.parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    S3ObjectUploadResult putKnownSizeBody(String bucket, String key, String contentType, String contentEncoding, InputStream is, long size) {
        var uri = this.uriHelper.uri(bucket, key);
        try (var telemetry = this.telemetry.putObject(bucket, key, size)) {
            try {
                var result = putAwsChunked(telemetry, uri, S3RequestSigner.EMPTY_QUERY, size, contentType, contentEncoding, is, null);
                return new S3ObjectUploadResult(bucket, key, result.etag(), result.versionId());
            } catch (Throwable t) {
                telemetry.setError(t);
                throw t;
            }
        }
    }

    S3ObjectUploadResult putFullBody(String bucket, String key, String contentEncoding, String contentType, byte[] buf, int off, int len) {
        try (var telemetry = this.telemetry.putObject(bucket, key, len)) {
            var bodySha256 = DigestUtils.sha256(buf, off, len).hex();
            var headers = new HashMap<String, String>();
            if (contentEncoding != null) {
                headers.put("content-encoding", contentEncoding);
            }
            var uri = this.uriHelper.uri(bucket, key);
            var httpBody = HttpBody.of(contentType, ByteBuffer.wrap(buf, off, len));
            var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}", HttpHeaders.of(), httpBody, this.requestTimeout);
            this.signer.processRequest(request, S3RequestSigner.EMPTY_QUERY, headers, bodySha256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    return new S3ObjectUploadResult(
                        bucket,
                        key,
                        rs.headers().getFirst("etag"),
                        rs.headers().getFirst("x-amz-version-id")
                    );
                }
                throw S3ClientImpl.parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    private static class LimitedInputStream extends InputStream {
        protected final InputStream in;
        private final int originalLength;
        private int remaining;

        private LimitedInputStream(InputStream in, int len) {
            this.in = in;
            this.originalLength = len;
            this.remaining = len;
        }

        public int read() throws IOException {
            if (this.remaining == 0) {
                return -1;
            }
            var b = this.in.read();
            if (b < 0) {
                throw new EOFException("DEF length " + this.originalLength + " object truncated by " + this.remaining);
            }
            this.remaining--;
            return b;
        }

        public int read(byte[] buf, int off, int len) throws IOException {
            if (this.remaining == 0) {
                return -1;
            }
            var toRead = Math.min(len, this.remaining);
            var numRead = this.in.read(buf, off, toRead);
            if (numRead < 0) {
                throw new EOFException("DEF length " + this.originalLength + " object truncated by " + this.remaining);
            }
            this.remaining -= numRead;

            return numRead;
        }
    }

    public S3ObjectUploadResult putKnownSizeBodyMultiparted(String bucket, String key, @Nullable String contentType, @Nullable String contentEncoding, InputStream is, long size) {
        var startMultipart = this.startMultipartUpload(bucket, key, contentEncoding, contentType);
        var parts = new ArrayList<CompleteMultipartUploadRequest.Part>();
        try {
            var bytesToUpload = size;
            for (var i = 1; bytesToUpload > 0; i++) {
                var partSize = bytesToUpload < this.singlePartLimit * 2L
                    ? bytesToUpload
                    : this.singlePartLimit;

                var partNumber = Integer.toString(i);
                var uri = this.uriHelper.uri(bucket, key + "?partNumber=" + partNumber + "&uploadId=" + startMultipart.uploadId());
                var query = new TreeMap<String, String>();
                query.put("uploadId", startMultipart.uploadId());
                query.put("partNumber", partNumber);
                try (var telemetry = this.telemetry.putObjectPart(bucket, key, startMultipart.uploadId(), i, partSize)) {
                    try {
                        var part = this.putAwsChunked(telemetry, uri, query, partSize, contentType, contentEncoding, new LimitedInputStream(is, Math.toIntExact(partSize)), null);
                        parts.add(new CompleteMultipartUploadRequest.Part(part.etag(), i, part.sha256()));
                    } catch (Throwable t) {
                        telemetry.setError(t);
                        throw t;
                    }
                }
                bytesToUpload -= partSize;
            }
        } catch (Throwable t) {
            try {
                this.abortUpload(bucket, key, startMultipart.uploadId());
            } catch (Throwable e) {
                t.addSuppressed(e);
            }
            throw t;
        }
        return this.completeUpload(bucket, key, startMultipart.uploadId(), new CompleteMultipartUploadRequest(parts), size);
    }

    record PutAwsChunkedResult(String etag, String sha256, @Nullable String versionId) {}

    private PutAwsChunkedResult putAwsChunked(S3Telemetry.S3TelemetryContext telemetry, URI uri, SortedMap<String, String> queryParams, long size, @Nullable String contentType, @Nullable String contentEncoding, InputStream is, @Nullable String sha256Base64) {
        assert size >= 0;
        var httpBody = new KnownSizeAwsChunkedHttpBody(this.signer, this.uploadChunkSize, this.uploadChunkSizeHex, contentType, is, size, sha256Base64);
        var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}", HttpHeaders.of(), httpBody, this.requestTimeout);
        var signature = this.signer.processRequestChunked(httpBody.date, request, queryParams, size, httpBody.contentLength(), contentEncoding, sha256Base64);
        httpBody.previousSignature = signature;

        try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
             var body = rs.body()) {
            telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
            telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
            if (rs.code() == 200) {
                var etag = rs.headers().getFirst("etag");
                return new PutAwsChunkedResult(
                    etag,
                    httpBody.sha256(),
                    rs.headers().getFirst("x-amz-version-id")
                );
            }
            throw S3ClientImpl.parseS3Exception(rs, body);
        } catch (S3ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new S3ClientUnknownException(e);
        }
    }
}
