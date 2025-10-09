package ru.tinkoff.kora.s3.client.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.s3.client.S3Client;
import ru.tinkoff.kora.s3.client.S3Config;
import ru.tinkoff.kora.s3.client.exception.S3ClientErrorException;
import ru.tinkoff.kora.s3.client.exception.S3ClientException;
import ru.tinkoff.kora.s3.client.exception.S3ClientResponseException;
import ru.tinkoff.kora.s3.client.exception.S3ClientUnknownException;
import ru.tinkoff.kora.s3.client.impl.xml.DeleteObjectsRequest;
import ru.tinkoff.kora.s3.client.impl.xml.DeleteObjectsResult;
import ru.tinkoff.kora.s3.client.impl.xml.ListBucketResult;
import ru.tinkoff.kora.s3.client.impl.xml.S3Error;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import ru.tinkoff.kora.s3.client.model.S3ObjectUploadResult;
import ru.tinkoff.kora.s3.client.telemetry.S3Telemetry;
import ru.tinkoff.kora.s3.client.telemetry.S3TelemetryFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class S3ClientImpl implements S3Client {
    private final HttpClient httpClient;
    private final S3Config config;
    private final S3Telemetry telemetry;
    private final S3RequestSigner signer;
    private final S3PutHelper putHelper;
    private final UriHelper uriHelper;

    public S3ClientImpl(HttpClient httpClient, S3Config config, S3TelemetryFactory telemetryFactory, HttpClientTelemetryFactory httpClientTelemetryFactory, Class<?> declarativeClientInterface) {
        this.httpClient = httpClient.with(new TelemetryInterceptor(httpClientTelemetryFactory.get(config.telemetry(), declarativeClientInterface.getSimpleName())));
        this.config = config;
        this.uriHelper = new UriHelper(config);
        this.telemetry = telemetryFactory.get(config.telemetry(), declarativeClientInterface);
        this.signer = new S3RequestSigner(config.accessKey(), config.secretKey(), config.region());
        this.putHelper = new S3PutHelper(httpClient, config, telemetry, uriHelper, signer);
    }

    @Override
    @Nullable
    public S3Object get(String bucket, String key, @Nullable RangeData range) {
        return this.getInternal(bucket, key, false, range);
    }

    @Override
    @Nullable
    public S3Object getOptional(String bucket, String key, @Nullable RangeData range) {
        return this.getInternal(bucket, key, true, range);
    }

    @Nullable
    public S3Object getInternal(String bucket, String key, boolean isOptional, RangeData range) {
        try (var telemetry = this.telemetry.getObject(bucket, key);) {
            try {
                var headers = HttpHeaders.of();
                if (range instanceof RangeData.Range r) {
                    headers.add("range", "bytes=" + r.from() + "-" + r.to());
                } else if (range instanceof RangeData.StartFrom r) {
                    headers.add("range", "bytes=" + r.from() + "-");
                } else if (range instanceof RangeData.LastN r) {
                    headers.add("range", "bytes=-" + r.bytes());
                } else if (range != null) {
                    throw new IllegalStateException("not gonna happen");
                }
                var uri = this.uriHelper.uri(bucket, key);
                var request = HttpClientRequest.of("GET", uri, "/{bucket}/{object}", headers, HttpBody.empty(), this.config.requestTimeout());
                this.signer.processRequest(request, S3RequestSigner.EMPTY_QUERY, Map.of(), S3RequestSigner.EMPTY_PAYLOAD_SHA256);
                var rs = this.httpClient.execute(request).toCompletableFuture().get();
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    var lastModified = rs.headers().getFirst("Last-Modified");
                    var modified = lastModified != null
                        ? Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lastModified))
                        : null;
                    var body = rs.body();
                    var s3Body = new InputStreamS3Body(rs.body().asInputStream(), body.contentLength(), body.contentType(), rs.headers().getFirst("Content-Encoding"));
                    var s3Meta = new S3ObjectMeta(bucket, key, modified, body.contentLength());
                    return new S3Object(s3Meta, s3Body);
                }
                if (rs.code() == 206) {
                    var lastModified = rs.headers().getFirst("Last-Modified");
                    var modified = lastModified != null
                        ? Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lastModified))
                        : null;
                    var body = rs.body();
                    var s3Body = new InputStreamS3Body(rs.body().asInputStream(), body.contentLength(), body.contentType(), rs.headers().getFirst("Content-Encoding"));
                    var size = s3Body.size();
                    var contentRange = rs.headers().getFirst("Content-Range");
                    if (contentRange != null) {
                        if (contentRange.startsWith("bytes ")) {
                            var i = contentRange.indexOf('/');
                            if (i >= 0) {
                                try {
                                    size = Long.parseLong(contentRange.substring(i + 1));
                                } catch (NumberFormatException ignore) {}
                            }
                        }
                    }
                    var s3Meta = new S3ObjectMeta(bucket, key, modified, size);
                    return new S3Object(s3Meta, s3Body);
                }
                if (rs.code() == 404 && isOptional) {
                    rs.close();
                    return null;
                }
                try (var ignore = rs; var body = rs.body()) {
                    throw parseS3Exception(rs, body);
                }
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    @Override
    @Nullable
    public S3ObjectMeta getMeta(String bucket, String key) {
        return this.getMetaInternal(bucket, key, false);
    }

    @Override
    @Nullable
    public S3ObjectMeta getMetaOptional(String bucket, String key) {
        return this.getMetaInternal(bucket, key, true);
    }

    @Nullable
    public S3ObjectMeta getMetaInternal(String bucket, String key, boolean isOptional) {
        try (var telemetry = this.telemetry.getMetadata(bucket, key);) {
            var headers = HttpHeaders.of();
            var uri = this.uriHelper.uri(bucket, key);
            var request = HttpClientRequest.of("HEAD", uri, "/{bucket}/{object}", headers, HttpBody.empty(), this.config.requestTimeout());
            this.signer.processRequest(request, S3RequestSigner.EMPTY_QUERY, Map.of(), S3RequestSigner.EMPTY_PAYLOAD_SHA256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get()) {
                var amxRequestId = rs.headers().getFirst("X-Amz-Request-Id");
                telemetry.setAwsRequestId(amxRequestId);
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    var accessor = DateTimeFormatter.RFC_1123_DATE_TIME.parse(rs.headers().getFirst("Last-Modified"));
                    var modified = Instant.from(accessor);
                    var contentLength = rs.headers().getFirst("content-length");
                    var contentLengthLong = contentLength == null
                        ? 0L
                        : Long.parseLong(contentLength);
                    return new S3ObjectMeta(bucket, key, modified, contentLengthLong);
                }
                if (rs.code() == 404) {
                    if (isOptional) {
                        return null;
                    } else {
                        throw new S3ClientErrorException(rs.code(), "NoSuchKey", "Object does not exist", amxRequestId);
                    }
                }
                try (var body = rs.body()) {
                    throw parseS3Exception(rs, body);
                }
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    @Override
    public List<S3ObjectMeta> list(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit) {
        return this.listInternal(bucket, prefix, delimiter, limit, null).objects();
    }

    @Override
    public Iterator<S3ObjectMeta> listIterator(String bucket, @Nullable String prefix, @Nullable String delimiter, int maxPageSize) {
        var first = this.listInternal(bucket, prefix, delimiter, maxPageSize, null);
        if (first.continuationToken() == null) {
            return first.objects().iterator();
        }
        return new Iterator<>() {
            private ListResult currentList = first;
            private Iterator<S3ObjectMeta> currentIterator = first.objects().iterator();

            @Override
            public boolean hasNext() {
                if (currentIterator.hasNext()) {
                    return true;
                }
                if (currentList.continuationToken() == null) {
                    return false;
                }
                currentList = listInternal(bucket, prefix, delimiter, maxPageSize, currentList.continuationToken());
                currentIterator = currentList.objects().iterator();
                return currentIterator.hasNext();
            }

            @Override
            public S3ObjectMeta next() {
                return currentIterator.next();
            }
        };
    }


    private record ListResult(List<S3ObjectMeta> objects, @Nullable String continuationToken) {}

    private ListResult listInternal(String bucket, @Nullable String prefix, @Nullable String delimiter, int limit, @Nullable String continuationToken) {
        try (var telemetry = this.telemetry.listMetadata(bucket, prefix, delimiter)) {
            var pathBuilder = new StringBuilder("?list-type=2");
            var queryParameters = new TreeMap<String, String>();
            queryParameters.put("list-type", "2");
            if (delimiter != null) {
                var encoded = URLEncoder.encode(delimiter, StandardCharsets.UTF_8);
                queryParameters.put("delimiter", encoded);
                pathBuilder.append("&delimiter=").append(encoded);
            }
            if (prefix != null) {
                var encoded = URLEncoder.encode(prefix, StandardCharsets.UTF_8);
                queryParameters.put("prefix", encoded);
                pathBuilder.append("&prefix=").append(encoded);
            }
            if (continuationToken != null) {
                var encoded = URLEncoder.encode(continuationToken, StandardCharsets.UTF_8);
                queryParameters.put("continuation-token", encoded);
                pathBuilder.append("&continuation-token=").append(encoded);
            }
            queryParameters.put("max-keys", Integer.toString(limit));
            pathBuilder.append("&max-keys=").append(limit);

            var headers = HttpHeaders.of();
            var uri = this.uriHelper.uri(bucket, pathBuilder.toString());
            var request = HttpClientRequest.of("GET", uri, "/{bucket}?list", headers, HttpBody.empty(), this.config.requestTimeout());
            this.signer.processRequest(request, queryParameters, Map.of(), S3RequestSigner.EMPTY_PAYLOAD_SHA256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    try (var is = body.asInputStream()) {
                        var listResult = ListBucketResult.fromXml(is);
                        var result = new ArrayList<S3ObjectMeta>(listResult.contents().size());
                        for (var content : listResult.contents()) {
                            var accessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(content.lastModified());
                            var modified = Instant.from(accessor);
                            var meta = new S3ObjectMeta(bucket, content.key(), modified, content.size());
                            result.add(meta);
                        }
                        return new ListResult(result, listResult.nextContinuationToken());
                    }
                }
                throw parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try (var telemetry = this.telemetry.deleteObject(bucket, key);) {
            var headers = HttpHeaders.of();
            var uri = this.uriHelper.uri(bucket, key);
            var request = HttpClientRequest.of("DELETE", uri, "/{bucket}/{key}", headers, HttpBody.empty(), this.config.requestTimeout());
            this.signer.processRequest(request, S3RequestSigner.EMPTY_QUERY, Map.of(), S3RequestSigner.EMPTY_PAYLOAD_SHA256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 204) {
                    return;
                }
                if (rs.code() == 404) { // no such bucket
                    return;
                }
                throw parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    @Override
    public void delete(String bucket, Collection<String> keys) {
        try (var telemetry = this.telemetry.deleteObjects(bucket, keys);) {
            var xml = DeleteObjectsRequest.toXml(keys.stream().map(DeleteObjectsRequest.S3Object::new)::iterator);
            var payloadSha256 = DigestUtils.sha256(xml, 0, xml.length).hex();
            var bodyMd5 = DigestUtils.md5(xml, 0, xml.length).base64();
            var headers = Map.of(
                "content-md5", bodyMd5
            );
            var uri = this.uriHelper.uri(bucket, "?delete=true");
            var request = HttpClientRequest.of("POST", uri, "/{bucket}", HttpHeaders.of(), HttpBody.of(xml), this.config.requestTimeout());
            this.signer.processRequest(request, new TreeMap<>(Map.of("delete", "true")), headers, payloadSha256);
            try (var rs = this.httpClient.execute(request).toCompletableFuture().get();
                 var body = rs.body()) {
                telemetry.setAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                telemetry.setAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                if (rs.code() == 200) {
                    var ignore = DeleteObjectsResult.fromXml(body.asInputStream());
                    return;
                }
                if (rs.code() == 404) { // no such bucket
                    return;
                }
                throw parseS3Exception(rs, body);
            } catch (S3ClientException e) {
                telemetry.setError(e);
                throw e;
            } catch (Exception e) {
                telemetry.setError(e);
                throw new S3ClientUnknownException(e);
            }
        }
    }

    @Override
    public S3ObjectUploadResult put(String bucket, String key, S3Body body) {
        try (var telemetry = this.telemetry.putObject(bucket, key, body.size())) {
            try {
                if (body instanceof ByteArrayS3Body bytes) {
                    if (bytes.len() > this.putHelper.singlePartLimit * 2) {
                        return this.putHelper.putByteArrayMultipart(bucket, key, body.encoding(), body.contentType(), bytes.bytes(), bytes.offset(), bytes.len());
                    }
                    return this.putHelper.putFullBody(bucket, key, body.encoding(), body.contentType(), bytes.bytes(), bytes.offset(), bytes.len());
                }
                if (body instanceof InputStreamS3Body stream) {
                    try (var is = stream.getInputStream()) {
                        if (body.size() > -1) {
                            if (stream.size() <= this.putHelper.uploadPartSize) {
                                // just download whole body and upload in one chunk
                                var bytes = is.readNBytes((int) stream.size());
                                return this.putHelper.putFullBody(bucket, key, stream.encoding(), stream.contentType(), bytes, 0, bytes.length);
                            }
                            if (stream.size() > this.putHelper.singlePartLimit * 2L) {
                                // we should multipart upload it
                                return this.putHelper.putKnownSizeBodyMultiparted(bucket, key, stream.contentType(), stream.encoding(), is, body.size());
                            }
                            // let's use aws-chunked encoding
                            return this.putHelper.putKnownSizeBody(bucket, key, stream.contentType(), stream.encoding(), is, body.size());
                        }
                        return this.putHelper.putUnknownSizeBody(bucket, key, stream);
                    } catch (IOException e) {
                        throw new S3ClientUnknownException(e);
                    }
                }
                throw new IllegalStateException("Unknown body type: " + body.getClass());
            } catch (Throwable t) {
                telemetry.setError(t);
                throw t;
            }
        }
    }


    static RuntimeException parseS3Exception(HttpClientResponse rs, HttpBodyInput body) {
        try (var is = body.asInputStream()) {
            var bytes = is.readAllBytes();
            try {
                var s3Error = S3Error.fromXml(new ByteArrayInputStream(bytes));
                throw new S3ClientErrorException(rs.code(), s3Error.code(), Objects.requireNonNullElse(s3Error.message(), ""), s3Error.requestId());
            } catch (S3ClientException e) {
                throw e;
            } catch (Exception e) {
                throw new S3ClientResponseException("Unexpected response from s3: code=%s, body=%s".formatted(rs.code(), new String(bytes, StandardCharsets.UTF_8)), e, rs.code());
            }
        } catch (IOException e) {
            throw new S3ClientUnknownException(e);
        }
    }
}
